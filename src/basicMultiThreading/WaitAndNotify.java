package basicMultiThreading;

public class WaitAndNotify {
    private static final Object LOCK = new Object();
    public static void main(String[] args) {
        Thread one = new Thread(() -> {
            try {
                one();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        Thread two = new Thread(() -> {
            try {
                two();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        Thread three = new Thread(() -> {
            try {
                two();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        one.start();
        two.start();
        three.start();
    }
    private static void one() throws InterruptedException {
        synchronized (LOCK){
            System.out.println("Thread one started");
            LOCK.wait();
            System.out.println("Thread one executed after thread two completes it's execution");
        }
    }

    /**
     * Even after calling notify the current thread will complete its execution and then release the lock
     * After that only the threads in waiting state will acquire the lock and execute the remaining code
     */
    private static void two() throws InterruptedException{

        synchronized (LOCK){
            System.out.println(Thread.currentThread().getName());
            LOCK.notify();
            System.out.println("Thread one is notified");
            for(int i=0; i<50; i++){
                System.out.println(Thread.currentThread().getName()+" "+i);
            }
            System.out.println("But thread one will not execute until current thread finishes the execution");
        }
    }
}
