package problems;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ThreadSafeCacheWithTTL<K,V> {
    static class CacheEntry<R>{
        final R data;
        final Long expiryTime;

        public CacheEntry(R data, Long expiryTime) {
            this.data = data;
            this.expiryTime = expiryTime;
        }
    }
    private final Map<K, CacheEntry<V>> entryMap;
    private final Queue<CacheEntry<K>> buffer;
    private final Semaphore service;
    private final Semaphore mutex;
    private final Semaphore resource;
    private int readerCnt;
    private final long TTL;
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    public ThreadSafeCacheWithTTL(long ttlMillis) {
        this.entryMap = new HashMap<>();
        this.buffer = new LinkedList<>();
        this.resource = new Semaphore(1);
        this.mutex = new Semaphore(1);
        this.service = new Semaphore(1, true);
        TTL = ttlMillis;
        cleaner.scheduleWithFixedDelay(this::cleanUp, ttlMillis, ttlMillis, TimeUnit.MILLISECONDS);


    }

    public V read(K key){
        CacheEntry<V> value = null;
        try{
            readResourceAcquire();
            value = entryMap.get(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            releaseReadResource();
        }
        if (value == null){
            return null;
        }
        return value.expiryTime < System.currentTimeMillis() ? null : value.data;
    }

    public void write(K key, V value) throws InterruptedException {
        try{
            writeResourceAcquire();
            long timestamp = System.currentTimeMillis() + TTL;
            entryMap.put(key, new CacheEntry<>(value, timestamp));
            buffer.offer(new CacheEntry<>(key, timestamp));
        } finally {
            releaseWriteResource();
        }
    }

    private void readResourceAcquire() throws InterruptedException {
        service.acquire();
        mutex.acquire();
        try{
            if (readerCnt++ == 0){
                resource.acquire();
            }
        } finally {
            service.release();
        }
    }

    private void releaseReadResource() {
        try {
            if (--readerCnt == 0) {
                resource.release();
            }
        } finally {
            mutex.release();
        }
    }

    private void writeResourceAcquire() throws InterruptedException {
        service.acquire();
        mutex.acquire();
        resource.acquire();
    }

    private void releaseWriteResource() {
        mutex.release();
        resource.release();
        service.release();
    }

    private void cleanUp() {
        boolean serviceAcquired = false;
        boolean mutexAcquired = false;
        boolean resourceAcquired = false;

        try {
            service.acquire();
            serviceAcquired = true;

            mutex.acquire();
            mutexAcquired = true;

            resource.acquire();
            resourceAcquired = true;

            drain();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdown();
            return;
        } finally {
            if (resourceAcquired) {
                resource.release();
            }

            if (mutexAcquired) {
                mutex.release();
            }

            if (serviceAcquired) {
                service.release();
            }
        }
    }

    private void drain() {
        while (!buffer.isEmpty() && buffer.peek().expiryTime < System.currentTimeMillis()) {
            CacheEntry<K> entry = buffer.poll();
            if (entry != null && entryMap.containsKey(entry.data) && Long.compare(entryMap.get(entry.data).expiryTime, entry.expiryTime) == 0) {
                entryMap.remove(entry.data);
            }
        }
    }
    public void shutdown() {
        cleaner.shutdownNow();
    }
}