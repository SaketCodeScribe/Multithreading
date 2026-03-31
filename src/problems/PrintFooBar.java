package problems;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PrintFooBar {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executors = Executors.newFixedThreadPool(2);
        System.out.println("PrintFooBar using flag");
        foobarWithFlag(executors);
        Thread.sleep(1000);

        System.out.println("\n\nPrintFooBar using Semaphores");
        foobarWithSemaphores(executors);
        Thread.sleep(1000);

        System.out.println("\n\nPrintFooBar using condition variables");
        foobarWithConditionVariable(executors);
        executors.shutdown();
    }

    private static void foobarWithConditionVariable(ExecutorService executors) {
        FooBarWithWithConditionVariables fooBar = new FooBarWithWithConditionVariables();
        int n = 100;

        executors.submit(() -> {
            for(int i=1; i<=n; i++){
                fooBar.printFoo(() -> System.out.print("foo"));
            }
        });
        executors.submit(() -> {
            for(int i=1; i<=n; i++){
                fooBar.printBar(() -> System.out.print("bar"));
            }
        });
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

class FooBarWithWithConditionVariables{
    private ReentrantLock lock = new ReentrantLock();
    private Condition foo = lock.newCondition();
    private Condition bar = lock.newCondition();
    private boolean barTurn = false;

    public FooBarWithWithConditionVariables(){ }
    public void printFoo(Runnable task){
        lock.lock();
        try{
            while(barTurn){
                foo.await();
            }
            task.run();
            barTurn = true;
            bar.signal();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }
    public void printBar(Runnable task){
        lock.lock();
        try{
            while(!barTurn){
                bar.await();
            }
            task.run();
            barTurn = false;
            foo.signal();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        finally {
            lock.unlock();
        }
    }
}