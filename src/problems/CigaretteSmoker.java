package problems;

import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CigaretteSmoker {
}

class PusherSmokerCigarette{
    private final Semaphore paper = new Semaphore(0);
    private final Semaphore tobacco = new Semaphore(0);
    private final Semaphore match = new Semaphore(0);

    private final Semaphore smokerTobacco = new Semaphore(0);
    private final Semaphore smokerPaper = new Semaphore(0);
    private final Semaphore smokerMatch = new Semaphore(0);

    private final Semaphore agent = new Semaphore(1);

    private final Lock lock = new ReentrantLock();
    private boolean hasPaper;
    private boolean hasTobacco;
    private boolean hasMatch;

    Random random = new Random();
    public void agent() throws InterruptedException {
        while(true) {
            agent.acquire();
            int val = random.nextInt(3);
            switch (val) {
                case 0:
                    paper.release();
                    tobacco.release();
                    break;
                case 1:
                    paper.release();
                    match.release();
                    break;
                case 2:
                    match.release();
                    tobacco.release();
                    break;
            }
        }
    }
    public void pusherTobacco() throws InterruptedException {
        while(true){
            tobacco.acquire();
            lock.lock();
            if (hasPaper){
                hasPaper = false;
                smokerMatch.release();
            }
            else if (hasMatch){
                hasMatch = false;
                smokerPaper.release();
            }
            else hasTobacco = true;
            lock.unlock();
        }
    }
    public void pusherMatch() throws InterruptedException {
        while(true){
            match.acquire();
            lock.lock();
            if (hasPaper){
                hasPaper = false;
                smokerTobacco.release();
            }
            else if (hasTobacco){
                hasTobacco = false;
                smokerPaper.release();
            }
            else hasMatch = true;
            lock.unlock();
        }
    }
    public void pusherPaper() throws InterruptedException {
        while(true){
            paper.acquire();
            lock.lock();
            if (hasMatch){
                hasMatch = false;
                smokerTobacco.release();
            }
            else if (hasTobacco){
                hasTobacco = false;
                smokerMatch.release();
            }
            else hasPaper = true;
            lock.unlock();
        }
    }

    public void smokeTobacco(Runnable smoke) throws InterruptedException {
        while(true){
            smokerTobacco.acquire();
            smoke.run();
            agent.release();
        }
    }
    public void smokePaper(Runnable smoke) throws InterruptedException {
        while(true){
            smokerPaper.acquire();
            smoke.run();
            agent.release();
        }
    }
    public void smokeMatch(Runnable smoke) throws InterruptedException {
        while(true){
            smokerMatch.acquire();
            smoke.run();
            agent.release();
        }
    }
}