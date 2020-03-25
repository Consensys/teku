/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis.util.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.CountingNoOpAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class SafeFutureTest {

  @AfterEach
  public void tearDown() {
    // Reset logging to remove any additional appenders we added
    final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    ctx.reconfigure();
  }

  @Test
  void of_successfullyCompletedFuture() {
    final CompletableFuture<String> completableFuture = new CompletableFuture<>();
    final SafeFuture<String> safeFuture = SafeFuture.of(completableFuture);

    assertThat(safeFuture).isNotDone();

    completableFuture.complete("Yay");
    assertThat(safeFuture).isCompletedWithValue("Yay");
  }

  @Test
  void of_exceptionallyCompletedFuture() {
    final CompletableFuture<String> completableFuture = new CompletableFuture<>();
    final SafeFuture<String> safeFuture = SafeFuture.of(completableFuture);

    assertThat(safeFuture).isNotDone();

    final RuntimeException exception = new RuntimeException("Oh no");
    completableFuture.completeExceptionally(exception);
    assertExceptionallyCompletedWith(safeFuture, exception);
  }

  @Test
  public void ofWithSupplier_propagatesSuccessfulResult() {
    final SafeFuture<Void> suppliedFuture = new SafeFuture<>();
    final AtomicBoolean supplierWasProcessed = new AtomicBoolean(false);
    final SafeFuture<Void> future =
        SafeFuture.of(
            () -> {
              supplierWasProcessed.set(true);
              return suppliedFuture;
            });

    assertThat(supplierWasProcessed).isTrue();
    assertThat(future).isNotDone();
    suppliedFuture.complete(null);
    assertThat(future).isCompleted();
  }

  @Test
  public void ofWithSupplier_propagatesExceptionalResult() {
    final SafeFuture<Void> suppliedFuture = new SafeFuture<>();
    final AtomicBoolean supplierWasProcessed = new AtomicBoolean(false);
    final SafeFuture<Void> future =
        SafeFuture.of(
            () -> {
              supplierWasProcessed.set(true);
              return suppliedFuture;
            });

    assertThat(supplierWasProcessed).isTrue();
    assertThat(future).isNotDone();

    final RuntimeException error = new RuntimeException("failed");
    suppliedFuture.completeExceptionally(error);
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasRootCause(error);
  }

  @Test
  public void ofWithSupplier_propagatesExceptionFromSupplier() {
    final RuntimeException error = new RuntimeException("whoops");
    final Supplier<CompletionStage<Void>> futureSupplier =
        () -> {
          throw error;
        };

    final SafeFuture<Void> future = SafeFuture.of(futureSupplier);

    assertExceptionallyCompletedWith(future, error);
  }

  @Test
  public void ofWithExceptionThrowingSupplier_propagatesSuccessfulResult() {

    final ExceptionThrowingSupplier<String> supplier = () -> "Yay";

    final SafeFuture<String> future = SafeFuture.of(supplier);

    assertThat(future).isCompletedWithValue("Yay");
  }

  @Test
  public void ofWithExceptionThrowingSupplier_propagatesExceptionFromSupplier() {

    final IOException error = new IOException("whoops");
    final ExceptionThrowingSupplier<Void> supplier =
        () -> {
          throw error;
        };

    final SafeFuture<Void> future = SafeFuture.of(supplier);

    assertExceptionallyCompletedWith(future, error);
  }

  @Test
  public void ofComposedWithExceptionThrowingSupplier_propagatesSuccessfulResult() {
    final SafeFuture<Void> suppliedFuture = new SafeFuture<>();
    final AtomicBoolean supplierWasProcessed = new AtomicBoolean(false);
    final SafeFuture<Void> future =
        SafeFuture.ofComposed(
            () -> {
              supplierWasProcessed.set(true);
              return suppliedFuture;
            });

    assertThat(supplierWasProcessed).isTrue();
    assertThat(future).isNotDone();
    suppliedFuture.complete(null);
    assertThat(future).isCompleted();
  }

  @Test
  public void ofComposedWithExceptionThrowingSupplier_propagatesExceptionFromSupplier() {
    final AtomicBoolean supplierWasProcessed = new AtomicBoolean(false);
    final Throwable error = new IOException("failed");
    final SafeFuture<Void> future =
        SafeFuture.ofComposed(
            () -> {
              supplierWasProcessed.set(true);
              throw error;
            });

    assertThat(supplierWasProcessed).isTrue();
    assertExceptionallyCompletedWith(future, error);
  }

  @Test
  void completedFuture_isCompletedWithValue() {
    assertThat(SafeFuture.completedFuture("Yay")).isCompletedWithValue("Yay");
  }

  @Test
  void failedFuture_isExceptionallyCompleted() {
    final RuntimeException exception = new RuntimeException("Oh no");
    final SafeFuture<String> safeFuture = SafeFuture.failedFuture(exception);

    assertExceptionallyCompletedWith(safeFuture, exception);
  }

  @Test
  public void finish_executeRunnableOnSuccess() {
    final AtomicBoolean called = new AtomicBoolean(false);
    final SafeFuture<String> future = new SafeFuture<>();
    future.finish(() -> called.set(true));

    assertThat(called).isFalse();
    future.complete("Yay");
    assertThat(called).isTrue();
  }

  @Test
  public void finish_doNotExecuteRunnableOnException() {
    final AtomicBoolean called = new AtomicBoolean(false);
    final SafeFuture<String> future = new SafeFuture<>();
    future.finish(() -> called.set(true));

    assertThat(called).isFalse();
    future.completeExceptionally(new RuntimeException("Nope"));
    assertThat(called).isFalse();
  }

  @Test
  public void finish_executeConsumerOnSuccess() {
    final AtomicReference<String> called = new AtomicReference<>();
    final SafeFuture<String> future = new SafeFuture<>();
    future.finish(called::set);

    assertThat(called).hasValue(null);
    future.complete("Yay");
    assertThat(called).hasValue("Yay");
  }

  @Test
  public void finish_doNotExecuteConsumerOnException() {
    final AtomicReference<String> called = new AtomicReference<>();
    final SafeFuture<String> future = new SafeFuture<>();
    future.finish(called::set);

    assertThat(called).hasValue(null);
    future.completeExceptionally(new RuntimeException("Oh no"));
    assertThat(called).hasValue(null);
  }

  @Test
  public void finish_runnableAndErrorHandler_executeRunnableOnSuccess() {
    final AtomicBoolean called = new AtomicBoolean(false);
    final AtomicReference<Throwable> errorCallback = new AtomicReference<>();
    final SafeFuture<String> future = new SafeFuture<>();
    future.finish(() -> called.set(true), errorCallback::set);

    assertThat(called).isFalse();
    assertThat(errorCallback).hasValue(null);
    future.complete("Yay");
    assertThat(called).isTrue();
    assertThat(errorCallback).hasValue(null);
  }

  @Test
  public void finish_runnableAndErrorHandler_executeErrorHandlerOnException() {
    final AtomicBoolean called = new AtomicBoolean(false);
    final AtomicReference<Throwable> errorCallback = new AtomicReference<>();
    final SafeFuture<String> future = new SafeFuture<>();
    future.finish(() -> called.set(true), errorCallback::set);

    assertThat(called).isFalse();
    assertThat(errorCallback).hasValue(null);
    final RuntimeException exception = new RuntimeException("Oh no");
    future.completeExceptionally(exception);
    assertThat(called).isFalse();
    assertThat(errorCallback).hasValue(exception);
  }

  @Test
  public void finish_callableAndErrorHandler_executeCallableOnSuccess() {
    final AtomicReference<String> resultCallback = new AtomicReference<>();
    final AtomicReference<Throwable> errorCallback = new AtomicReference<>();
    final SafeFuture<String> future = new SafeFuture<>();
    future.finish(resultCallback::set, errorCallback::set);

    assertThat(resultCallback).hasValue(null);
    assertThat(errorCallback).hasValue(null);
    future.complete("Yay");
    assertThat(resultCallback).hasValue("Yay");
    assertThat(errorCallback).hasValue(null);
  }

  @Test
  public void finish_callableAndErrorHandler_executeErrorHandlerOnException() {
    final AtomicReference<String> resultCallback = new AtomicReference<>();
    final AtomicReference<Throwable> errorCallback = new AtomicReference<>();
    final SafeFuture<String> future = new SafeFuture<>();
    future.finish(resultCallback::set, errorCallback::set);

    assertThat(resultCallback).hasValue(null);
    assertThat(errorCallback).hasValue(null);
    final RuntimeException exception = new RuntimeException("Oh no");
    future.completeExceptionally(exception);
    assertThat(resultCallback).hasValue(null);
    assertThat(errorCallback).hasValue(exception);
  }

  @Test
  public void newIncompleteFuture_returnSafeFuture() {
    // The compiler makes this look like a tautology but it's really checking ClassCastException
    // isn't thrown during execution
    final SafeFuture<String> future1 = new SafeFuture<>();
    final SafeFuture<Object> future2 = future1.newIncompleteFuture();
    assertThat(future2).isInstanceOf(SafeFuture.class);

    final SafeFuture<Void> chainedFuture = future1.thenAccept(foo -> {});
    assertThat(chainedFuture).isInstanceOf(SafeFuture.class);
  }

  @Test
  public void finish_shouldNotLogHandledExceptions() {
    final CountingNoOpAppender logCounter = startCountingReportedUnhandledExceptions();

    final SafeFuture<Object> safeFuture = new SafeFuture<>();
    safeFuture.finish(() -> {}, onError -> {});
    safeFuture.completeExceptionally(new RuntimeException("Oh no!"));

    assertThat(logCounter.getCount()).isZero();
  }

  @Test
  public void finish_shouldReportExceptionsThrowBySuccessHandler() {
    final CountingNoOpAppender logCounter = startCountingReportedUnhandledExceptions();

    final SafeFuture<Object> safeFuture = new SafeFuture<>();
    safeFuture.finish(
        () -> {
          throw new RuntimeException("Oh no!");
        },
        onError -> {});
    safeFuture.complete(null);

    assertThat(logCounter.getCount()).isEqualTo(1);
  }

  @Test
  public void finish_shouldReportExceptionsThrowByErrorHandler() {
    final CountingNoOpAppender logCounter = startCountingReportedUnhandledExceptions();

    final SafeFuture<Object> safeFuture = new SafeFuture<>();
    safeFuture.finish(
        () -> {},
        onError -> {
          throw new RuntimeException("Oh no!");
        });
    safeFuture.completeExceptionally(new Exception("Original exception"));

    assertThat(logCounter.getCount()).isEqualTo(1);
  }

  @Test
  public void finish_shouldReportExceptionsWhenNoErrorHandlerProvided() {
    final CountingNoOpAppender logCounter = startCountingReportedUnhandledExceptions();

    final SafeFuture<Object> safeFuture = new SafeFuture<>();
    safeFuture.finish(() -> {});
    safeFuture.completeExceptionally(new RuntimeException("Not handled"));

    assertThat(logCounter.getCount()).isEqualTo(1);
  }

  @Test
  public void reportExceptions_shouldLogUnhandledExceptions() {
    final CountingNoOpAppender logCounter = startCountingReportedUnhandledExceptions();

    final SafeFuture<Object> safeFuture = new SafeFuture<>();
    safeFuture.reportExceptions();
    safeFuture.completeExceptionally(new RuntimeException("Oh no!"));

    assertThat(logCounter.getCount()).isEqualTo(1);
  }

  @Test
  public void exceptionallyCompose_shouldComposeInErrorCase() {
    final AtomicReference<Throwable> receivedError = new AtomicReference<>();
    final SafeFuture<String> safeFuture = new SafeFuture<>();
    final SafeFuture<String> composedFuture = new SafeFuture<>();
    final SafeFuture<String> result =
        safeFuture.exceptionallyCompose(
            error -> {
              receivedError.set(error);
              return composedFuture;
            });
    final RuntimeException exception = new RuntimeException("Nope");

    safeFuture.completeExceptionally(exception);
    assertThat(result).isNotDone();
    assertThat(receivedError).hasValue(exception);

    composedFuture.complete("Success");
    assertThat(result).isCompletedWithValue("Success");
  }

  @Test
  public void exceptionallyCompose_shouldCompleteExceptionallyWhenHandlerThrowsException() {
    final RuntimeException exception1 = new RuntimeException("Error1");
    final RuntimeException exception2 = new RuntimeException("Error2");
    final SafeFuture<String> safeFuture = new SafeFuture<>();
    final SafeFuture<String> result =
        safeFuture.exceptionallyCompose(
            error -> {
              throw exception2;
            });

    safeFuture.completeExceptionally(exception1);
    assertExceptionallyCompletedWith(result, exception2);
  }

  @Test
  public void exceptionallyCompose_shouldNotDoAnythingWhenCompletedSuccessfully() {
    final AtomicReference<Throwable> receivedError = new AtomicReference<>();
    final SafeFuture<String> safeFuture = new SafeFuture<>();
    final SafeFuture<String> composedFuture = new SafeFuture<>();
    final SafeFuture<String> result =
        safeFuture.exceptionallyCompose(
            error -> {
              receivedError.set(error);
              return composedFuture;
            });

    safeFuture.complete("Success");
    assertThat(result).isCompletedWithValue("Success");
    assertThat(receivedError).hasValue(null);

    composedFuture.complete("Composed");
    assertThat(result).isCompletedWithValue("Success");
  }

  @Test
  public void catchAndRethrow_shouldNotExecuteHandlerWhenCompletedSuccessfully() {
    final AtomicReference<Throwable> receivedError = new AtomicReference<>();
    final SafeFuture<String> safeFuture = new SafeFuture<>();

    final SafeFuture<String> result = safeFuture.catchAndRethrow(receivedError::set);

    safeFuture.complete("Yay");
    assertThat(result).isCompletedWithValue("Yay");
    assertThat(receivedError).hasValue(null);
  }

  @Test
  public void
      catchAndRethrow_shouldExecuteHandlerWhenCompletedSuccessfullyAndCompleteExceptionally() {
    final AtomicReference<Throwable> receivedError = new AtomicReference<>();
    final SafeFuture<String> safeFuture = new SafeFuture<>();

    final SafeFuture<String> result = safeFuture.catchAndRethrow(receivedError::set);

    final RuntimeException exception = new RuntimeException("Nope");
    safeFuture.completeExceptionally(exception);
    assertExceptionallyCompletedWith(result, exception);
    assertThat(receivedError).hasValue(exception);
  }

  @Test
  public void propagateTo_propagatesSuccessfulResult() {
    final SafeFuture<String> target = new SafeFuture<>();
    final SafeFuture<String> source = new SafeFuture<>();
    source.propagateTo(target);
    source.complete("Yay");
    assertThat(target).isCompletedWithValue("Yay");
  }

  @Test
  public void propagateTo_propagatesExceptionalResult() {
    final SafeFuture<String> target = new SafeFuture<>();
    final SafeFuture<String> source = new SafeFuture<>();
    source.propagateTo(target);
    final RuntimeException exception = new RuntimeException("Oh no!");
    source.completeExceptionally(exception);
    assertExceptionallyCompletedWith(target, exception);
  }

  @Test
  public void fromRunnable_propagatesSuccessfulResult() {
    final AtomicBoolean runnableWasProcessed = new AtomicBoolean(false);
    final SafeFuture<Void> future = SafeFuture.fromRunnable(() -> runnableWasProcessed.set(true));

    assertThat(runnableWasProcessed).isTrue();
    assertThat(future.isDone()).isTrue();
  }

  @Test
  public void fromRunnable_propagatesExceptionalResult() {
    final RuntimeException error = new RuntimeException("whoops");
    final SafeFuture<Void> future =
        SafeFuture.fromRunnable(
            () -> {
              throw error;
            });

    assertThat(future).isCompletedExceptionally();
    assertExceptionallyCompletedWith(future, error);
  }

  @Test
  public void allOfFailFast_failImmediatelyWhenAnyFutureFails() {
    final SafeFuture<Void> future1 = new SafeFuture<>();
    final SafeFuture<Void> future2 = new SafeFuture<>();
    final SafeFuture<Void> result = SafeFuture.allOfFailFast(future1, future2);
    assertThat(result).isNotDone();

    final RuntimeException error = new RuntimeException("Nope");
    future1.completeExceptionally(error);
    assertExceptionallyCompletedWith(result, error);
  }

  @Test
  public void allOfFailFast_failImmediatelyWhenSomeFuturesCompleteAndOneFails() {
    final SafeFuture<Void> future1 = new SafeFuture<>();
    final SafeFuture<Void> future2 = new SafeFuture<>();
    final SafeFuture<Void> future3 = new SafeFuture<>();
    final SafeFuture<Void> result = SafeFuture.allOfFailFast(future1, future2, future3);
    assertThat(result).isNotDone();

    future2.complete(null);
    assertThat(result).isNotDone();

    final RuntimeException error = new RuntimeException("Nope");
    future3.completeExceptionally(error);
    assertExceptionallyCompletedWith(result, error);
  }

  @Test
  public void allOfFailFast_completesWhenAllFuturesComplete() {
    final SafeFuture<Void> future1 = new SafeFuture<>();
    final SafeFuture<Void> future2 = new SafeFuture<>();
    final SafeFuture<Void> result = SafeFuture.allOfFailFast(future1, future2);
    assertThat(result).isNotDone();

    future1.complete(null);
    assertThat(result).isNotDone();

    future2.complete(null);
    assertThat(result).isCompleted();
  }

  private CountingNoOpAppender startCountingReportedUnhandledExceptions() {
    final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    final Configuration config = ctx.getConfiguration();
    final CountingNoOpAppender appender =
        new CountingNoOpAppender("Mock", PatternLayout.newBuilder().build());
    appender.start();
    config.addAppender(appender);
    AppenderRef ref = AppenderRef.createAppenderRef("Mock", null, null);
    AppenderRef[] refs = new AppenderRef[] {ref};
    LoggerConfig loggerConfig =
        LoggerConfig.createLogger(false, Level.ERROR, "Mock", null, refs, null, config, null);
    loggerConfig.addAppender(appender, null, null);
    config.addLogger(SafeFuture.class.getName(), loggerConfig);
    ctx.updateLoggers();
    return appender;
  }

  static void assertExceptionallyCompletedWith(
      final SafeFuture<?> safeFuture, final Throwable exception) {
    assertThat(safeFuture).isCompletedExceptionally();
    assertThatThrownBy(safeFuture::join)
        .isInstanceOf(CompletionException.class)
        .extracting(Throwable::getCause)
        .isSameAs(exception);
  }
}
