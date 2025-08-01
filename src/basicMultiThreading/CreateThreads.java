package basicMultiThreading;

public class CreateThreads {
    public static void main(String[] args) {
        Thread t1 = new Thread(() -> {
            for(int i=0; i<50; i++){
                System.out.println(Thread.currentThread().getName()+" "+i);
            }
        });
        Thread t2 = new Thread(() -> {
            for(int i=0; i<50; i++){
                System.out.println(Thread.currentThread().getName()+" "+i);
            }
        });
        t1.start();
        t2.start();
        for(int i=0; i<50; i++){
            System.out.println(Thread.currentThread().getName()+" "+i);
        }
    }
}
