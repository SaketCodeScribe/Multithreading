package problems;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class ProblemBoundedBuffer {
}

class BoundedBufferWithConditionVariables<T> {
    private final Queue<T> queue;
    private final int capacity;
    private final Lock lock = new ReentrantLock();
    private final Condition writeCondition = lock.newCondition();
    private final Condition readCondition = lock.newCondition();

    public BoundedBufferWithConditionVariables(int size) {
        this.capacity = size;
        this.queue = new LinkedList<>();
    }

    public void write(T data) throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() >= capacity) {
                writeCondition.await();
            }
            queue.offer(data);
            readCondition.signal();  // Wake one reader waiting
        } finally {
            lock.unlock();
        }
    }

    public void read(Consumer<T> consumer) throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                readCondition.await();
            }
            consumer.accept(queue.poll());
            writeCondition.signal();  // Wake one writer waiting
        } finally {
            lock.unlock();
        }
    }
}

class BoundedBufferWithSemaphoreBasedSolution<T>{
    Queue<T> queue;
    private Semaphore empty; // slots = capacity
    private Semaphore full;
    private Semaphore mutex;

    public BoundedBufferWithSemaphoreBasedSolution(int size){
        empty = new Semaphore(size);
        full = new Semaphore(0);
        mutex = new Semaphore(1);
        queue = new LinkedList<>();
    }

    public void write(T data) throws InterruptedException {
        empty.acquire();
        mutex.acquire();
        try {
            queue.offer(data);
            full.release();
        } finally {
            mutex.release();
        }
    }

    public void read(Consumer<T> consumer) throws InterruptedException {
        full.acquire();
        mutex.acquire();
        try {
            consumer.accept(queue.poll());
            empty.release();
        } finally {
            mutex.release();
        }
    }
}
