package tech.pegasys.artemis.util.future;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.util.future.FutureUtils.wrapInFuture;

import org.junit.jupiter.api.Test;

class FutureUtilsTest {
  @Test
  public void wrapInFutureShouldReturnResultAsCompletedFuture() {
    assertThat(wrapInFuture(() -> "Yay")).isCompletedWithValue("Yay");
  }
  
  @Test
  public void wrapInFutureShouldCompleteExceptionallyWhenExceptionThrown() {
    final IllegalStateException exception = new IllegalStateException("Nope");
    assertThat(wrapInFuture(() -> {
      throw exception;
    })).hasFailedWithThrowableThat().isSameAs(exception);
  }
}