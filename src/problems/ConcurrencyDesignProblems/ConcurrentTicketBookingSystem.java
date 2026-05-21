package problems.ConcurrencyDesignProblems;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class ConcurrentTicketBookingSystem {

    class SeatManager {
        private final List<SeatSnapshotHolder> seatSnapshots;
        private final HoldManager holdManager;
        private final long delay;
        private final Executor cleaner = Executors.newSingleThreadScheduledExecutor( r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        public SeatManager(int noOfSeat, long delay) {
            this.delay = delay;
            this.holdManager = new HoldManager();
            this.seatSnapshots = new ArrayList<>();

            for (int i = 0; i < noOfSeat; i++) {
                this.seatSnapshots.add(new SeatSnapshotHolder());
            }

            cleaner.execute(this.holdManager::drain);
        }

        public void holdSeat(String userId, int[] seats) {
            final List<Integer> heldSeats = new ArrayList<>();
            String taskId = UUID.randomUUID().toString();
            long expiryTime = System.currentTimeMillis() + delay;

            for (int seatNo : seats) {
                SeatSnapshotHolder seatSnapshot = seatSnapshots.get(seatNo);

                if (tryAcquire(userId, expiryTime, seatSnapshot, taskId)) {
                    heldSeats.add(seatNo);
                } else {
                    heldSeats.forEach(seat -> rollback(seatSnapshots.get(seat), taskId, userId));
                    System.out.println("Seat hold failed. Rollback completed.");
                    return;
                }
            }

            heldSeats.forEach(seat -> schedule(userId, taskId, seatSnapshots.get(seat)));
            System.out.println("Seats are held successfully.");
        }

        private void schedule(String userId, String taskId, SeatSnapshotHolder seatSnapshot) {
            holdManager.add(new DelayedTask(userId, taskId, seatSnapshot, delay));
        }

        public boolean tryAcquire(String userId, long expiryTime, SeatSnapshotHolder seatSnapshot, String taskId) {
            while (true) {
                SeatSnapshot current = seatSnapshot.getSnapshot();
                String currUser = current.userId;

                if (current.state == SeatState.BOOKED) {
                    return false;
                }

                if (current.state == SeatState.HELD && currUser != null && !current.isExpired()) {
                    return false;
                }

                SeatSnapshot next = new SeatSnapshot(
                        SeatState.HELD,
                        userId,
                        taskId,
                        expiryTime
                );

                if (seatSnapshot.compareAndSet(current, next)) {
                    return true;
                }
            }
        }

        public void bookSeat(String taskId, String userId, int[] seats) {
            List<Integer> bookedSeats = new ArrayList<>();

            for (int seatNo : seats) {
                SeatSnapshotHolder seatSnapshot = seatSnapshots.get(seatNo);

                if (confirmSeat(seatSnapshot, taskId, userId)) {
                    bookedSeats.add(seatNo);
                } else {
                    bookedSeats.forEach(seat -> rollback(seatSnapshots.get(seat), taskId, userId));
                    System.out.println("Seat booking failed due to payment expiration.");
                    return;
                }
            }

            System.out.println("Seats booked successfully.");
        }

        private boolean confirmSeat(SeatSnapshotHolder seatSnapshot, String taskId, String userId) {
            while (true) {
                SeatSnapshot current = seatSnapshot.getSnapshot();

                if (current.state != SeatState.HELD) {
                    return false;
                }

                if (!userId.equals(current.userId)) {
                    return false;
                }

                if (!taskId.equals(current.taskId)) {
                    return false;
                }

                if (current.isExpired()) {
                    return false;
                }

                SeatSnapshot next = new SeatSnapshot(
                        SeatState.BOOKED,
                        current.userId,
                        current.taskId,
                        current.expiryTime
                );

                if (seatSnapshot.compareAndSet(current, next)) {
                    return true;
                }
            }
        }

        public void rollback(SeatSnapshotHolder seatSnapshot, String taskId, String userId) {
            while (true) {
                SeatSnapshot current = seatSnapshot.getSnapshot();

                if (!canRollback(current, taskId, userId)) {
                    return;
                }

                SeatSnapshot next = new SeatSnapshot(
                        SeatState.AVAILABLE,
                        null,
                        null,
                        0L
                );

                if (seatSnapshot.compareAndSet(current, next)) {
                    return;
                }
            }
        }

        private boolean canRollback(SeatSnapshot current, String taskId, String userId) {
            return current.state != SeatState.AVAILABLE
                    && userId.equals(current.userId)
                    && taskId.equals(current.taskId);
        }


    }

    static class HoldManager {
        private final DelayQueue<DelayedTask> delayQueue = new DelayQueue<>();

        public void drain() {
            while (true) {
                try {
                    DelayedTask task = delayQueue.take();
                    clear(task);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private static void clear(DelayedTask task) {
            SeatSnapshotHolder seatSnapshot = task.getSeatSnapshot();
            String taskId = task.getTaskId();
            String userId = task.getUserId();

            while (true) {
                SeatSnapshot current = seatSnapshot.getSnapshot();

                if (current.state != SeatState.HELD) {
                    return;
                }

                if (!userId.equals(current.userId)) {
                    return;
                }

                if (!taskId.equals(current.taskId)) {
                    return;
                }

                SeatSnapshot next = new SeatSnapshot(
                        SeatState.AVAILABLE,
                        null,
                        null,
                        0L
                );

                if (seatSnapshot.compareAndSet(current, next)) {
                    return;
                }
            }
        }

        public void add(DelayedTask task) {
            delayQueue.offer(task);
        }

    }

    static class DelayedTask implements Delayed {
        private final String userId;
        private final String taskId;
        private final SeatSnapshotHolder seatSnapshot;
        private final long expiryTime;

        public DelayedTask(String userId, String taskId, SeatSnapshotHolder seatSnapshot, long delay) {
            this.userId = userId;
            this.taskId = taskId;
            this.seatSnapshot = seatSnapshot;
            this.expiryTime = System.currentTimeMillis() + delay;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = expiryTime - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        public String getUserId() {
            return userId;
        }

        public String getTaskId() {
            return taskId;
        }

        public SeatSnapshotHolder getSeatSnapshot() {
            return seatSnapshot;
        }

        @Override
        public int compareTo(Delayed o) {
            return o == this ? 0 : Long.compare(this.expiryTime, ((DelayedTask) o).expiryTime);
        }
    }

    static class SeatSnapshotHolder {
        private final AtomicReference<SeatSnapshot> snapshot;

        public SeatSnapshotHolder() {
            this.snapshot = new AtomicReference<>(
                    new SeatSnapshot(SeatState.AVAILABLE, null, null, -1L)
            );
        }

        public SeatSnapshot getSnapshot() {
            return snapshot.get();
        }

        public boolean compareAndSet(SeatSnapshot current, SeatSnapshot next) {
            return snapshot.compareAndSet(current, next);
        }
    }

    static class SeatSnapshot {
        private final SeatState state;
        private final String userId;
        private final String taskId;
        private final long expiryTime;

        public SeatSnapshot(SeatState state, String userId, String taskId, long expiryTime) {
            this.state = state;
            this.userId = userId;
            this.taskId = taskId;
            this.expiryTime = expiryTime;
        }

        public boolean isExpired() {
            return expiryTime > 0 && System.currentTimeMillis() > expiryTime;
        }
    }

    enum SeatState {
        AVAILABLE,
        HELD,
        BOOKED
    }
}