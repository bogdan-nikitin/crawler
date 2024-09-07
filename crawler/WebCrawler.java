package crawler;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Crawls websites in parallel.
 * Creates separate pools of workers to download websites and to extract URLs from websites.
 * This class in thread-safe.
 *
 * @author Bogdan Nikitin
 */
public class WebCrawler {
    private final BoundedExecutor<String> downloadExecutor;
    private final ExecutorService extractExecutor;
    private final Downloader downloader;

    /**
     * Creates {@code WebCrawler} and starts pools of workers.
     *
     * @param downloader  downloader used to download sites.
     * @param downloaders max amount of downloading workers.
     * @param extractors  max amount of extracting workers.
     * @param perHost     max amount of sites downloaded from same host.
     */
    public WebCrawler(final Downloader downloader, final int downloaders, final int extractors, final int perHost) {
        this.downloader = downloader;
        this.downloadExecutor = new BoundedExecutor<>(Executors.newFixedThreadPool(downloaders), perHost);
        this.extractExecutor = Executors.newFixedThreadPool(extractors);
    }

    /**
     * Downloads website up to specified depth.
     *
     * @param url      start URL.
     * @param depth    download depth.
     * @param excludes URLs containing one of given substrings are ignored.
     * @return download result.
     */
    public Result download(final String url, final int depth, final Set<String> excludes) {
        return new DownloadRunner(excludes).download(url, depth);
    }

    /**
     * Downloads website up to specified depth.
     *
     * @param url   start <a href="http://tools.ietf.org/html/rfc3986">URL</a>.
     * @param depth download depth.
     * @return download result.
     */
    public Result download(final String url, final int depth) {
        return download(url, depth, Collections.emptySet());
    }

    /**
     * Closes this crawler, freeing executors.
     */
    public void close() {
        boolean wasInterrupted = Thread.interrupted();
        downloadExecutor.shutdown();
        extractExecutor.shutdown();
        while (true) {
            try {
                if (downloadExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS) &&
                        extractExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)) {
                    break;
                }
            } catch (InterruptedException e) {
                wasInterrupted = true;
            }
        }
        if (wasInterrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private class DownloadRunner {
        private final Set<String> excluded;
        private final List<String> downloaded = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, IOException> errors = new ConcurrentHashMap<>();

        private final Set<String> visited = ConcurrentHashMap.newKeySet();
        private Phaser incrementDepth;
        private List<String> nextPending;

        public DownloadRunner(final Set<String> excludes) {
            excluded = excludes;
        }

        private void markError(final String url, final IOException exception) {
            errors.put(url, exception);
            incrementDepth.arrive();
        }

        public void addDownloadTask(final String url) {
            final String host;
            try {
                host = URLUtils.getHost(url);
            } catch (final MalformedURLException e) {
                markError(url, e);
                return;
            }
            try {
                downloadExecutor.execute(() -> {
                    final Document document;
                    try {
                        document = downloader.download(url);
                    } catch (final IOException e) {
                        markError(url, e);
                        return;
                    }
                    downloaded.add(url);
                    addExtractTask(document, url);
                }, host);
            } catch (RejectedExecutionException e) {
                incrementDepth.arrive();
            }
        }

        private void addExtractTask(final Document document, final String extractUrl) {
            try {
                extractExecutor.execute(() -> {
                    final List<String> urls;
                    try {
                        urls = document.extractLinks();
                    } catch (final IOException e) {
                        markError(extractUrl, e);
                        return;
                    }
                    nextPending.addAll(urls.stream().filter(this::needsDownloading).toList());
                    incrementDepth.arrive();
                });
            } catch (final RejectedExecutionException ignored) {
                incrementDepth.arrive();
            }
        }

        private boolean needsDownloading(final String url) {
            return excluded.stream().noneMatch(url::contains) && markVisited(url);
        }

        private boolean markVisited(final String url) {
            return visited.add(url);
        }

        public Result download(final String url, final int depth) {
            if (needsDownloading(url)) {
                nextPending = Collections.synchronizedList(new ArrayList<>());
                List<String> pending = Collections.synchronizedList(new ArrayList<>());
                nextPending.add(url);
                for (int i = 0; i < depth; ++i) {
                    incrementDepth = new Phaser(nextPending.size() + 1);
                    final List<String> temp = pending;
                    pending = nextPending;
                    nextPending = temp;
                    nextPending.clear();
                    pending.forEach(this::addDownloadTask);
                    incrementDepth.arriveAndAwaitAdvance();
                }
            }
            return new Result(downloaded, errors);
        }
    }
}
