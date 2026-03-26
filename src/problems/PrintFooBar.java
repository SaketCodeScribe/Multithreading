package problems;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class PrintFooBar {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executors = Executors.newFixedThreadPool(2);
        System.out.println("PrintFooBar using flag");
        foobarWithFlag(executors);
        Thread.sleep(1000);
        System.out.println("\n\nPrintFooBar using Semaphores");
        foobarWithSemaphores(executors);
        Thread.sleep(1000);
        executors.shutdown();
    }

    private static void foobarWithSemaphores(ExecutorService executors) throws InterruptedException {
        FooBarWithSemaphores obj = new FooBarWithSemaphores(20);
        executors.submit(() -> {
            obj.bar(() -> System.out.print("bar"));
        });
        Thread.sleep(300);
        executors.submit(() -> {
            obj.foo(() -> System.out.print("foo"));
        });

    }

    public static void foobarWithFlag(ExecutorService executors) throws InterruptedException {
        FooBarFlag obj = new FooBarFlag(20);
        executors.submit(() -> {
            obj.bar(() -> System.out.print("bar"));
        });
        executors.submit(() -> {
            obj.foo(() -> System.out.print("foo"));
        });

    }
}

/**
 * Use flag to orchestrate the foo/bar print
 * Pros - no deadlock, no race condition
 * Cons - busy cpu cycle waste
 */
class FooBarFlag{
    private final int n;
    private AtomicBoolean fooTurn = new AtomicBoolean(true);
    public FooBarFlag(int n){
        this.n = n;
    }
    public void foo(Runnable task){
        for(int i=0; i<n; i++){
            while(!fooTurn.get()){}
            task.run();
            fooTurn.set(false);
        }
    }
    public void bar(Runnable task){
        for(int i=0; i<n; i++){
            while(fooTurn.get()){}
            task.run();
            fooTurn.set(true);
        }
    }
}

/**
 * Use semaphores to signal the next iteration
 * Pros - thread doesn't spin or busy-wait. Rather it sleeps and waits to be notified when a permit becomes available.
 */
class FooBarWithSemaphores{
    private final int n;
    private Semaphore fooPermit = new Semaphore(1);
    private Semaphore barPermit = new Semaphore(0);
    public FooBarWithSemaphores(int n){
        this.n = n;
    }
    public void foo(Runnable task) {
        for(int i=0; i<n; i++){
            try {
                fooPermit.acquire();
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finally{
                barPermit.release();
            }
        }
    }
    public void bar(Runnable task){
        for(int i=0; i<n; i++){
            try {
                barPermit.acquire();
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finally{
                fooPermit.release();
            }
        }
    }
}
