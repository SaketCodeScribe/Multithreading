package problems;

import java.util.concurrent.*;

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
