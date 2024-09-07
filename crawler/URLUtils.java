package crawler;

import java.net.MalformedURLException;

public final class URLUtils {
    private URLUtils() {}

    /**
     * Returns host part of the specified URL.
     *
     * @param url url to get host part for.
     *
     * @return host part of the provided URL or empty string if URL has no host part.
     *
     * @throws MalformedURLException if specified URL is invalid.
     */
    public static String getHost(final String url) throws MalformedURLException {
        throw new UnsupportedOperationException("Provide implementation");
    }
}
