package basicMultiThreading;

/**
 * Thread priority doesn't guarantee that a higher thread will be executed first.
 * It just tells JVM will select this thread will be more likely.
 * It uses preemptive scheduling to select thread. this algo varies OS to OS.
 */
public class ThreadPriority {
    public static void main(String[] args) {
        Thread t1 = new Thread(() -> {
            for(int i=0; i<100; i++){
                System.out.println("Thread 1"+": "+i);
            }
        });
        Thread t2 = new Thread(() -> {
            for(int i=0; i<100; i++){
                System.out.println("Thread 2"+": "+i);
            }
        });
        t1.setPriority(7);
        t1.start();
        t2.start();
        for(int i=0; i<100; i++){
            System.out.println("Main "+": "+i);
        }
    }
}
