package problems.ConcurrencyDesignProblems;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

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
