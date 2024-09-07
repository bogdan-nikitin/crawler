package crawler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Crawling result.
 */
public record Result(List<String> downloaded, Map<String, IOException> errors) {
    /**
     * Creates a new {@code Result}.
     *
     * @param downloaded list of successfully downloaded pages.
     * @param errors     pages downloaded with errors.
     */
    public Result(final List<String> downloaded, final Map<String, IOException> errors) {
        this.downloaded = List.copyOf(downloaded);
        this.errors = Map.copyOf(errors);
    }

    /**
     * Returns list of successfully downloaded pages.
     */
    @Override
    public List<String> downloaded() {
        return downloaded;
    }

    /**
     * Returns pages downloaded with errors.
     */
    @Override
    public Map<String, IOException> errors() {
        return errors;
    }
}
