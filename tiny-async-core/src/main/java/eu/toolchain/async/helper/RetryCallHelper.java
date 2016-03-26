package eu.toolchain.async.helper;

import eu.toolchain.async.AsyncFuture;
import eu.toolchain.async.FutureDone;
import eu.toolchain.async.ResolvableFuture;
import eu.toolchain.async.RetryPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A helper class for retry-until-resolved behaviour.
 * <p>
 * retry-until-resolved is provided by {@link eu.toolchain.async
 * .AsyncFramework#retryUntilResolved(java.util.concurrent.Callable,
 * eu.toolchain.async.RetryPolicy)}.
 *
 * @param <T> The type of the class.
 */
public class RetryCallHelper<T> implements FutureDone<T> {
    private final ScheduledExecutorService scheduler;
    private final Callable<? extends AsyncFuture<? extends T>> action;
    private final RetryPolicy policy;
    private final ResolvableFuture<T> future;

    /*
     * Does not require synchronization since the behaviour of this helper guarantees that only
     * one thread at a time accesses it
     */
    private final ArrayList<Throwable> errors = new ArrayList<>();
    private final AtomicReference<ScheduledFuture<?>> nextCall = new AtomicReference<>();

    public RetryCallHelper(
        final ScheduledExecutorService scheduler,
        final Callable<? extends AsyncFuture<? extends T>> callable, final RetryPolicy policy,
        final ResolvableFuture<T> future
    ) {
        this.scheduler = scheduler;
        this.action = callable;
        this.policy = policy;
        this.future = future;
    }

    public List<Throwable> getErrors() {
        return errors;
    }

    @Override
    public void failed(final Throwable cause) throws Exception {
        final RetryPolicy.Decision decision = policy.apply();

        if (!decision.shouldRetry()) {
            for (final Throwable suppressed : errors) {
                cause.addSuppressed(suppressed);
            }

            future.fail(cause);
            return;
        }

        errors.add(cause);

        if (decision.backoff() <= 0) {
            next();
        } else {
            nextCall.set(scheduler.schedule(() -> {
                nextCall.set(null);
                next();
            }, decision.backoff(), TimeUnit.MILLISECONDS));
        }
    }

    @Override
    public void resolved(final T result) throws Exception {
        future.resolve(result);
    }

    @Override
    public void cancelled() throws Exception {
        future.cancel();
    }

    public void next() {
        if (future.isDone()) {
            throw new IllegalStateException("Target future is done");
        }

        final AsyncFuture<? extends T> result;

        try {
            result = action.call();
        } catch (final Exception e) {
            // inner catch, since the policy might be user-provided.
            try {
                failed(e);
            } catch (final Exception inner) {
                inner.addSuppressed(e);
                future.fail(inner);
            }

            return;
        }

        if (result == null) {
            future.fail(new IllegalStateException("Retry action returned null"));
            return;
        }

        result.onDone(this);
    }

    /**
     * Must be called when the target future finishes to clean up any potential scheduled _future_
     * events.
     */
    public void finished() {
        final ScheduledFuture<?> scheduled = nextCall.getAndSet(null);

        if (scheduled != null) {
            scheduled.cancel(true);
        }
    }
}
