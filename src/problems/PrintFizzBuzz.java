package problems;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class PrintFizzBuzz {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executors = Executors.newFixedThreadPool(4);
        System.out.println("Print fizBuzzNumber using semaphores");
        fizzBuzzUsingSemaphore(executors);
        Thread.sleep(1000);
        executors.shutdown();
    }

    private static void fizzBuzzUsingSemaphore(ExecutorService executors) {
        FizzBuzzWithSemaphores obj = new FizzBuzzWithSemaphores(20);
        executors.submit(() -> {
            obj.number();
        });
        executors.submit(() -> {
            obj.fizz();
        });
        executors.submit(() -> {
            obj.buzz();
        });
        executors.submit(() -> {
            obj.fizzBuzz();
        });
    }
}

class FizzBuzzWithSemaphores{
    private AtomicInteger state = new AtomicInteger(1);
    private final int num;
    private Semaphore numP = new Semaphore(0);
    private Semaphore fizzP = new Semaphore(0);
    private Semaphore buzzP = new Semaphore(0);
    private Semaphore fizzBuzzP = new Semaphore(0);

    public FizzBuzzWithSemaphores(int num){
        this.num = num;
        signalNext(1);
    }
    private void signalNext(int i){
        if (i > num){
            numP.release();
            fizzBuzzP.release();
            fizzP.release();
            buzzP.release();
        }
        else if (i%3 == 0 && i%5 == 0){
            fizzBuzzP.release();
        }
        else if (i%3 == 0){
            fizzP.release();
        }
        else if (i%5 == 0){
            buzzP.release();
        }
        else{
            numP.release();
        }
    }
    public void number(){
        try {
            while(true) {
                numP.acquire();
                int i = state.get();
                if (i > num) return;
                System.out.print(state.get() + ",");
                signalNext(state.incrementAndGet());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    public void fizz(){
        try {
            while(true) {
                fizzP.acquire();
                int i = state.get();
                if (i > num) return;
                System.out.print("fizz,");
                signalNext(state.incrementAndGet());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    public void buzz(){
        try {
            while(true) {
                buzzP.acquire();
                int i = state.get();
                if (i > num) return;
                System.out.print("buzz,");
                signalNext(state.incrementAndGet());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    public void fizzBuzz(){
        try {
            while(true) {
                fizzBuzzP.acquire();
                int i = state.get();
                if (i > num) return;
                System.out.print("fizzBuzz,");
                signalNext(state.incrementAndGet());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
