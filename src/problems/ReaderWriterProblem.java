package problems;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReaderWriterProblem {
}

// Read heavy application
class ReaderHeavy{
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
        readAcquire();
        task.run();
        readRelease();
    }
    public void writeResource(Runnable task){
        try {
            writeAcquire();
            task.run();
            writeRelease();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

class WriterHeavy{
    private int readerCnt = 0;
    private int writerCnt = 0;
    private Lock readerLock = new ReentrantLock();
    private Lock writerLock = new ReentrantLock();
    private Semaphore resource = new Semaphore(1);
    private Semaphore readTry = new Semaphore(1);

    private void readAcquire() throws InterruptedException {
        readTry.acquire();
        try{
            readerLock.lock();
            try {
                readerCnt++;
                if (readerCnt == 1) {
                    resource.acquire();
                }
            }
            finally {
                readerLock.unlock();
            }
        }
        finally {
            readTry.release();
        }
    }
    private void readRelease() {
        readerLock.lock();
        if (--readerCnt == 0){
            resource.release();
        }
        readerLock.unlock();
    }
    public void readResource(Runnable task){
        try {
            readAcquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        task.run();
        readRelease();
    }

    public void writeResource(Runnable task){
        try {
            writeAcquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        task.run();
        writeRelease();
    }

    private void writeAcquire() throws InterruptedException {
        writerLock.lock();
        try{
            writerCnt++;
            if (writerCnt == 1){
                readTry.acquire();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            writerLock.unlock();
        }
        resource.acquire();
    }

    private void writeRelease() {
        writerLock.lock();
        if (--writerCnt == 0){
            readTry.release();
            resource.release();
        }
    }
}

class FairReadWrite{
    private int readCount = 0;
    private Lock lock = new ReentrantLock();
    private Semaphore resource = new Semaphore(1);
    private Semaphore serviceQueue = new Semaphore(1, true);

    private void readAcquire(){
        try {
            serviceQueue.acquire();
            lock.lock();
            try{
                if (++readCount == 1){
                    resource.acquire();
                }
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        finally {
            lock.unlock();
            serviceQueue.release();
        }
    }
    private void readRelease(){
        lock.lock();
        if (--readCount == 0){
            resource.release();
        }
    }
    public void readResource(Runnable task){
        readAcquire();
        task.run();
        readRelease();
    }
    public void writeResource(Runnable task) {
        try {
            serviceQueue.acquire();
            resource.acquire();
            task.run();
        }catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        resource.release();
        serviceQueue.release();
    }
}