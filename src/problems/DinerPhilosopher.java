package problems;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DinerPhilosopher {
    static class Pair<K, V>{
        K key;
        V value;

        public Pair(K id, V eat) {
            key = id;
            value = eat;
        }
    }
    private Semaphore[] forks;
    private int numberOfPhilosopher;
    private Queue<Pair<Integer, Runnable>> queue;
    private Lock lock;
    public DinerPhilosopher(int n){
        forks = new Semaphore[n];
        for(int i=0; i<n; i++){
            forks[i] = new Semaphore(1, true);
        }
        numberOfPhilosopher = n;
        queue = new LinkedList<>();
        lock = new ReentrantLock();
    }

    public void philosopher(int id, Runnable eat) throws InterruptedException {
        thinking(id);
        lock.lock();
        try{
            queue.offer(new Pair<>(id, eat));
        }
        finally {
            lock.unlock();
        }
    }

    private void thinking(int id) {

    }

    private void eat(){
        lock.lock();
        try {
            while (!queue.isEmpty()) {
                Pair<Integer, Runnable> data = queue.poll();
                int id = data.key;
                int prev = id;
                int next = (prev+1)%numberOfPhilosopher;
                if (forks[prev].tryAcquire()){
                    if (forks[next].tryAcquire()) {
                        data.value.run();
                        forks[next].release();
                    }
                    else{
                        queue.offer(data);
                    }
                    forks[prev].release();
                }
                else{
                    queue.offer(data);
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
