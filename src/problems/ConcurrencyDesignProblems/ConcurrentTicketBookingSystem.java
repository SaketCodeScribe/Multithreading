package problems.ConcurrencyDesignProblems;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ConcurrentTicketBookingSystem {
    enum SeatState {
        AVAILABLE,
        HELD,
        BOOKED
    }

    enum PaymentState{
        NOT_STARTED,
        INITIATED,
        COMPLETED,
        CANCELLED;
    }

    enum SystemState{
        NOT_RUNNING,
        RUNNING,
        CANCELLED;
    }

    static class SeatSnapshot{
        private final HeldInfo heldInfo;
        private final long expiryTime;
        private final SeatState state;

        public SeatSnapshot(HeldInfo heldInfo, long expiryTime, SeatState state) {
            this.heldInfo = heldInfo;
            this.expiryTime = expiryTime;
            this.state = state;
        }

        public String getUserId() {
            return heldInfo.getUserId();
        }

        public String getRequestId() {
            return heldInfo.getRequestId();
        }

        public long getExpiryTime() {
            return expiryTime;
        }

        public SeatState getState() {
            return state;
        }

        public boolean isExpired(){
            return expiryTime > 0 && expiryTime < System.currentTimeMillis();
        }

        public HeldInfo getHeldInfo() {
            return heldInfo;
        }
    }

    static class Seat{
        private final String seatId;
        private AtomicReference<SeatSnapshot> snapshot;

        public Seat(String seatId) {
            this.seatId = seatId;
            snapshot = new AtomicReference<>(new SeatSnapshot(null, 0, SeatState.AVAILABLE));
        }

        public String getSeatId() {
            return seatId;
        }

        public SeatSnapshot getSnapshot() {
            return snapshot.get();
        }

        public boolean compareAndSet(SeatSnapshot oldState, SeatSnapshot newState){
            return snapshot.compareAndSet(oldState, newState);
        }
        public void freeSeat(){
            this.snapshot.set(new SeatSnapshot(null, 0l, SeatState.AVAILABLE));
        }
    }

    static class SeatManager{
        public static boolean heldSeat(HeldInfo heldInfo, long expiryTime, Seat seat){
            SeatSnapshot newSnap = new SeatSnapshot(heldInfo, expiryTime, SeatState.HELD);
            final int MAX_RETRIES = 100;

            for(int i=0; i<MAX_RETRIES; i++) {
                SeatSnapshot oldSnap = seat.getSnapshot();
                if (oldSnap.getState() == SeatState.BOOKED) return false;
                if (oldSnap.getState() == SeatState.HELD && !oldSnap.isExpired()) return false;

                if (seat.compareAndSet(oldSnap, newSnap)) return true;

                Thread.onSpinWait();
            }
            return false;
        }

        public static boolean confirmSeat(HeldInfo heldInfo, Seat seat){
            SeatSnapshot newSnap = new SeatSnapshot(heldInfo, Long.MAX_VALUE, SeatState.BOOKED);
            final int MAX_RETRIES = 100;

            for(int i=0; i<MAX_RETRIES; i++) {
                SeatSnapshot oldSnap = seat.getSnapshot();
                if (oldSnap.getState() != SeatState.HELD) return false;
                if (!Objects.equals(oldSnap.getHeldInfo(), heldInfo)) return false;
                if (oldSnap.isExpired()) return false;

                if (seat.compareAndSet(oldSnap, newSnap)) return true;

                Thread.onSpinWait();
            }
            return false;
        }

        public static void rollback(HeldInfo heldInfo, Seat seat, SeatState seatState){
            SeatSnapshot newSnap = new SeatSnapshot(null, 0l, SeatState.AVAILABLE);

            while (true){
                SeatSnapshot oldSnap = seat.getSnapshot();
                if (oldSnap.getState() != seatState) return;
                if (!Objects.equals(oldSnap.getHeldInfo(), heldInfo)) return;
                if (seat.compareAndSet(oldSnap, newSnap)) return;
            }
        }
    }
    static class HeldInfo{
        private final String userId;
        private final String requestId;

        public HeldInfo(String userId, String requestId) {
            this.userId = userId;
            this.requestId = requestId;
        }

        public String getUserId() {
            return userId;
        }

        public String getRequestId() {
            return requestId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HeldInfo heldInfo)) return false;
            return Objects.equals(userId, heldInfo.userId) && Objects.equals(requestId, heldInfo.requestId);
        }

        @Override
        public int hashCode() {
            return (userId + "-" + requestId).hashCode();
        }
    }
    static class BookingService{
        ConcurrentMap<HeldInfo, List<Seat>> activeHolds;
        ConcurrentHashMap<HeldInfo, PaymentState> bookInfo;
        Map<String, Seat> seatInfo; // this can be normal map, as threads use it to only read it. Seat itself is thread safe
        private final long delay;
        AtomicReference<SystemState> state = new AtomicReference<>(SystemState.NOT_RUNNING);

        private final ScheduledExecutorService cleanScheduler = Executors.newSingleThreadScheduledExecutor();
        private AtomicBoolean shouldDrain;

        public BookingService(int noOfSeats, long delay) {
            activeHolds = new ConcurrentHashMap<>();
            bookInfo = new ConcurrentHashMap<>();
            var tempMap = new HashMap<String, Seat>();
            for(int i=0; i<noOfSeats; i++){
                tempMap.put("seat-"+(i+1), new Seat("seat-"+(i+1)));
            }
            seatInfo = Collections.unmodifiableMap(tempMap);
            state.set(SystemState.RUNNING);
            this.delay = delay;
            this.shouldDrain = new AtomicBoolean(true);
            this.cleanScheduler.scheduleAtFixedRate(this::drain, delay, delay, TimeUnit.MILLISECONDS);
        }

        public void holdSeat(String userId, String requestId, String[] seats){
            HeldInfo held = new HeldInfo(userId, requestId);
            // to handle duplicate requests
            List<Seat> heldSeats = activeHolds.getOrDefault(held, new ArrayList<>());
            var existing = activeHolds.putIfAbsent(held, heldSeats);
            if (existing != null){
                System.out.println("duplicate request");
                return;
            }

            long expiryTime = System.currentTimeMillis() + delay;

            for(String seat:seats) {
                Seat s = seatInfo.get(seat);
                if (state.get() == SystemState.RUNNING && s!= null && SeatManager.heldSeat(held, expiryTime, s)){
                    heldSeats.add(s);
                }
                else{
                    heldSeats.forEach(item -> SeatManager.rollback(held, item, SeatState.HELD));
                    System.out.println("Seat hold failed");
                    return;
                }
            }
            activeHolds.put(held, heldSeats);
            bookInfo.put(held, PaymentState.NOT_STARTED);
        }

        public void confirmSeat(String userId, String requestId){
            List<Seat> bookedSeats = new ArrayList<>();
            HeldInfo held = new HeldInfo(userId, requestId);

            var existingState = bookInfo.putIfAbsent(held, PaymentState.INITIATED);
            switch (existingState){
                case COMPLETED -> System.out.println("Payment is completed. duplicate request");
                case INITIATED -> System.out.println("Payment is initiated. duplicate request");
                case CANCELLED -> System.out.println("Payment is cancelled");
                default -> {
                    List<Seat> seats = activeHolds.get(held);
                    if (seats == null) {
                        System.out.println("Hold not found");
                        return;
                    }
                    for (Seat seat : seats) {
                        if (state.get() == SystemState.RUNNING && SeatManager.confirmSeat(held, seat)) {
                            bookedSeats.add(seat);
                        } else {
                            bookedSeats.forEach(item -> SeatManager.rollback(held, item, SeatState.BOOKED));
                            System.out.println("Seat confirm failed");
                            return;
                        }
                    }
                    activeHolds.remove(held);
                    bookInfo.put(held, PaymentState.COMPLETED);
                }
            }
        }

        private void drain(){
            if (!this.shouldDrain.get()) return;
            for(Map.Entry<String, Seat> entry: seatInfo.entrySet()){
                Seat seat = entry.getValue();
                while(true) {
                    SeatSnapshot oldSnap = seat.getSnapshot();
                    SeatSnapshot newSnap = new SeatSnapshot(null, 0l, SeatState.AVAILABLE);
                    if (!oldSnap.isExpired()) break;
                    if (seat.compareAndSet(oldSnap, newSnap)) break;
                }
            }

            activeHolds.entrySet().removeIf(entry -> {
                if(entry.getValue().stream().anyMatch(seat -> !Objects.equals(seat.getSnapshot().getHeldInfo(), entry.getKey()))){
                    bookInfo.put(entry.getKey(), PaymentState.CANCELLED);
                    return true;
                }
                return false;
            });
        }

        public void shutdown(){
            state.set(SystemState.CANCELLED);
            this.shouldDrain.set(false);

            cleanScheduler.shutdownNow();

            for(Map.Entry<String, Seat> entry: seatInfo.entrySet()){
                Seat seat = entry.getValue();
                if (seat.getSnapshot().getState() != SeatState.BOOKED) seat.freeSeat();
            }
            bookInfo.entrySet().forEach(bookingEntry -> {
                if (bookingEntry.getValue() == PaymentState.INITIATED) bookingEntry.setValue(PaymentState.CANCELLED);
            });
            activeHolds.clear();
            System.out.println("shutdown successful");
        }
    }
}