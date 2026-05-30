package problems.ConcurrencyDesignProblems;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 1. Handle race condition for crawled url
 * 2. Control hitting url from same domain to avoid crawler getting blocked.
 * 3. graceful shutdown.
 */
public class ConcurrentWebCrawler {
    private final Set<String> visited;
    private final BlockingQueue<String> frontierQueue;
    private final ExecutorService executorService;
    private final ConcurrentMap<String, Semaphore> token;
    private final ConcurrentLinkedQueue<String> crawledPages;

    public ConcurrentWebCrawler(int tPool) {
        visited = ConcurrentHashMap.newKeySet();
        frontierQueue = new LinkedBlockingQueue<>();
        token = new ConcurrentHashMap<>();
        crawledPages = new ConcurrentLinkedQueue<>();
        executorService = Executors.newFixedThreadPool(tPool, r -> {
            Thread workerThread = new Thread(r, "worker Thread");
            return workerThread;
        });
        Crawler crawler = new Crawler();
        for (int i = 0; i < tPool; i++) {
            executorService.submit(crawler::crawlPage);
        }
    }

    public void add(String url) {
        if (visited.add(url)) {
            frontierQueue.offer(url);
        }
    }

    static class HttpClient {
        public static List<String> getContents(String url){
            return new ArrayList<>();
        }
        public static String getDomain(String url){
            return "Url domain";
        }
    }

    class Crawler{
        public void crawlPage(){
            while(true){
                try {
                    var m = frontierQueue.take();
                    Semaphore p = token.computeIfAbsent(HttpClient.getDomain(m), x -> new Semaphore(1, true));
                    try{
                        p.acquire();
                        List<String> contents = HttpClient.getContents(m);
                        crawledPages.add(m);
                        for (String url : contents) {
                            if (visited.add(url)) {
                                add(url);
                            }
                        }
                    } finally {
                        p.release();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
    public void shutdown() {
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

class WebCrawlerWithBoundedQueue{
    LinkedBlockingQueue<String> frontierQueue;
    ConcurrentMap<String, Semaphore> token;
    ExecutorService executorService;
    Set<String> visited;
    ConcurrentLinkedQueue<String> crawledPages;
    ConcurrentLinkedQueue<String> dirty;
    final int MAX_CONCURRENT_PER_DOMAIN = 10;
    ScheduledExecutorService dirtyScheduler;
    public WebCrawlerWithBoundedQueue(int capacity, int nThreads){
        frontierQueue = new LinkedBlockingQueue<>(capacity);
        token = new ConcurrentHashMap<>();
        executorService = Executors.newFixedThreadPool(nThreads, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        crawledPages = new ConcurrentLinkedQueue<>();
        visited = ConcurrentHashMap.newKeySet();
        dirty = new ConcurrentLinkedQueue<>();
        Crawler crawler = new Crawler(capacity/nThreads, capacity/10, TimeUnit.MILLISECONDS, 10000);
        for(int i=0; i<nThreads; i++){
            executorService.submit(crawler::crawlPage);
        }

        dirtyScheduler = Executors.newScheduledThreadPool(1);
        dirtyScheduler.scheduleAtFixedRate(this::clean, 10000, 10000, TimeUnit.MILLISECONDS);

    }

    private void clean(){
        String m;
        while((m = dirty.poll()) != null){
            add(m);
        }
    }

    public void add(String url) {
        if (visited.add(url)){
            // the queue would never become full as we are flushing urls into the frontier queue in batches. Flush condition is
            // elapsed time or buffer size >= batchSize or buffer is at 70% capacity. So we are controlling the push rate as crawling have higher push than pull.
            if (!frontierQueue.offer(url)){
                System.out.printf("queue is full: %s added to dirty queue for later crawl%n", url);
                dirty.offer(url);
                visited.remove(url);
            }
        }
    }

    class Crawler{
        static class HttpClient {
            public static List<String> getContents(String url){
                return new ArrayList<>();
            }
            public static String getDomain(String url){
                return "Url domain";
            }
        }

        ConcurrentLinkedQueue<String> buffer;
        final double thresholdCapacity = 0.7;
        int capacity;
        int batchSize;
        long delay;
        TimeUnit unit;
        AtomicLong insertedAt;
        private Semaphore permit;
        AtomicInteger bufferSize;
        public Crawler(int size, int bSize, TimeUnit unit, long delay){
            this.capacity = size;
            this.delay = delay;
            this.batchSize = bSize;
            this.bufferSize = new AtomicInteger(0);
            this.unit = unit;
            buffer = new ConcurrentLinkedQueue<>();
            permit = new Semaphore(1);
            insertedAt = new AtomicLong(System.currentTimeMillis());

        }

        public void crawlPage(){
            while(!Thread.currentThread().isInterrupted()){
                long current = System.currentTimeMillis();
                boolean isFlushed = false;
                long elapsed = current - insertedAt.get();
                boolean permitAcq = false, domainPermitAcq = false;
                try {
                    if (permit.tryAcquire()) {
                        permitAcq = true;
                        if (thresholdCapacity * capacity <= bufferSize.get() || elapsed >= delay || buffer.size() >= batchSize) {
                            permit.release();
                            permitAcq = false;
                            flush();
                            isFlushed = true;
                        }
                    }
                    String url = frontierQueue.take();
                    Semaphore domainPermit = token.computeIfAbsent(HttpClient.getDomain(url), p -> new Semaphore(MAX_CONCURRENT_PER_DOMAIN));
                    List<String> content;
                    try {
                        domainPermit.acquire();
                        domainPermitAcq = true;
                        content = HttpClient.getContents(url);
                        crawledPages.add(url);
                        domainPermit.release();
                        domainPermitAcq = false;
                    }  catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } finally {
                        if (domainPermitAcq){
                            domainPermit.release();
                        }
                    }
                    for(String childUrls:content){
                        buffer.offer(childUrls);
                        bufferSize.incrementAndGet();
                        if (isFlushed) {
                            insertedAt.set(System.currentTimeMillis());
                            isFlushed = false;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    if (permitAcq) {
                        permit.release();
                    }
                }
            }
        }
        private void flush() {
            int count = 0;
            String url;
            while (count < batchSize && (url = buffer.poll()) != null) {
                add(url);
                bufferSize.decrementAndGet();
                count++;
            }
        }
    }
    public void shutdown() {
        dirtyScheduler.shutdownNow();
        executorService.shutdownNow();
        try {
            dirtyScheduler.awaitTermination(5, TimeUnit.MILLISECONDS);
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
