package problems;


import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class SantaClaus {
    private int reindeerCnt;
    private int reindeerCycle;
    private int elvesCnt;
    private final int noOfReindeer;
    private final int noOfElves;
    private boolean reindeerTask;
    private boolean elvesTask;
    private final Semaphore reindeerMutex;
    private final Semaphore elvesMutex;
    private final Semaphore santaResource;
    private final Semaphore elvesResource;
    private final Semaphore reindeerResource;
    private final int MAXCYCLE;
    private final Executor pool = Executors.newSingleThreadExecutor();


    public SantaClaus(int noOfReindeer, int noOfElves) {
        reindeerMutex = new Semaphore(1);
        elvesMutex = new Semaphore(1);
        santaResource = new Semaphore(0);
        reindeerResource = new Semaphore(1);
        elvesResource = new Semaphore(1);
        this.noOfReindeer = noOfReindeer;
        this.noOfElves = noOfElves;
        MAXCYCLE = 5;
        pool.execute(this::santaWorking);
    }

    public void registerReindeer(Runnable task) throws InterruptedException {
        reindeerMutex.acquire();
        try{
            reindeerCnt++;
            if (reindeerCnt == noOfReindeer){
                reindeerTask = true;
                santaResource.release();
                reindeerResource.acquire();
                reindeerCnt = 0;
                reindeerTask = false;
            }
        } finally {
            reindeerMutex.release();
        }
    }
    public void registerElves(Runnable task) throws InterruptedException {
        elvesMutex.acquire();
        try{
            elvesCnt++;
            while (elvesCnt >= noOfElves){
                elvesTask = true;
                santaResource.release();
                elvesResource.acquire();
                elvesCnt -= noOfElves;
                elvesTask = false;
            }
        } finally {
            elvesMutex.release();
        }
    }

    private void santaWorking() {
        while(!Thread.currentThread().isInterrupted()){
            try {
                santaResource.acquire();
                if (reindeerTask && reindeerCycle < MAXCYCLE){
                    santaGivingGifts();
                    reindeerCycle++;
                    reindeerResource.release();
                }
                else if (elvesTask){
                    santaDiscussingWithElves();
                    reindeerCycle = 0;
                    elvesResource.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void santaGivingGifts(){
        System.out.println("santa went out for giving gifts");
    }

    private void santaDiscussingWithElves(){
        System.out.println("santa discussing with elves");
    }

}