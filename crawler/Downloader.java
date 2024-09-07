package crawler;

import java.io.IOException;

/**
 * Downloads {@link crawler.Document documents}.
 */
public interface Downloader {
    /**
     * Downloads {@link crawler.Document} by URL.
     *
     * @param url URL to download.
     * @return downloaded document.
     * @throws IOException if an error occurred.
     */
    Document download(final String url) throws IOException;
}
