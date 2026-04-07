package problems;

import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PrintH2O {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executors = Executors.newFixedThreadPool(9);
        System.out.println("Print H2O with Barrier");
        printH2OWithBarrier(executors);
        executors.shutdown();
    }

    private static void printH2OWithBarrier(ExecutorService executors) {
        H2OWithBarrier obj = new H2OWithBarrier();
        executors.submit(() -> {
            obj.hydrogen();
        });
        executors.submit(() -> {
            obj.oxygen();
        });
        executors.submit(() -> {
            obj.oxygen();
        });
        executors.submit(() -> {
            obj.hydrogen();
        });
        executors.submit(() -> {
            obj.hydrogen();
        });
        executors.submit(() -> {
            obj.hydrogen();
        });
        executors.submit(() -> {
            obj.oxygen();
        });
        executors.submit(() -> {
            obj.hydrogen();
        });
        executors.submit(() -> {
            obj.hydrogen();
        });
    }

}
class H2OWithBarrier{
    private Semaphore hydrogenPermit = new Semaphore(2);
    private Semaphore oxygenPermit = new Semaphore(1);
    private CyclicBarrier barrier = new CyclicBarrier(3, () -> {
        System.out.println("Water molecule created!!");
        hydrogenPermit.release(2);
        oxygenPermit.release();
    });

    public void hydrogen(){
        try{
            hydrogenPermit.acquire();
            barrier.await();
        } catch (BrokenBarrierException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    public void oxygen(){
        try{
            oxygenPermit.acquire();
            barrier.await();
        } catch (BrokenBarrierException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

class H2OWithSemaphores{
    private Semaphore hydrogenPermit = new Semaphore(0);
    private Semaphore oxygenPermit = new Semaphore(0);
    private Semaphore mutex = new Semaphore(1);
    private int hydrogenCnt = 0;
    private int oxygenCnt = 0;

    public void hydrogen(Runnable task) throws InterruptedException {
        mutex.acquire();
        hydrogenCnt++;
        try{
            if (hydrogenCnt > 1 && oxygenCnt > 0){
                hydrogenCnt -= 2;
                hydrogenPermit.release(2);
                oxygenCnt--;
                oxygenPermit.release();
            }
            mutex.release();
            hydrogenPermit.acquire();
            task.run();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    public void oxygen(Runnable task) throws InterruptedException {
        mutex.acquire();
        oxygenCnt++;
        try{
            if (hydrogenCnt > 1 && oxygenCnt > 0){
                hydrogenCnt -= 2;
                hydrogenPermit.release(2);
                oxygenCnt--;
                oxygenPermit.release();
            }
            mutex.release();
            oxygenPermit.acquire();
            task.run();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
