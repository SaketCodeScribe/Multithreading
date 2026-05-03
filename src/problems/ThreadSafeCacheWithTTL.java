package problems;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class ThreadSafeCacheWithTTL<K,V> {
    static class Pair<R>{
        R data;
        Long expiryTime;

        public Pair(R data, Long expiryTime) {
            this.data = data;
            this.expiryTime = expiryTime;
        }
    }
    private final ExecutorService cleanUpExecutor = Executors.newSingleThreadExecutor();
    private final Map<K,Pair<V>> entryMap;
    private final Queue<Pair<K>> buffer;
    private final Semaphore service;
    private final Semaphore mutex;
    private final Semaphore resource;
    private int readerCnt;
    private long startTime;
    private final int LIMIT = 10;
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
        Pair<V> value = null;
        try{
            readResourceAcquire();
            value = entryMap.get(key);
            releaseReadResource();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (value == null){
            return null;
        }
        return value.expiryTime < System.currentTimeMillis() ? null : value.data;
    }

    private void releaseReadResource() throws InterruptedException {
        service.acquire();
        cleanUp();
        mutex.acquire();
        if (readerCnt-- == 1){
            resource.release();
        }
        mutex.release();
        service.release();
    }

    private void cleanUp() throws InterruptedException {
        if (startTime == -1){
            startTime = System.currentTimeMillis();
        } else {
            long elaspedTime = System.currentTimeMillis() - startTime;
            if (elaspedTime >= TTL) {
                try {
                    this.cleanUpExecutor.execute(this::drain);
                    startTime = -1;
                } catch (RuntimeException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedException(e.getMessage());
                }
            }
        }
    }

    private void readResourceAcquire() throws InterruptedException {
        mutex.acquire();
        try{
            if (readerCnt++ == 0){
                resource.acquire();
            }
        } finally {
            mutex.release();
        }
    }

    public void write(K key, V value) throws InterruptedException {
        writeResourceAcquire();
        long timestamp = System.currentTimeMillis() + TTL;
        mutex.acquire();
        entryMap.put(key, new Pair<>(value, timestamp));
        buffer.offer(new Pair<>(key, timestamp));
        releaseWriteResource();
    }

    private void releaseWriteResource() {
        mutex.release();
        resource.release();
        service.release();
    }

    private void writeResourceAcquire() throws InterruptedException {
        service.acquire();
        cleanUp();
        resource.acquire();
    }

    private void drain() {
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            while (!buffer.isEmpty() && buffer.peek().expiryTime < System.currentTimeMillis()) {
                Pair<K> entry = buffer.poll();
                if (Long.compare(entryMap.get(entry.data).expiryTime, entry.expiryTime) == 0) {
                    entryMap.remove(entry.data);
                }
            }
        } finally {
            mutex.release();
        }
    }
}