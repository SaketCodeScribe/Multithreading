package problems.ConcurrencyDesignProblems;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class DeferredCallbackExecutorWithSystemCrash {
    ConsumerEventQueue<ScheduledTask> consumerEventQueue;
    ConcurrentMap<String, Task> map;
    ProducerQueue<ScheduledTask> producerQueue;

    private final Executor executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ConsumerEventQueue Daemon Thread");
        t.setDaemon(true);
        return t;
    });

    public DeferredCallbackExecutor(ConsumerEventQueue<ScheduledTask> consumerEventQueue, Map<String, ScheduledTask> map, ProducerQueue<ScheduledTask> producerQueue) {
        this.consumerEventQueue = consumerEventQueue;
        this.map = new ConcurrentHashMap<>();
        this.producerQueue = producerQueue;
        executor.execute(this::execute);
    }

    public void schedule(Task task){
        map.putIfAbsent(task.getTaskId(), task);
        producerQueue.add(task.getTask());
    }

    public void cancelTask(String taskId){
        map.computeIfPresent(taskId, ((s, task) -> {
            task.getTask().setState(CallbackState.CANCELLED);
            return task;
        }));
    }

    private void execute(){
        consumerEventQueue.add(producerQueue.poll());
    }
}

class Task{
    private ScheduledTask task;
    private String taskId;

    public Task(ScheduledTask task, String taskId) {
        this.task = task;
        this.taskId = taskId;
    }

    public ScheduledTask getTask() {
        return task;
    }

    public String getTaskId() {
        return taskId;
    }
}

class ConsumerEventQueue<T extends Delayed> implements EventQueue<T> {
    private final DelayQueue<T> delayQueue;
    private final Executor executor;
    private final CompletableFutureChain<T> completableFutureChain;

    public ConsumerEventQueue(){
        delayQueue = new DelayQueue<>();
        completableFutureChain = new CompletableFutureChain<>();
        executor = Executors.newFixedThreadPool(10, new EventQueue.ThreadProviderFactory());
        Thread backgroundThread = new Thread(this::run, "Consumer Event queue background thread");
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }

    public void add(T task){
        delayQueue.offer(task);
    }

    private void run(){
        while (!delayQueue.isEmpty()){
            try {
                T task = delayQueue.take();
                this.completableFutureChain.thenCompose(this::execute);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private CompletableFuture<Void> execute(T task){
        return CompletableFuture.completedFuture(task).
                thenApplyAsync(t -> {
                    ((ScheduledTask)t).run();
                    return null;
                }, executor);
    }

}

interface EventQueue<T>{
    static class ThreadProviderFactory implements ThreadFactory{
        AtomicInteger cnt = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Consumer Thread " + cnt.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}

class CompletableFutureChain<T>{
    private CompletableFuture<Void> COMPLETE_FUTURE = CompletableFuture.completedFuture(null);
    private CompletableFuture<Void> CANCELLED_FUTURE = CompletableFuture.failedFuture(new CancellationException());

    private final Queue<CompletableFuture<T>> chain;
    private CompletableFuture<CompletableFuture<Void>> tail;

    private boolean cancelled;

    public CompletableFutureChain() {
        this.chain = new LinkedList<>();
        this.tail = COMPLETE_FUTURE;
    }

    public void thenCompose(Function<T, CompletableFuture<Void>> fn){
        this.cancelled = false;
        var future = this.tail.thenCompose(t -> {
            if (this.cancelled){
                return CANCELLED_FUTURE;
            }
            return fn.apply(t);
        });
        chain.offer(future);
        this.tail = future;
    }

    public void drain(){
        while(!chain.isEmpty()){
            if (chain.peek().isDone()){
                try {
                    ((CallableTask) chain.poll().get()).setState(CallbackState.COMPLETED);
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void clear(){
        this.cancelled = true;
        this.chain.clear();
    }
}

class ProducerQueue<T>{
    static class Node<T> {
        T task;
        Node<T> next;
        Node<T> prev;

        public Node(T task) {
            this.task = task;
        }

    }

    private final Lock lock;
    private final Condition condition;
    private final AtomicReference<Node<T>> top;
    private final AtomicReference<Node<T>> tail;
    {
        top = tail = new AtomicReference<>();
        executor.execute(this::poll);
        lock = new ReentrantLock();
        condition = lock.newCondition();
    }

    /**
     * CAS operation
     * @param task
     */
    public void add(T task){
        Node<T> newHead = new Node<>(task);
        while(true){
            Node<T> oldHead = top.get();
            Node<T> last = tail.get();
            newHead.next = oldHead;
            if (top.compareAndSet(oldHead, newHead)){
                if (oldHead != null) {
                    oldHead.prev = newHead;
                }
                if (tail.get() == null){
                    tail.set(oldHead);
                    condition.signal();
                }
            }
        }
    }

    /**
     * CAS operation
     */
    public T poll(){
        while(true){
            Node<T> peek = tail.get();
            if (peek == null){
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            Node<T> newPeek = peek.prev;
            if (tail.compareAndSet(peek, newPeek)){
                return peek.task;
            }
        }
    }
}
enum CallbackState{
    SCHEDULED,
    RUNNING,
    COMPLETED,
    CANCELLED;
}

class CallableTask{
    CallbackState state;
    Runnable task;

    public CallableTask(Runnable task) {
        this.task = task;
    }

    public void run(){
        task.run();
    }

    public void setState(CallbackState state) {
        this.state = state;
    }
}

class ScheduledTask implements Delayed {
    private final long scheduledTime;
    private final CallableTask task;

    public ScheduledTask(long scheduledTime, CallableTask task) {
        this.scheduledTime = scheduledTime;
        this.task = task;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.toMillis(scheduledTime);
    }

    @Override
    public int compareTo(Delayed o) {
        if (o == this) return 0;
        Delayed other = (Delayed) o;
        return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
    }

    public void run(){
        task.run();
    }

    public void setState(CallbackState state){
        this.task.setState(state);
    }
}