package problems;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReaderWriterProblem {
}

// Read heavy application
class ConcurrentReader{
    private int readerCount = 0;
    private Lock lock = new ReentrantLock();
    private Semaphore resourceAccess = new Semaphore(1);

    private void readAcquire() {
        lock.lock();
        try{
            readerCount++;
            if (readerCount == 1){
                resourceAccess.acquire();
            }
        }
        catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
        finally {
            lock.unlock();
        }
    }
    private void readRelease(){
        lock.lock();
        try {
            if (--readerCount == 0) {
                resourceAccess.release();
            }
        }
        finally {
            lock.unlock();
        }
    }
    private void writeAcquire() throws InterruptedException {
        resourceAccess.acquire();
    }
    private void writeRelease(){
        resourceAccess.release();
    }
    public void readResource(Runnable task){
        try {
            readAcquire();
            task.run();
            readRelease();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    public void writeResource(Runnable task){
        try {
            writeAcquire();
            task.run();
            writeRelease();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
