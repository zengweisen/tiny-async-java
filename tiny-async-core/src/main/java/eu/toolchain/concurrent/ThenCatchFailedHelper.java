package eu.toolchain.concurrent;

import java.util.function.Function;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ThenCatchFailedHelper<T> implements CompletionHandle<T> {
  private final Function<? super Throwable, ? extends T> transform;
  private final CompletableFuture<T> target;

  @Override
  public void failed(Throwable cause) {
    final T value;

    try {
      value = transform.apply(cause);
    } catch (final Exception e) {
      target.fail(e);
      return;
    }

    target.complete(value);
  }

  @Override
  public void completed(T result) {
    target.complete(result);
  }

  @Override
  public void cancelled() {
    target.cancel();
  }
}
