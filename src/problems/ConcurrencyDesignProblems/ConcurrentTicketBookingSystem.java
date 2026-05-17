package problems.ConcurrencyDesignProblems;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class ConcurrentTicketBookingSystem {

    private final Executor backgroundThread = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Background thread");
        t.setDaemon(true);
        return t;
    });
    private final HoldManager holdManager;
    public ConcurrentTicketBookingSystem() {
        holdManager = new HoldManager();
        backgroundThread.execute(holdManager::clean);
    }

    public void bookSeat(Seat seats){

    }

    static class HoldManager{
        private final DelayQueue<DelayedTask> delayQueue;

        public HoldManager() {
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
            delayQueue.offer(new DelayedTask(System.currentTimeMillis(), task));
        }
    }

    static class DelayedTask implements Delayed{
        private final long executedTime;
        private final Task task;

        public DelayedTask(long executedTime, Task task) {
            this.executedTime = executedTime;
            this.task = task;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long elapsed = System.currentTimeMillis() - executedTime;
            return unit.toMillis(elapsed);
        }

        @Override
        public int compareTo(Delayed o) {
            return this.task.getTaskId().compareTo(((DelayedTask)o).task.getTaskId());
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

        public Seat(int seatNo) {
            this.state = new AtomicReference<>(SeatState.AVAILABLE);
            this.seatNo = seatNo;
        }

        public boolean changeState(SeatState newState){
            while(true){
                SeatState oldState = this.state.get();
                if (oldState != SeatState.BOOKED || oldState == newState) return false;

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
            return Integer.compare(this.seatNo, o.seatNo)
        }
    }

    enum SeatState{
        AVAILABLE,
        HELD,
        BOOKED;
    }

}

