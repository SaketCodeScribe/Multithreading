package basicMultiThreading;

public class ThreadJoining {
    public static void main(String[] args) throws InterruptedException {
        Thread t1 = new Thread(() -> {
            for(int i=0; i<50; i++){
                System.out.println("Thread 1"+": "+i);
            }
        });
        Thread t2 = new Thread(() -> {
            for(int i=0; i<50; i++){
                System.out.println("Thread 2"+": "+i);
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        for(int i=0; i<50; i++){
            System.out.println("Main "+": "+i);
        }
    }
}
