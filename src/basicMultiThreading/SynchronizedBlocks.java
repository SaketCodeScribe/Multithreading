package basicMultiThreading;

public class SynchronizedBlocks {
    static int counter;
    public static void main(String[] args) throws InterruptedException {
        withoutSynchronizedBlock();
        Thread.sleep(500);
        wihtSynchronizedBlock();
    }

    private static void wihtSynchronizedBlock() {
        counter = 0;
        Thread t1 = new Thread(() -> {
            int i = 0;
            while(i < 10000){
                increment();
                i++;
            }
        });
        Thread t2 = new Thread(() -> {
            int i = 0;
            while(i < 10000){
                increment();
                i++;
            }
        });
        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
            Thread.sleep(10);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("counter with synchronized block will always be equal to 20000 -> "+counter);
    }

    private synchronized static void increment() {
        counter++;
    }

    private static void withoutSynchronizedBlock() {
        counter = 0;
        Thread t1 = new Thread(() -> {
            int i = 0;
            while(i < 10000){
                counter++;
                i++;
            }
        });
        Thread t2 = new Thread(() -> {
            int i = 0;
            while(i < 10000){
                counter++;
                i++;
            }
        });
        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
            Thread.sleep(10);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("counter wihtout synchronized block may not be equal to 20000 -> "+counter);
    }
}
