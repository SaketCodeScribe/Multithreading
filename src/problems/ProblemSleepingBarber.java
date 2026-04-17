package problems;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class ProblemSleepingBarber {
}

class SleepingBarberWithSemaphores<T>{
    Queue<T> queue;
    private Semaphore empty;
    private Semaphore mutex;
    private Condition emptyCondition;
    private Lock lock;

    public SleepingBarberWithSemaphores(int size){
        empty = new Semaphore(size, true);
        mutex = new Semaphore(1);
        lock = new ReentrantLock();
        queue = new LinkedList<>();
        emptyCondition = lock.newCondition();
    }
    public void cutHair(Consumer<T> consumer) throws InterruptedException {
        lock.lock();
        try{
            while(true){
                if (!queue.isEmpty()) {
                    mutex.acquire();
                    try {
                        consumer.accept(queue.poll());
                        empty.release();
                    } finally {
                        mutex.release();
                    }
                }
                else {
                    emptyCondition.await();
                }
            }
        }finally {
            lock.unlock();
        }
    }
    public boolean customerArrives(T data) throws InterruptedException {
        if (!empty.tryAcquire()) {
            return false; // no seats, customer leaves
        }
        lock.lock();
        try{
            empty.acquire();
            queue.offer(data);
            emptyCondition.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }
}
