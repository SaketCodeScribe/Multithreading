package problems.ConcurrencyDesignProblems;

import java.util.UUID;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DeferredCallbackExecutor {

    private final DelayQueue<ScheduledTask> delayQueue = new DelayQueue<>();
    private final Thread workerThread;
    private volatile boolean running = true;

    public DeferredCallbackExecutor() {
        workerThread = new Thread(this::workerLoop, "DeferredCallbackExecutor-Thread");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    /**
     * Schedule a callback to run after the specified delay (in milliseconds).
     * Returns a unique task ID that can be used for cancellation.
     */
    public String schedule(Runnable callback, long delayMs) {
        long executeAt = System.currentTimeMillis() + delayMs;
        ScheduledTask task = new ScheduledTask(callback, executeAt);
        delayQueue.offer(task);
        return task.getId();
    }

    /**
     * Cancel a scheduled callback by taskId.
     * Returns true if the task was cancelled before execution.
     */
    public boolean cancel(String taskId) {
        // We can't efficiently remove from DelayQueue, so we mark as cancelled.
        for (ScheduledTask task : delayQueue) {
            if (task.getId().equals(taskId)) {
                return task.cancel();
            }
        }
        return false;
    }

    /**
     * Stops the executor gracefully.
     */
    public void shutdown() {
        running = false;
        workerThread.interrupt();
    }

    /**
     * Main worker loop: takes tasks from the DelayQueue and executes them if not cancelled.
     */
    private void workerLoop() {
        while (running) {
            try {
                ScheduledTask task = delayQueue.take();  // blocks until next task is due
                if (task.tryRun()) {
                    // Successfully executed
                } else {
                    // Task was cancelled or already running
                }
            } catch (InterruptedException e) {
                // If interrupted and not running, exit loop
                if (!running) {
                    break;
                }
            }
        }
    }

    /**
     * Inner class representing a scheduled task.
     * Implements Delayed for DelayQueue.
     */
    private static class ScheduledTask implements Delayed {
        enum State { SCHEDULED, RUNNING, CANCELLED, COMPLETED }

        private final String id;
        private final Runnable callback;
        private final long executeAtMillis;
        private final AtomicReference<State> state = new AtomicReference<>(State.SCHEDULED);

        public ScheduledTask(Runnable callback, long executeAtMillis) {
            this.id = UUID.randomUUID().toString();
            this.callback = callback;
            this.executeAtMillis = executeAtMillis;
        }

        public String getId() {
            return id;
        }

        /**
         * Attempt to cancel the task.
         * Returns true if cancellation succeeded.
         */
        public boolean cancel() {
            return state.compareAndSet(State.SCHEDULED, State.CANCELLED);
        }

        /**
         * Attempt to run the task if not cancelled.
         * Returns true if executed.
         */
        public boolean tryRun() {
            if (!state.compareAndSet(State.SCHEDULED, State.RUNNING)) {
                return false; // Either cancelled or already running
            }
            try {
                callback.run();
            } catch (Throwable t) {
                System.err.println("Callback " + id + " threw exception: " + t);
            } finally {
                state.set(State.COMPLETED);
            }
            return true;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long delay = executeAtMillis - System.currentTimeMillis();
            return unit.convert(delay, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (this == other) return 0;
            if (other instanceof ScheduledTask) {
                return Long.compare(this.executeAtMillis, ((ScheduledTask) other).executeAtMillis);
            }
            long diff = this.getDelay(TimeUnit.MILLISECONDS) - other.getDelay(TimeUnit.MILLISECONDS);
            return (diff == 0) ? 0 : (diff < 0 ? -1 : 1);
        }
    }
}
