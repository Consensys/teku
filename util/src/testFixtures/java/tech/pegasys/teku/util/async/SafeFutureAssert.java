package tech.pegasys.teku.util.async;

import java.util.concurrent.CompletionException;
import org.assertj.core.api.AbstractCompletableFutureAssert;
import org.assertj.core.api.Assertions;

public class SafeFutureAssert<T> extends AbstractCompletableFutureAssert<SafeFutureAssert<T>, T> {

  private SafeFutureAssert(final SafeFuture<T> actual) {
    super(actual, SafeFutureAssert.class);
  }

  public static <T> SafeFutureAssert<T> assertThatSafeFuture(final SafeFuture<T> actual) {
    return new SafeFutureAssert<>(actual);
  }

  public void isCompletedExceptionallyWith(final Throwable t) {
    isCompletedExceptionally();
    Assertions.assertThatThrownBy(actual::join)
        .isInstanceOf(CompletionException.class)
        .extracting(Throwable::getCause)
        .isSameAs(t);
  }

  public void isCompletedExceptionallyWith(final Class<? extends Throwable> exceptionType) {
    isCompletedExceptionally();
    Assertions.assertThatThrownBy(actual::join)
        .isInstanceOf(CompletionException.class)
        .extracting(Throwable::getCause)
        .isInstanceOf(exceptionType);
  }
}
