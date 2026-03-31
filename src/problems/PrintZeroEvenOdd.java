package problems;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PrintZeroEvenOdd {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executors = Executors.newFixedThreadPool(3);
        System.out.println("Print Zero Even Odd using condition variables");
        zeroEvenOdd(executors);
        Thread.sleep(1000);
        executors.shutdown();
    }

    private static void zeroEvenOdd(ExecutorService executors) {
        ZeroEvenOddWithConditionVariable obj = new ZeroEvenOddWithConditionVariable();
        executors.submit(() -> {
            for(int i=1; i<=10; i++){
                obj.runZero();
            }
        });
        executors.submit(() -> {
            for(int i=1; i<=5; i++){
                obj.runOdd();
            }
        });
        executors.submit(() -> {
            for(int i=1; i<=5; i++){
                obj.runEven();
            }
        });
    }
}
class ZeroEvenOddWithConditionVariable{
    private int phase = 1;
    private boolean zeroTurn = true;
    private Lock lock = new ReentrantLock();
    private Condition zero = lock.newCondition();
    private Condition odd = lock.newCondition();
    private Condition even = lock.newCondition();

    public void runZero(){
        lock.lock();
        try{
            while(!zeroTurn){
                zero.await();
            }
            System.out.print(0);
            zeroTurn = false;
            if (phase%2 != 0) {
                odd.signal();
            }
            else {
                even.signal();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            lock.unlock();
        }
    }
    public void runEven(){
        lock.lock();
        try{
            while(phase%2 != 0 || zeroTurn){
                even.await();
            }
            System.out.print(phase);
            phase++;
            zeroTurn = true;
            zero.signal();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            lock.unlock();
        }
    }
    public void runOdd(){
        lock.lock();
        try{
            while(phase%2 == 0 || zeroTurn){
                odd.await();
            }
            System.out.print(phase);
            phase++;
            zeroTurn = true;
            zero.signal();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            lock.unlock();
        }
    }
}
