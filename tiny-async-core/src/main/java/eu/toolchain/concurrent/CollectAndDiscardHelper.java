package eu.toolchain.concurrent;

import static eu.toolchain.concurrent.CoreAsync.buildCollectedException;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of {@link Async#collectAndDiscard(Collection)}.
 *
 * @author udoprog
 */
public class CollectAndDiscardHelper implements CompletionHandle<Object> {
  private final CompletableFuture<Void> target;
  private final AtomicInteger countdown;
  private final AtomicInteger cancelled = new AtomicInteger();
  private final ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

  public CollectAndDiscardHelper(int size, CompletableFuture<Void> target) {
    this.target = target;
    this.countdown = new AtomicInteger(size);
  }

  @Override
  public void failed(Throwable e) {
    errors.add(e);
    check();
  }

  @Override
  public void completed(Object result) {
    check();
  }

  @Override
  public void cancelled() {
    cancelled.incrementAndGet();
    check();
  }

  private void done() {
    if (!errors.isEmpty()) {
      target.fail(buildCollectedException(errors));
      return;
    }

    if (cancelled.get() > 0) {
      target.cancel();
      return;
    }

    target.complete(null);
  }

  private void check() {
    if (countdown.decrementAndGet() == 0) {
      done();
    }
  }
}