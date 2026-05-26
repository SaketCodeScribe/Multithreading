package problems.ConcurrencyDesignProblems;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

public class ConcurrentWebCrawler {
    private final ConcurrentLinkedQueue<URL> buffer;
    private final ConcurrentHashMap<String, URL> seen;
    private final List<URL> crawledPages;
    private final ExecutorService executorService;
    private final Semaphore permit, consume;
    public ConcurrentWebCrawler(int bufferSize, int threadPool) {
        buffer = new ConcurrentLinkedQueue<>();
        permit = new Semaphore(bufferSize);
        consume = new Semaphore(0);
        executorService = Executors.newFixedThreadPool(threadPool);
        executorService.execute(this::consumeUrl);
        seen = new ConcurrentHashMap<>();
        crawledPages = new CopyOnWriteArrayList<>();
    }

    public void putURL(URL url){
        try {
            permit.acquire();
            buffer.offer(url);
            consume.release();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public void consumeUrl(){
        try {
            consume.acquire();
            crawl(Objects.requireNonNull(buffer.poll()));
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void crawl(URL poll) throws IOException {

        String[] content = ((String) poll.getContent()).split(" ");
        for(String cnt:content){
            if (cnt.contains("//")){
                URL url = new URL(cnt);
                if(seen.putIfAbsent(cnt, url) == null) {
                    putURL(new URL(cnt));
                }
            }
        }
        crawledPages.add(poll);
    }
}
