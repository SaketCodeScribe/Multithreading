package problems;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadSafeRateLimiter {
    private final ConcurrentMap<String, TokenBucket> buckets;
    private final RateLimiterConfig config;
    private final ScheduledExecutorService cleanUpScheduler;
    private AtomicBoolean running;

    public ThreadSafeRateLimiter(RateLimiterConfig config) {
        this.config = config;
        this.buckets = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(true);

        this.cleanUpScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "rate-limiter-cleanUp");
            thread.setDaemon(true);
            return thread;
        });

        cleanUpScheduler.scheduleAtFixedRate(
                this::cleanupStaleBuckets,
                config.getCleanupInterval().toMillis(),
                config.getCleanupInterval().toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    public ThreadSafeRateLimiter(double tokensPerSecond, long capacity) {
        this(RateLimiterConfig.builder().withTokensPerSecond(tokensPerSecond).withCapacity(capacity).build());
    }

    private void cleanupStaleBuckets() {
        long now = System.nanoTime();
        long timeOut = config.getInactivityTimeout().getNano();

        buckets.entrySet().removeIf(entry -> {
            TokenBucket tokenBucket = entry.getValue();
            return tokenBucket.getLastAccessTime() + timeOut < now;
        });
    }

    public void serveRequest(String clientKey){
        TokenBucket bucket = buckets.computeIfAbsent(clientKey, x -> new TokenBucket(10, 2.5d));
        if (bucket.tryAcquire()){
            System.out.println("Serve Request");
        }
        else {
            System.out.println("Not enough bucket!!");
        }
    }

    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            cleanUpScheduler.shutdown();
            try {
                if (!cleanUpScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanUpScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanUpScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            buckets.clear();
        }
    }

}

class TokenBucket{
    private final AtomicLong lastRefilledTime;
    private final AtomicLong lastAccessTime;
    private AtomicLong tokens;
    private final long PRECISION = 1_000_000;
    private final long capacity;
    private final double tokenRate;

    public TokenBucket(int capacity, double tokenRate){
        this.capacity = capacity * PRECISION;
        this.tokenRate = tokenRate;
        this.lastRefilledTime = new AtomicLong(System.nanoTime());
        this.lastAccessTime = new AtomicLong(System.nanoTime());
    }


    public long getLastAccessTime() {
        return lastAccessTime.get();
    }

    public boolean tryAcquire(){
        while(true) {
            long token = tokens.get();
            long now = System.nanoTime();
            long lastRefillTime = lastRefilledTime.get();
            long elapsedTime = now - lastRefillTime;
            long newTokens = Math.min(capacity, refill(elapsedTime) + token);

            if (newTokens < PRECISION){
                return false;
            }
            long afterConsume = newTokens - PRECISION;

            if (tokens.compareAndSet(token, afterConsume)){
                lastRefilledTime.compareAndSet(lastRefillTime, now);
                lastAccessTime.set(now);
                return true;
            }
        }

    }

    private long refill(long elapsed){
        return (long)(elapsed / 1_000_000_000 * tokenRate);
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
