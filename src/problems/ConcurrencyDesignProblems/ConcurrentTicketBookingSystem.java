package problems.ConcurrencyDesignProblems;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class ConcurrentTicketBookingSystem {

    private final ConcurrentMap<String, Task> concurrentMap;
    private final Executor backgroundThread = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Background thread");
        t.setDaemon(true);
        return t;
    });

    private final HoldManager holdManager;
    public ConcurrentTicketBookingSystem(long delay) {
        holdManager = new HoldManager(delay);
        concurrentMap = new ConcurrentHashMap<>();
        backgroundThread.execute(holdManager::clean);
    }

    public void bookSeat(Seat[] seats, String id){
        Task task = new Task(id, seats);
        if(!tryBookingSeat(seats, SeatState.HELD)){
            rollback(seats);
            System.out.println("Seat booking failed");
        }
        else{
            Task existing = concurrentMap.putIfAbsent(id, task);
            if (existing != null){
                rollback(seats);
                return;
            }
            holdManager.add(task);
            System.out.println("Seat booked successfully");
        }
    }

    public void makePayment(String id, Function<String, Boolean> payment){
        Task task = concurrentMap.get(id);
        if (task != null && payment.apply(id)){
            if (!tryBookingSeat(task.getSeats(), SeatState.BOOKED)){
                rollback(task.getSeats());
                System.out.println("initiate refund!!");
            }
            else{
                concurrentMap.remove(id);
            }
        }
    }

    private void rollback(Seat[] seats) {
        for(int i = 0; i< seats.length; i++){
            if (seats[i].getState() == SeatState.HELD){
                seats[i].changeState(SeatState.AVAILABLE);
            }
        }
    }

    private boolean tryBookingSeat(Seat[] seats, SeatState state) {
        for(int i = 0; i< seats.length; i++){
            if (!seats[i].changeState(state)){
                return false;
            }
        }
        return true;
    }

    static class HoldManager{
        private final DelayQueue<DelayedTask> delayQueue;
        private final long delay;

        public HoldManager(long delay) {
            this.delay = delay;
            this.delayQueue = new DelayQueue<>();
        }

        public void clean() {
            while(true){
                try {
                    DelayedTask task = delayQueue.take();

                    for(Seat seat:task.task.seats){
                        seat.changeState(SeatState.AVAILABLE);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public void add(Task task){
            delayQueue.offer(new DelayedTask(task, delay));
        }
    }

    static class DelayedTask implements Delayed{
        private final long expiryTime;
        private final Task task;

        public DelayedTask(Task task, long delay) {
            this.expiryTime = System.currentTimeMillis() + delay;
            this.task = task;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long elapsed = expiryTime - System.currentTimeMillis();
            return unit.toMillis(elapsed);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(this.expiryTime, ((DelayedTask)o).expiryTime);
        }
    }

    static class Task{
        private final String taskId;
        private final Seat[] seats;

        public String getTaskId() {
            return taskId;
        }

        public Seat[] getSeats() {
            return seats;
        }

        public Task(String taskId, Seat[] seats) {
            this.taskId = taskId;
            Arrays.sort(seats);
            this.seats = seats;
        }

    }

    static class Seat implements Comparable<Seat>{
        AtomicReference<SeatState> state;
        private final int seatNo;
        private final String userId;

        public Seat(int seatNo, String userId) {
            this.state = new AtomicReference<>(SeatState.AVAILABLE);
            this.seatNo = seatNo;
            this.userId = userId;
        }

        public boolean changeState(SeatState newState){
            while(true){
                SeatState oldState = this.state.get();
                if (oldState == SeatState.AVAILABLE && newState != SeatState.HELD) return false;
                if (oldState == SeatState.HELD && newState == SeatState.HELD) return false;
                if (oldState == SeatState.BOOKED) return false;

                if (this.state.compareAndSet(oldState, newState)){
                    return true;
                }
            }
        }

        public int getSeatNo() {
            return seatNo;
        }

        public SeatState getState() {
            return this.state.get();
        }

        @Override
        public int compareTo(Seat o) {
            return Integer.compare(this.seatNo, o.seatNo);
        }
    }

    enum SeatState{
        AVAILABLE,
        HELD,
        BOOKED;
    }

}