package problems;

import java.util.concurrent.Semaphore;

public class DinerPhilosopher {
}

class DinerPhilosopherWithResourceHierarchy{

    private Semaphore[] forks;
    private int numberOfPhilosopher;
    public DinerPhilosopherWithResourceHierarchy(int n){
        forks = new Semaphore[n];
        for(int i=0; i<n; i++){
            forks[i] = new Semaphore(1, true);
        }
        numberOfPhilosopher = n;
    }

    public void philosopher(int id, Runnable eat) throws InterruptedException {
        int minFork = Math.min(id, (id + 1) % numberOfPhilosopher);
        int maxFork = Math.max(id, (id + 1) % numberOfPhilosopher);

        while (true) {
            thinking(id);
            forks[minFork].acquire();
            try {
                forks[maxFork].acquire();
                try {
                    eat.run();
                } finally {
                    forks[maxFork].release();
                }
            } finally {
                forks[minFork].release();
            }
        }
    }

    private void thinking(int id) {

    }
}

class DinerPhilosopherWithWaiterCoordination{
    private Semaphore[] forks;
    private int numOfPhilosopher;
    private Semaphore coordinator;

    public DinerPhilosopherWithWaiterCoordination(int numOfPhilosopher) {
        this.numOfPhilosopher = numOfPhilosopher;
        forks = new Semaphore[numOfPhilosopher];
        for (int i = 0; i < numOfPhilosopher; i++) {
            forks[i] = new Semaphore(1, true);
        }
        coordinator = new Semaphore(numOfPhilosopher-1, true);
    }

    public void philosopher(int id, Runnable eat){
        int right = (id+1)%numOfPhilosopher;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                thinking(id);
                coordinator.acquire();
                forks[id].acquire();
            forks[right].acquire();
                    eat.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                forks[id].release();
                forks[right].release();
                coordinator.release();
            }
        }
    }

    private void thinking(int id) throws InterruptedException {
        Thread.sleep(10000);
    }
}
