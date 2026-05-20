package problems.ConcurrencyDesignProblems;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class ConcurrentTicketBookingSystem {

    class SeatManager{
        private final List<SeatSnapshot> seatSnapshots;
        private final HoldManager holdManager;
        private final long delay;
        private final Executor cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        public SeatManager(int noOfSeat, long delay) {
            this.delay = delay;
            this.holdManager = new HoldManager();
            this.seatSnapshots = new ArrayList<>();
            for(int i=0; i<noOfSeat; i++){
                this.seatSnapshots.add(new SeatSnapshot(i+1));
            }
            cleaner.execute(this.holdManager::drain);
        }

        public void heldSeat(String userId, int[] seats){
            final List<Integer> heldSeats = new ArrayList<>();
            long expiryTime = System.currentTimeMillis() + delay;

            for(int seatNo:seats){
                SeatSnapshot seatSnapshot = seatSnapshots.get(seatNo);
                if (tryAcquire(seatSnapshot)){
                    seatSnapshot.setUserId(userId);
                    seatSnapshot.setExpiryTime(expiryTime);
                    heldSeats.add(seatNo);
                }
                else{
                    heldSeats.forEach(seat -> clearState(seatSnapshots.get(seat)));
                    System.out.println("Seat held failed");
                    break;
                }
            }
            long scheduleId = UUID.randomUUID().node();
            heldSeats.forEach(seat ->  schedule(scheduleId, userId, seatSnapshots.get(seat)));
            System.out.println("Seat is held");
        }

        private void schedule(long scheduleId, String userId, SeatSnapshot seatSnapshot) {
            holdManager.add(new DelayedTask(scheduleId, userId, seatSnapshot));
        }

        public void clearState(SeatSnapshot seatSnapshot){
            while(true){
                String userId = seatSnapshot.getUserId();
                SeatState oldState = seatSnapshot.getState();
                if (userId != null && seatSnapshot.getDelay() <= 0){
                    seatSnapshot.setUserId(userId, null);
                    seatSnapshot.setState(oldState, SeatState.AVAILABLE);
                }
                oldState = seatSnapshot.getState();
                if (oldState == SeatState.AVAILABLE) break;
                if (seatSnapshot.setState(oldState, SeatState.AVAILABLE)) break;
            }
        }

        public boolean tryAcquire(SeatSnapshot seatSnapshot){
            while(true){
                if (seatSnapshot.userId != null && seatSnapshot.getDelay() <= 0){
                    seatSnapshot.userId = null;
                    seatSnapshot.setState(SeatState.AVAILABLE);
                }
                SeatState oldState = seatSnapshot.getState();
                if (oldState != SeatState.AVAILABLE) return false;
                if (seatSnapshot.setState(oldState, SeatState.HELD)){
                    return true;
                }
            }
        }
        public void bookSeat(String userId, int[] seats){
            final List<Integer> heldSeats = new ArrayList<>();

            for(int seatNo:seats){
                SeatSnapshot seatSnapshot = seatSnapshots.get(seatNo);
                if (confirmSeat(seatSnapshot)){
                    seatSnapshot.setUserId(userId);
                    heldSeats.add(seatNo);
                }
                else{
                    heldSeats.forEach(seat -> clearState(seatSnapshot));
                    System.out.println("Seat booking failed");
                }
            }
            System.out.println("Seat is booked");
        }

        private boolean confirmSeat(SeatSnapshot seatSnapshot) {
            while(true){
                String userId = seatSnapshot.getUserId();
                if (userId != null && seatSnapshot.getDelay() <= 0){
                    seatSnapshot.setUserId(userId, null);
                    seatSnapshot.setState(SeatState.AVAILABLE);
                }
                SeatState oldState = seatSnapshot.getState();
                if (oldState != SeatState.HELD) return false;
                if (seatSnapshot.setState(oldState, SeatState.HELD)){
                    return true;
                }
            }
        }
    }

    static class HoldManager{
        private final DelayQueue<DelayedTask> delayQueue;

        public HoldManager() {
            this.delayQueue = new DelayQueue<>();
        }

        public void drain(){
            while(true){
                try {
                    DelayedTask task = delayQueue.take();
                    clearSeat(task.userId, task.seatSnapshot);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        public void add(DelayedTask task){
            delayQueue.offer(task);
        }
        private void clearSeat(String userId, SeatSnapshot seatSnapshot){
            while(true){
                String userIdSnap = seatSnapshot.getUserId();
                if (!userId.equals(userIdSnap)) return;
                SeatState oldState = seatSnapshot.getState();
                if (oldState != SeatState.HELD) return;
                if (seatSnapshot.setState(oldState, SeatState.AVAILABLE)) return;
            }
        }
    }

    static class DelayedTask implements Delayed{
        private final long scheduleId;
        private final String userId;
        private final SeatSnapshot seatSnapshot;

        public DelayedTask(long scheduleId, String userId, SeatSnapshot seatSnapshot) {
            this.scheduleId = scheduleId;
            this.userId = userId;
            this.seatSnapshot = seatSnapshot;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return seatSnapshot.getDelay();
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(seatSnapshot.expiryTime, ((DelayedTask)o).seatSnapshot.expiryTime);
        }
    }
    static class SeatSnapshot{
        private final int seatNo;
        private AtomicReference<String> userId = new AtomicReference<>();
        private AtomicReference<SeatState> state = new AtomicReference<>(SeatState.AVAILABLE);
        private long expiryTime;

        public SeatSnapshot(int seatNo) {
            this.seatNo = seatNo;
        }

        public SeatSnapshot(int seatNo, String userId, long expiryTime) {
            this.seatNo = seatNo;
            this.userId = new AtomicReference<>(userId);
            this.expiryTime = expiryTime;
        }

        public void setExpiryTime(long expiryTime) {
            this.expiryTime = expiryTime;
        }

        public void setUserId(String oldId, String newId) {
            this.userId.compareAndSet(oldId, newId);
        }
        public void setUserId(String id) {
            this.userId.set(id);
        }

        public String getUserId() {
            return userId.get();
        }

        public void setState(SeatState state) {
            this.state.set(state);
        }

        public long getDelay(){
            return expiryTime - System.currentTimeMillis();
        }

        public SeatState getState() {
            return state.get();
        }

        public boolean setState(SeatState oldState, SeatState newState){
            return this.state.compareAndSet(oldState, newState);
        }
    }
    enum SeatState{
        AVAILABLE,
        HELD,
        BOOKED;
    }
}