package problems;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class PrintH2O {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executors = Executors.newFixedThreadPool(2);
        System.out.println("Print H2O using barriers");
        h2oWithBarrier(executors);
        Thread.sleep(1000);
        executors.shutdown();
    }

    private static void h2oWithBarrier(ExecutorService executors) {
        H2OWithBarrier obj = new H2OWithBarrier();
        executors.submit(() -> {
            for(char atom:new char[]{'H','H','H','H','H','H'}){
                obj.createH2OMolecule(atom);
            }
        });
        executors.submit(() -> {
            for(char atom:new char[]{'O','O','O'}){
                obj.createH2OMolecule(atom);
            }
        });

    }
}
class H2OWithBarrier{
    CyclicBarrier barrier = new CyclicBarrier(2, () -> System.out.println("Water molecule created!!"));
    int hydrogen = 0;
   int oxygen = 0;

    public void createH2OMolecule(char atom){
        try {
            if (atom == 'H'){
                hydrogen++;
                if (hydrogen > 0 && hydrogen %2 == 0){
                        barrier.await();
                        hydrogen -= 2;
                }
            }
            else{
                oxygen++;
                barrier.await();
                oxygen--;
            }
        } catch (InterruptedException | BrokenBarrierException e) {
            Thread.currentThread().interrupt();
        }
    }
}
