package concurrentUtil;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ConcurrentBlockingQueue {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Integer> blockingQueue = new LinkedBlockingQueue<>(10);
        int i=0;
        blockingQueue.poll(10l, TimeUnit.MILLISECONDS);
        while (i < 10) {
            blockingQueue.offer(i++);
        }
        /**
         * until the queue is free no object will be added to the queue until the consumer frees up the space
        */
        blockingQueue.offer(i++);
        try {
            blockingQueue.add(i++);
        }
        catch (Exception e){
            System.out.println("Illegal State Exception");
        }
        /**
         * this will wait for a free slot to available and then add it to queue
         */
        blockingQueue.put(i++);
        /**
         * It will wait to poll from queue if queue is empty.
         */
        blockingQueue.take();
        /**
         * poll(long, TimeUnit) will wait for the given time to poll from the queue if it's empty,
         * if queue remains empty even after elapsed time, it throws InterruptedException
         */
        blockingQueue.poll(10l, TimeUnit.MILLISECONDS);
        /**
         * poll() simply returns null if queue is empty
         */
        blockingQueue.poll();
    }
}
