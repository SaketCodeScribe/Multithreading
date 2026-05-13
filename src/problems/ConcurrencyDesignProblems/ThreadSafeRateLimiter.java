package problems.ConcurrencyDesignProblems;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadSafeRateLimiter {
    private final ConcurrentMap<String, TokenBucket> buffer;
    private final ScheduledExecutorService cleaner;
    private final RateLimiterConfig config;

    public ThreadSafeRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.buffer = new ConcurrentHashMap<>();
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cleanerThread");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(
                this::cleanUp,
                config.getCleanupInterval().toNanos(),
                config.getCleanupInterval().toNanos(),
                TimeUnit.NANOSECONDS);
    }

    private void cleanUp() {
        long now = System.nanoTime();
        long inactivityTimeout = config.getInactivityTimeout().toNanos();
        buffer.entrySet().removeIf(entry -> {
           TokenBucket bucket = entry.getValue();
            return bucket.getLastAccessTime() + inactivityTimeout < now;
        });
    }

    public boolean canServeRequest(String clientId){
       TokenBucket bucket = buffer.computeIfAbsent(clientId, t -> new TokenBucket(config.getCapacity(), config.getTokensPerSecond()));
       return bucket.tryAcquire();
    }
}

class TokenBucket{
    private final AtomicLong tokens;
    private final AtomicLong lastAccessTime;
    private final AtomicLong lastRefilledTime;
    private final int PRECISION = 1_000_000;
    private final long capacity;
    private final double rate;

    public TokenBucket(long capacity, double rate) {
        this.capacity = capacity * PRECISION;
        this.rate = rate;
        this.tokens = new AtomicLong(this.capacity);
        this.lastAccessTime = new AtomicLong(System.nanoTime());
        this.lastRefilledTime = new AtomicLong(System.nanoTime());
    }
    public boolean tryAcquire(){
        while(true){
            long token = tokens.get();
            long lastRefillTime = lastRefilledTime.get();;
            long now = System.nanoTime();
            long elapsedTime = now - lastRefillTime;
            long newToken = Math.min(this.capacity, (long)(refill(elapsedTime) * PRECISION) + token);

            if (newToken < PRECISION) return false;

            long afterConsume = newToken - PRECISION;
            if (tokens.compareAndSet(token, afterConsume)){
                lastRefilledTime.set(now);
                lastAccessTime.set(now);
                return true;
            }
        }
    }

    public long getLastAccessTime() {
        return lastAccessTime.get();
    }

    private double refill(long elapsedTime) {
        return rate * elapsedTime;
    }

    public String TokenInfo(){
        return "["+tokens.get()+", "+lastRefilledTime.get()+"]";
    }
}
class RateLimiterConfig{
    private final double tokensPerSecond;
    private final long capacity;
    private final Duration cleanupInterval;
    private final Duration inactivityTimeout;
    public static Builder builder(){
        return new Builder();
    }

    private RateLimiterConfig(Builder builder){
        this.tokensPerSecond = builder.tokensPerSecond;
        this.capacity = builder.capacity;
        this.cleanupInterval = builder.cleanupInterval;
        this.inactivityTimeout = builder.inactivityTimeout;
    }

    public double getTokensPerSecond() {
        return tokensPerSecond;
    }

    public long getCapacity() {
        return capacity;
    }

    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    public Duration getInactivityTimeout() {
        return inactivityTimeout;
    }

    static class Builder{
        private double tokensPerSecond;
        private long capacity;
        private Duration cleanupInterval = Duration.ofMinutes(1);
        private Duration inactivityTimeout = Duration.ofMinutes(5);

        public Builder withTokensPerSecond(double tokensPerSecond){
            this.tokensPerSecond = tokensPerSecond;
            return this;
        }
        public Builder withCapacity(long capacity){
            this.capacity = capacity;
            return this;
        }
        public Builder withCleanupInterval(Duration cleanupInterval){
            this.cleanupInterval = cleanupInterval;
            return this;
        }
        public Builder withInactivityInterval(Duration inactivityTimeout){
            this.inactivityTimeout = inactivityTimeout;
            return this;
        }
        public RateLimiterConfig build(){
            return new RateLimiterConfig(this);
        }
    }
}
