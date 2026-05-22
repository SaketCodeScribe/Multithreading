package problems.ConcurrencyDesignProblems;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ConcurrentTicketBookingSystem {
    enum SeatState {
        AVAILABLE,
        HELD,
        BOOKED
    }

    enum SystemState{
        NOT_RUNNING,
        RUNNING,
        CANCELLED;
    }

    static class SeatSnapshot{
        private final String userId;
        private final String requestId;
        private final long expiryTime;
        private final SeatState state;

        public SeatSnapshot(String userId, String requestId, long expiryTime, SeatState state) {
            this.userId = userId;
            this.requestId = requestId;
            this.expiryTime = expiryTime;
            this.state = state;
        }

        public String getUserId() {
            return userId;
        }

        public String getRequestId() {
            return requestId;
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
    }

    static class Seat{
        private final String seatId;
        private AtomicReference<SeatSnapshot> snapshot;

        public Seat(String seatId) {
            this.seatId = seatId;
            snapshot = new AtomicReference<>(new SeatSnapshot(null, null, 0, SeatState.AVAILABLE));
        }

        public String getSeatId() {
            return seatId;
        }

        public SeatSnapshot getSnapshot() {
            return snapshot.get();
        }

        public boolean compareAndSeat(SeatSnapshot oldState, SeatSnapshot newState){
            return snapshot.compareAndSet(oldState, newState);
        }
    }

    static class SeatManager{
        public static boolean heldSeat(String requestId, String userId, long expiryTime, Seat seat){
            SeatSnapshot newSnap = new SeatSnapshot(userId, requestId, expiryTime, SeatState.HELD);

            while(true){
                SeatSnapshot oldSnap = seat.getSnapshot();
                if (oldSnap.getState() == SeatState.BOOKED) return false;
                if (oldSnap.getState() == SeatState.HELD && !oldSnap.isExpired()) return false;

                if (seat.compareAndSeat(oldSnap, newSnap)) return true;
            }
        }

        public static boolean confirmSeat(String requestId, String userId, long delay, Seat seat){
            SeatSnapshot newSnap = new SeatSnapshot(userId, requestId, Long.MAX_VALUE, SeatState.BOOKED);

            while(true){
                SeatSnapshot oldSnap = seat.getSnapshot();
                if (oldSnap.getState() == SeatState.BOOKED || oldSnap.getState() == SeatState.AVAILABLE) return false;
                if (oldSnap.getState() == SeatState.HELD && oldSnap.isExpired()) return false;

                if (seat.compareAndSeat(oldSnap, newSnap)) return true;
            }
        }

        public static void rollback(HeldInfo heldInfo, Seat seat){
            SeatSnapshot newSnap = new SeatSnapshot(null, null, 0l, SeatState.AVAILABLE);

            while (true){
                SeatSnapshot oldSnap = seat.getSnapshot();
                if (oldSnap.getState() == SeatState.AVAILABLE) return;
                if (oldSnap.getState() == SeatState.BOOKED && (!Objects.equals(oldSnap.getUserId(), heldInfo.getUserId()) || Objects.equals(oldSnap.getRequestId(), heldInfo.getRequestId()))) return;
                if (seat.compareAndSeat(oldSnap, newSnap)) return;
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
        Map<String, Seat> seatInfo;
        private final long delay;
        AtomicReference<SystemState> state = new AtomicReference<>(SystemState.NOT_RUNNING);

        private final ScheduledExecutorService cleanScheduler = Executors.newSingleThreadScheduledExecutor();;

        public BookingService(int noOfSeats, long delay) {
            activeHolds = new ConcurrentHashMap<>();
            seatInfo = new HashMap<>();

            for(int i=0; i<noOfSeats; i++){
                seatInfo.put("seat-"+(i+1), new Seat("seat-"+(i+1)));
            }
            state.set(SystemState.RUNNING);
            this.delay = delay;
            this.cleanScheduler.scheduleAtFixedRate(this::drain, delay, delay, TimeUnit.MILLISECONDS);
        }

        public void holdSeat(String userId, String requestId, String[] seats){
            HeldInfo held = new HeldInfo(userId, requestId);
            List<Seat> heldSeats = new ArrayList<>();
            var existing = activeHolds.putIfAbsent(held, heldSeats);
            if (existing != null){
                System.out.println("duplicate request");
                return;
            }

            long expiryTime = System.currentTimeMillis() + delay;

            for(String seat:seats) {
                Seat s = seatInfo.get(seat);
                if (state.get() == SystemState.RUNNING && s!= null && SeatManager.heldSeat(requestId, userId, expiryTime, s)){
                    heldSeats.add(s);
                }
                else{
                    heldSeats.forEach(item -> SeatManager.rollback(held, item));
                    System.out.println("Seat hold failed");
                    return;
                }
            }

            activeHolds.putIfAbsent(held, heldSeats);
        }

        public void confirmSeat(String userId, String requestId){
            List<Seat> bookedSeats = new ArrayList<>();
            HeldInfo held = new HeldInfo(userId, requestId);
            List<Seat> seats = activeHolds.get(held);
            if (seats == null) {
                System.out.println("Hold not found");
                return;
            }
            for(Seat seat:seats) {
                if (state.get() == SystemState.RUNNING && SeatManager.confirmSeat(requestId, userId, delay, seat)){
                    bookedSeats.add(seat);
                }
                else{
                    bookedSeats.forEach(item -> SeatManager.rollback(held, item));
                    System.out.println("Seat confirm failed");
                    return;
                }
            }
            activeHolds.remove(held);
        }

        public void drain(){
            activeHolds.entrySet().removeIf(entry -> {
                return entry.getValue().stream().findFirst().map(seat -> {
                    while (true) {
                        SeatSnapshot oldSnap = seat.getSnapshot();
                        if (oldSnap.getState() != SeatState.HELD) return false;
                        if (!oldSnap.isExpired()) return false;
                        SeatSnapshot newSnap = new SeatSnapshot(null, null, 0, SeatState.AVAILABLE);
                        if (seat.compareAndSeat(oldSnap, newSnap)) return true;
                    }
                }).orElse(false);
            });
        }

        public void shutdown(){
            state.set(SystemState.CANCELLED);
            cleanScheduler.shutdown();

            try {
                if (cleanScheduler.awaitTermination(delay * 10, TimeUnit.MILLISECONDS)){
                    activeHolds.keySet().removeIf(entry -> {
                        activeHolds.get(entry).forEach(seat -> {
                            seat.
                        });
                    });
                    seatInfo.clear();
                }
            } catch (InterruptedException e) {
                System.out.println("shutdown failure");
                System.exit(-1);
            }
            System.out.println("shutdown successful");
        }
    }
}