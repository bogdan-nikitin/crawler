package crawler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Executes submitted tasks with tag with a bound on the number of executing tasks with the same tag.
 * Uses supplied {@link ExecutorService} to execute tasks.
 * @param <T> type of tag.
 *
 * @author Bogdan Nikitin
 */
public class BoundedExecutor<T> implements AutoCloseable {
    private static class DeferredEntry {
        public final Deque<Runnable> tasks = new ArrayDeque<>();
        public int executing;
    }

    private final ConcurrentMap<T, DeferredEntry> deferred;
    private final ExecutorService executor;
    private final int bound;

    /**
     * Creates {@code BoundedExecutor}.
     * @param executor executor used to execute tasks.
     * @param bound bound on the number of executing tasks with the same tag.
     */
    public BoundedExecutor(final ExecutorService executor, final int bound) {
        this.executor = executor;
        this.bound = bound;
        this.deferred = new ConcurrentHashMap<>();
    }

    /**
     * Executes the given command sometime in the future.
     * Guaranteed that amount of concurrently executed commands with the same tag
     * will be not greater than specified bound.
     * @param command the task to execute.
     * @param tag task tag.
     * @throws java.util.concurrent.RejectedExecutionException if this task cannot be accepted for execution.
     */
    public void execute(final Runnable command, T tag) {
        deferred.compute(tag, (ignored, info) -> {
            if (info == null) {
                info = new DeferredEntry();
            }
            if (info.executing == bound) {
                info.tasks.add(command);
            } else {
                info.executing++;
                addTask(command, tag);
            }
            return info;
        });
    }

    private void addTask(final Runnable task, final T tag) {
        executor.execute(() -> {
            task.run();
            finishTask(tag);
        });
    }

    private void finishTask(final T tag) {
        deferred.computeIfPresent(tag, (ignored, info) -> {
           if (info.tasks.isEmpty()) {
               if (--info.executing == 0) {
                   return null;
               }
           } else {
               addTask(info.tasks.remove(), tag);
           }
           return info;
        });
    }

    /**
     * Initiates an orderly shutdown in which previously submitted tasks are
     * executed, but no new tasks will be accepted.
     * Shutdown performed by {@link ExecutorService#close()}.
     * @throws SecurityException if {@link ExecutorService#close()} throws.
     */
    @Override
    public void close() {
        executor.close();
    }

    /**
     * Blocks until all tasks have completed execution after a shutdown
     * request, or the timeout occurs, or the current thread is
     * interrupted, whichever happens first.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if this executor terminated and
     *         {@code false} if the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #awaitTermination awaitTermination}
     * to do that.
     *
     * Calls {@link ExecutorService#shutdown()} of underlying executor.
     *
     * @throws SecurityException if a security manager exists and
     *         shutting down this ExecutorService may manipulate
     *         threads that the caller is not permitted to modify
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")},
     *         or the security manager's {@code checkAccess} method
     *         denies access.
     */
    void shutdown() {
        executor.shutdown();
    }
}
