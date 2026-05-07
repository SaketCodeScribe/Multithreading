package problems;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadSafeRateLimiter {

}

class TokenBucket{
    private AtomicLong lastRefilledTime;
    private AtomicInteger tokens;
    private final int CAPACITY = 10;
    private final int TOKEN_RATE = 2;

    public boolean tryAcquire(){
        while(true){
            int token = tokens.get();
            long lastRefillTime = lastRefilledTime.get();
            long current = System.nanoTime();
            long elapsed = current - lastRefillTime;
            int refillToken = refill(elapsed);
            int newToken = Math.min(CAPACITY, token + refillToken);
            if (newToken <= CAPACITY) return false;

            if (tokens.compareAndSet(token, newToken)){
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }
    }

    private int refill(long elapsed){
        return (int)((elapsed / 1_000_000_000.0) * TOKEN_RATE);
    }
}
