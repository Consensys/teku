package tech.pegasys.artemis.util.future;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class FutureUtils {
  public static <T> CompletableFuture<T> wrapInFuture(final Callable<T> action) {
    try {
      return CompletableFuture.completedFuture(action.call());
    } catch (final Throwable t) {
      return CompletableFuture.failedFuture(t);
    }
  }
}
