package problems;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ProblemUnisexBathroom {
}

enum Person{
    MALE,
    FEMALE;
}

class ConcurrentBathroom{
    private int maleCount = 0;
    private int femaleCount = 0;
    private int currentOccupants = 0;
    private Lock lock = new ReentrantLock();
    private Semaphore restroom = new Semaphore(1);
    private Condition maleCondition = lock.newCondition();
    private Condition femaleCondition = lock.newCondition();
    private Semaphore serviceQueue = new Semaphore(1, true);
    private int capacity;

    public ConcurrentBathroom(int capacity){
        this.capacity = capacity;
    }

    private void maleAcquire(){
        try {
            serviceQueue.acquire();
            lock.lock();
            try{
                maleCount++;
                if (maleCount == 1){
                    restroom.acquire();
                }
                while(currentOccupants >= capacity){
                    maleCondition.await();
                }
                currentOccupants++;
                serviceQueue.release();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            lock.unlock();
        }
    }

    private void maleRelease(){
        lock.lock();
        try {
            maleCount--;
            currentOccupants--;
            if (currentOccupants < capacity) {
                maleCondition.signal();
            }
            if (maleCount == 0) {
                restroom.release();
            }
        } finally {
            lock.unlock();
        }
    }
    private void femaleAcquire(){
        try {
            serviceQueue.acquire();
            lock.lock();
            try{
                femaleCount++;
                if (femaleCount == 1){
                    restroom.acquire();
                }
                while(currentOccupants >= capacity){
                    femaleCondition.await();
                }
                currentOccupants++;
                serviceQueue.release();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            lock.unlock();
        }
    }

    private void femaleRelease(){
        lock.lock();
        try{
            femaleCount--;
            currentOccupants--;
            if (currentOccupants < capacity) {
                femaleCondition.signal();
            }
            if (femaleCount == 0){
                restroom.release();
            }
        } finally {
            lock.unlock();
        }
    }

    public void useWashroom(Person person, Runnable task){
        if (person == Person.MALE){
            maleAcquire();
            task.run();
            maleRelease();
        }
        else{
            femaleAcquire();
            task.run();
            femaleRelease();
        }
    }
}
