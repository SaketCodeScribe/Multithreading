package problems;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Semaphore;

public class ThreadSafeCacheWithTTL<K,V> {
    static class CacheEntry<R>{
        R data;
        Long expiryTime;

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
    private long startTime;
    private final long TTL;
    public ThreadSafeCacheWithTTL(long ttl) {
        this.entryMap = new HashMap<>();
        this.buffer = new LinkedList<>();
        this.resource = new Semaphore(1);
        this.mutex = new Semaphore(1);
        this.service = new Semaphore(1, true);
        TTL = ttl;
        startTime = -1;
    }

    public V read(K key){
        CacheEntry<V> value = null;
        try{
            readResourceAcquire();
            value = entryMap.get(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mutex.release();
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
            cleanUp();
        } finally {
            service.release();
        }
    }

    private void releaseReadResource() {
        if (readerCnt-- == 1) {
            resource.release();
        }
    }

    private void writeResourceAcquire() throws InterruptedException {
        service.acquire();
        mutex.acquire();
        resource.acquire();
        cleanUp();
    }

    private void releaseWriteResource() {
        mutex.release();
        resource.release();
        service.release();
    }

    private void cleanUp() throws InterruptedException {
        if (startTime == -1){
            startTime = System.currentTimeMillis();
        } else {
            if (System.currentTimeMillis() - startTime >= TTL) {
                drain();
                startTime = -1;
            }
        }
    }

    private void drain() {
        while (!buffer.isEmpty() && buffer.peek().expiryTime < System.currentTimeMillis()) {
            CacheEntry<K> entry = buffer.poll();
            if (entry != null && Long.compare(entryMap.get(entry.data).expiryTime, entry.expiryTime) == 0) {
                entryMap.remove(entry.data);
            }
        }
    }
}