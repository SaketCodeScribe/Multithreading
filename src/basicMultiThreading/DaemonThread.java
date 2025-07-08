package basicMultiThreading;

/**
 * Once the user threads are completed, JVM shutdowns the daemon thread.
 */
public class DaemonThread {
    public static void main(String[] args) {
        Thread t1 = new Thread(() -> {
            int i = 0;
            while(i < 1000){
                i++;
            }
            System.out.println(Thread.currentThread().getName() + " completed");
        });
        Thread t2 = new Thread(() -> {
            int i = 0;
            while(i < 1000){
                i++;
            }
            System.out.println(Thread.currentThread().getName() + " completed");
        });
        Thread daemonThread = new Thread(() -> {
            int i = 0;
            while(i < 1000){
                try {
                    Thread.sleep(50);
                    i++;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println(Thread.currentThread().getName() + " completed");
        });
        daemonThread.setDaemon(true);
        t1.start();
        t2.start();
        daemonThread.start();
    }
}
