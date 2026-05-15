package problems.ConcurrencyDesignProblems;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class DeferredCallbackExecutor<T> {
    DelayQueue<DelayedTask<T>> delayQueue;
    ConcurrentMap<String, CallableTask<T>> concurrentMap;
    Thread workerThread;

    CancellationStrategy cancellationStrategy;

    public DeferredCallbackExecutor() {
        this.delayQueue = new DelayQueue<>();
        concurrentMap = new ConcurrentHashMap<>();
        workerThread = new Thread(this::runScheduledTask, "WorkerThread");
        cancellationStrategy = new EagerCancellationStrategy();
        start();
    }

    public void start() {
        workerThread.start();
    }

    public void shutdown() {
        workerThread.interrupt();
        try {
            workerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void schedule(CallableTask<T> task, long delayInMillis){
        long executionTimeInMillis = delayInMillis + System.currentTimeMillis();
        DelayedTask<T> delayedTask = new DelayedTask<>(executionTimeInMillis, task);
        task.setState(CallableState.SCHEDULED);
        delayQueue.offer(delayedTask);
        concurrentMap.putIfAbsent(task.getTaskId(), task);
    }

    public void cancel(String taskId){
        cancellationStrategy.cancel(taskId);
    }

    private void runScheduledTask(){
        while(!Thread.currentThread().isInterrupted()){
            try {
                CallableTask<T> task = delayQueue.take().getTask();
                if (task.setState(CallableState.RUNNING)){
                    System.out.println(task.run());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    class DelayedTask<V> implements Delayed{
        private final long executionTimeInMillis;
        private final CallableTask<V> task;

        public DelayedTask(long executionTimeInMillis, CallableTask<V> task) {
            this.executionTimeInMillis = executionTimeInMillis;
            this.task = task;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = executionTimeInMillis - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            if (o == this) return 0;
            return Long.compare(this.executionTimeInMillis, ((DelayedTask)o).executionTimeInMillis);
        }

        public CallableTask<V> getTask() {
            return task;
        }

    }

    static class CallableTask<R>{
        private final String taskId;
        private final Callable<R> task;
        private AtomicReference<CallableState> state;

        public CallableTask(String taskId, Callable<R> task) {
            this.taskId = taskId;
            this.task = task;
            this.state = new AtomicReference<>();
        }

        public String getTaskId() {
            return taskId;
        }

        public Callable<R> getTask() {
            return task;
        }

        public boolean setState(CallableState newState) {
            while(true){
                CallableState oldState = state.get();
                if (oldState == CallableState.RUNNING || oldState == CallableState.CANCELLED || oldState == CallableState.COMPLETED) return false;
                if (state.compareAndSet(oldState, newState)){
                    return true;
                }
            }
        }

        public CallableState getState() {
            return state.get();
        }

        public R run() throws Exception {
            R value = task.call();
            setState(CallableState.COMPLETED);
            return value;
        }
    }

    enum CallableState {
        SCHEDULED,
        RUNNING,
        COMPLETED,
        CANCELLED;
    }

    interface CancellationStrategy{
        public void cancel(String taskId);
    }

    class EagerCancellationStrategy implements CancellationStrategy{

        @Override
        public void cancel(String taskId) {
            delayQueue.removeIf(delayedTask -> {
                if (delayedTask.getTask().taskId.equals(taskId)){
                    return delayedTask.getTask().setState(CallableState.CANCELLED);
                }
                return false;
            });
            concurrentMap.remove(taskId);
        }
    }

    class LazyCancellationStrategy implements CancellationStrategy{

        @Override
        public void cancel(String taskId) {
            concurrentMap.computeIfPresent(taskId, (k, v) -> {
                if (!v.setState(CallableState.CANCELLED)) {
                    System.out.println(v.getTask()+" cant be CANCELLED as current state is: "+ v.getState());
                }
                return v;
            });
        }
    }

}

