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

class SleepingSingleBarberWithSemaphores<T>{
    Queue<T> queue;
    private Semaphore chair;
    private Lock lock;
    private Condition condition;

    public SleepingBarberWithSemaphores(int size){
        chair = new Semaphore(size, true);
        lock = new ReentrantLock();
        condition = lock.newCondition();
        queue = new LinkedList<>();
    }
    public void cutHair(Consumer<T> consumer) throws InterruptedException {
        while(true){
            lock.lock();
            T customer;
            try {
                while (queue.isEmpty()) {
                    condition.await();
                }
                customer = queue.poll();
                chair.release();
            } finally {
                lock.unlock();
            }
            consumer.accept(customer);
        }
    }
    public boolean customerArrives(T data) throws InterruptedException {
        if (!chair.tryAcquire()) return false;

        lock.lock();
        try{
            queue.offer(data);
            condition.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }
}

class SleepingMBarberWithSemaphores<T>{
    Queue<T> queue;
    private Semaphore chair;
    private Semaphore barber;
    private Lock lock;
    private Condition condition;

    public SleepingMBarberWithSemaphores(int size, int barbers){
        chair = new Semaphore(size, true);
        barber = new Semaphore(barbers, true);
        lock = new ReentrantLock();
        condition = lock.newCondition();
        queue = new LinkedList<>();
    }
    public void cutHair(Consumer<T> consumer) throws InterruptedException {
        while(true){
            barber.acquire();
            lock.lock();
            T customer;
            try {
                while (queue.isEmpty()) {
                    condition.await();
                }
                customer = queue.poll();
                chair.release();
            } finally {
                lock.unlock();
            }
            consumer.accept(customer);
            barber.release();
        }
    }
    public boolean customerArrives(T data) throws InterruptedException {
        if (!chair.tryAcquire()) return false;

        lock.lock();
        try{
            queue.offer(data);
            condition.signalAll();
            return true;
        } finally {
            lock.unlock();
        }
    }
}
