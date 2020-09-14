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

package tech.pegasys.teku.infrastructure.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture.Interruptor;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;

public class SafeFutureTest {

  private static class TestAsyncExec {
    SafeFuture<Integer> fut = new SafeFuture<>();
    boolean executed = false;

    SafeFuture<Integer> exec() {
      executed = true;
      return fut;
    }
  }

  private static class InterruptTest {
    SafeFuture<Integer> interruptorFut1 = new SafeFuture<>();
    SafeFuture<Integer> interruptorFut2 = new SafeFuture<>();
    Interruptor interruptor1 =
        SafeFuture.createInterruptor(interruptorFut1, IllegalStateException::new);
    Interruptor interruptor2 =
        SafeFuture.createInterruptor(interruptorFut2, IllegalArgumentException::new);
    SafeFuture<Integer> fut0 = new SafeFuture<>();
    TestAsyncExec exec1 = new TestAsyncExec();
    TestAsyncExec exec2 = new TestAsyncExec();

    SafeFuture<Integer> intFut =
        fut0.orInterrupt(interruptor1, interruptor2)
            .thenCompose(__ -> exec1.exec())
            .orInterrupt(interruptor1, interruptor2)
            .thenCompose(__ -> exec2.exec())
            .orInterrupt(interruptor1, interruptor2);

    public InterruptTest() {
      assertThat(hasDependents(interruptorFut1)).isTrue();
      assertThat(hasDependents(interruptorFut2)).isTrue();
      assertThat(hasDependents(fut0)).isTrue();
    }

    public void assertReleased() {
      assertThat(hasDependents(interruptorFut1)).isFalse();
      assertThat(hasDependents(interruptorFut2)).isFalse();
      assertThat(hasDependents(fut0)).isFalse();
    }
  }

  @AfterEach
  public void tearDown() {
    // Reset the thread uncaught exception handler
    Thread.currentThread().setUncaughtExceptionHandler(null);
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
    assertThatSafeFuture(safeFuture).isCompletedExceptionallyWith(exception);
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
    final ExceptionThrowingFutureSupplier<Void> futureSupplier =
        () -> {
          throw error;
        };

    final SafeFuture<Void> future = SafeFuture.of(futureSupplier);

    assertThatSafeFuture(future).isCompletedExceptionallyWith(error);
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

    assertThatSafeFuture(future).isCompletedExceptionallyWith(error);
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
    assertThatSafeFuture(future).isCompletedExceptionallyWith(error);
  }

  @Test
  void completedFuture_isCompletedWithValue() {
    assertThat(SafeFuture.completedFuture("Yay")).isCompletedWithValue("Yay");
  }

  @Test
  void failedFuture_isExceptionallyCompleted() {
    final RuntimeException exception = new RuntimeException("Oh no");
    final SafeFuture<String> safeFuture = SafeFuture.failedFuture(exception);

    assertThatSafeFuture(safeFuture).isCompletedExceptionallyWith(exception);
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
    final List<Throwable> caughtExceptions = collectUncaughtExceptions();

    final SafeFuture<Object> safeFuture = new SafeFuture<>();
    safeFuture.finish(() -> {}, onError -> {});
    safeFuture.completeExceptionally(new RuntimeException("Oh no!"));

    assertThat(caughtExceptions).isEmpty();
  }

  @Test
  public void finish_shouldReportExceptionsThrowBySuccessHandler() {
    final List<Throwable> caughtExceptions = collectUncaughtExceptions();

    final SafeFuture<Object> safeFuture = new SafeFuture<>();
    safeFuture.finish(
        () -> {
          throw new RuntimeException("Oh no!");
        },
        onError -> {});
    safeFuture.complete(null);

    assertThat(caughtExceptions).hasSize(1);
  }

  @Test
  public void finish_shouldReportExceptionsThrowByErrorHandler() {
    final List<Throwable> caughtExceptions = collectUncaughtExceptions();

    final SafeFuture<Object> safeFuture = new SafeFuture<>();
    safeFuture.finish(
        () -> {},
        onError -> {
          throw new RuntimeException("Oh no!");
        });
    safeFuture.completeExceptionally(new Exception("Original exception"));

    assertThat(caughtExceptions).hasSize(1);
  }

  @Test
  public void finishAsync_shouldNotLogHandledExceptions() {
    final InlineEventThread eventThread = new InlineEventThread();
    final List<Throwable> caughtExceptions = collectUncaughtExceptions();

    final SafeFuture<Object> safeFuture = new SafeFuture<>();
    safeFuture.finishAsync(
        __ -> fail("Should not have called success handler"),
        onError -> eventThread.checkOnEventThread(),
        eventThread);
    safeFuture.completeExceptionally(new RuntimeException("Oh no!"));

    assertThat(caughtExceptions).isEmpty();
  }

  @Test
  public void finishAsync_shouldReportExceptionsThrowBySuccessHandler() {
    final InlineEventThread eventThread = new InlineEventThread();
    final List<Throwable> caughtExceptions = collectUncaughtExceptions();

    final RuntimeException exception = new RuntimeException("Oh no!");
    final SafeFuture<Object> safeFuture = new SafeFuture<>();
    safeFuture.finishAsync(
        __ -> {
          eventThread.checkOnEventThread();
          throw exception;
        },
        onError -> fail("Should not have invoked error handler"),
        eventThread);
    safeFuture.complete(null);

    assertThat(caughtExceptions).hasSize(1);
    assertThat(caughtExceptions.get(0)).hasRootCause(exception);
  }

  @Test
  public void finishAsync_shouldReportExceptionsThrowByErrorHandler() {
    final InlineEventThread eventThread = new InlineEventThread();
    final List<Throwable> caughtExceptions = collectUncaughtExceptions();

    final RuntimeException exception = new RuntimeException("Oh no!");
    final SafeFuture<Object> safeFuture = new SafeFuture<>();
    safeFuture.finishAsync(
        __ -> fail("Should not have invoked success handler"),
        onError -> {
          throw exception;
        },
        eventThread);
    safeFuture.completeExceptionally(new Exception("Original exception"));

    assertThat(caughtExceptions).hasSize(1);
    assertThat(caughtExceptions.get(0)).hasRootCause(exception);
  }

  @Test
  public void reportExceptions_shouldLogUnhandledExceptions() {
    final List<Throwable> caughtExceptions = collectUncaughtExceptions();

    final SafeFuture<Object> safeFuture = new SafeFuture<>();
    safeFuture.reportExceptions();
    safeFuture.completeExceptionally(new RuntimeException("Oh no!"));

    assertThat(caughtExceptions).hasSize(1);
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
    assertThatSafeFuture(result).isCompletedExceptionallyWith(exception2);
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
    assertThatSafeFuture(result).isCompletedExceptionallyWith(exception);
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
    assertThatSafeFuture(target).isCompletedExceptionallyWith(exception);
  }

  @Test
  public void propagateToAsync_propagatesSuccessfulResult() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    final SafeFuture<String> target = new SafeFuture<>();
    final SafeFuture<String> source = new SafeFuture<>();
    source.propagateToAsync(target, asyncRunner);
    source.complete("Yay");
    assertThat(target).isNotDone();

    asyncRunner.executeQueuedActions();
    assertThat(target).isCompletedWithValue("Yay");
  }

  @Test
  public void propagateToAsync_propagatesExceptionalResult() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    final SafeFuture<String> target = new SafeFuture<>();
    final SafeFuture<String> source = new SafeFuture<>();
    source.propagateToAsync(target, asyncRunner);
    final RuntimeException exception = new RuntimeException("Oh no!");
    source.completeExceptionally(exception);
    assertThat(target).isNotDone();

    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(target).isCompletedExceptionallyWith(exception);
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
    final Exception error = new Exception("whoops");
    final SafeFuture<Void> future =
        SafeFuture.fromRunnable(
            () -> {
              throw error;
            });

    assertThat(future).isCompletedExceptionally();
    assertThatSafeFuture(future).isCompletedExceptionallyWith(error);
  }

  @Test
  public void allOf_shouldAddAllSuppressedExceptions() {
    final Throwable error1 = new RuntimeException("Nope");
    final Throwable error2 = new RuntimeException("Oh dear");
    final SafeFuture<Void> future1 = new SafeFuture<>();
    final SafeFuture<Void> future2 = new SafeFuture<>();
    final SafeFuture<Void> future3 = new SafeFuture<>();

    final SafeFuture<Void> result = SafeFuture.allOf(future1, future2, future3);
    assertThat(result).isNotDone();

    future2.completeExceptionally(error2);
    assertThat(result).isNotDone();

    future3.complete(null);
    assertThat(result).isNotDone();

    future1.completeExceptionally(error1);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasSuppressedException(error2);
    assertThatThrownBy(result::join).hasRootCause(error1);
  }

  @Test
  public void allof_shouldCompleteWhenAllFuturesComplete() {
    final SafeFuture<Void> future1 = new SafeFuture<>();
    final SafeFuture<Void> future2 = new SafeFuture<>();

    final SafeFuture<Void> result = SafeFuture.allOf(future1, future2);
    assertThat(result).isNotDone();

    future1.complete(null);
    assertThat(result).isNotDone();

    future2.complete(null);
    assertThat(result).isCompletedWithValue(null);
  }

  @Test
  public void allOfFailFast_failImmediatelyWhenAnyFutureFails() {
    final SafeFuture<Void> future1 = new SafeFuture<>();
    final SafeFuture<Void> future2 = new SafeFuture<>();
    final SafeFuture<Void> result = SafeFuture.allOfFailFast(future1, future2);
    assertThat(result).isNotDone();

    final RuntimeException error = new RuntimeException("Nope");
    future1.completeExceptionally(error);
    assertThatSafeFuture(result).isCompletedExceptionallyWith(error);
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
    assertThatSafeFuture(result).isCompletedExceptionallyWith(error);
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

  private static boolean hasDependents(CompletableFuture<?> fut) {
    return fut.getNumberOfDependents() > 0;
  }

  @Test
  public void orTest1() throws Exception {
    SafeFuture<Integer> fut0 = new SafeFuture<>();
    SafeFuture<Integer> fut1 = new SafeFuture<>();

    assertThat(hasDependents(fut0)).isFalse();

    SafeFuture<Integer> orFut = fut0.or(fut1);

    assertThat(hasDependents(fut0)).isTrue();
    assertThat(hasDependents(fut1)).isTrue();

    fut1.complete(12);

    assertThat(hasDependents(fut0)).isFalse();
    assertThat(hasDependents(fut1)).isFalse();
    assertThat(orFut.get()).isEqualTo(12);
  }

  @Test
  public void or_completeNormally() throws Exception {
    SafeFuture<Integer> fut0 = new SafeFuture<>();
    SafeFuture<Integer> fut1 = new SafeFuture<>();

    assertThat(hasDependents(fut0)).isFalse();

    SafeFuture<Integer> orFut = fut0.or(fut1);

    assertThat(hasDependents(fut0)).isTrue();
    assertThat(hasDependents(fut1)).isTrue();

    fut0.complete(12);

    assertThat(hasDependents(fut0)).isFalse();
    assertThat(hasDependents(fut1)).isFalse();
    assertThat(orFut.get()).isEqualTo(12);
  }

  @Test
  public void or_completeExceptionally() {
    SafeFuture<Integer> fut0 = new SafeFuture<>();
    SafeFuture<Integer> fut1 = new SafeFuture<>();

    assertThat(hasDependents(fut0)).isFalse();

    SafeFuture<Integer> orFut = fut0.or(fut1);

    assertThat(hasDependents(fut0)).isTrue();
    assertThat(hasDependents(fut1)).isTrue();

    fut1.completeExceptionally(new IllegalStateException());

    assertThat(hasDependents(fut0)).isFalse();
    assertThat(hasDependents(fut1)).isFalse();
    assertThat(orFut).isCompletedExceptionally();
  }

  @Test
  public void orInterrupt_simpleCompleteWithoutInterruption() throws Exception {
    SafeFuture<Integer> interruptorFut = new SafeFuture<>();
    Interruptor interruptor =
        SafeFuture.createInterruptor(interruptorFut, IllegalStateException::new);
    SafeFuture<Integer> fut0 = new SafeFuture<>();

    assertThat(hasDependents(interruptorFut)).isFalse();
    assertThat(hasDependents(fut0)).isFalse();

    SafeFuture<Integer> intFut = fut0.orInterrupt(interruptor);

    assertThat(hasDependents(interruptorFut)).isTrue();
    assertThat(hasDependents(fut0)).isTrue();

    fut0.complete(12);

    assertThat(hasDependents(interruptorFut)).isFalse();
    assertThat(hasDependents(fut0)).isFalse();
    assertThat(intFut.get()).isEqualTo(12);
  }

  @Test
  public void orInterrupt_triggerOneOfTwoInterruptors() {
    SafeFuture<Integer> interruptorFut1 = new SafeFuture<>();
    SafeFuture<Integer> interruptorFut2 = new SafeFuture<>();
    Interruptor interruptor1 =
        SafeFuture.createInterruptor(interruptorFut1, IllegalStateException::new);
    Interruptor interruptor2 =
        SafeFuture.createInterruptor(interruptorFut2, IllegalStateException::new);
    SafeFuture<Integer> fut0 = new SafeFuture<>();

    assertThat(hasDependents(interruptorFut1)).isFalse();
    assertThat(hasDependents(interruptorFut2)).isFalse();
    assertThat(hasDependents(fut0)).isFalse();

    SafeFuture<Integer> intFut = fut0.orInterrupt(interruptor1, interruptor2);

    assertThat(hasDependents(interruptorFut1)).isTrue();
    assertThat(hasDependents(interruptorFut2)).isTrue();
    assertThat(hasDependents(fut0)).isTrue();

    interruptorFut2.complete(0);

    assertThat(hasDependents(interruptorFut1)).isFalse();
    assertThat(hasDependents(interruptorFut2)).isFalse();
    assertThat(hasDependents(fut0)).isFalse();
    assertThat(intFut).isCompletedExceptionally();
  }

  @Test
  public void orInterrupt_complexAllCompleteThenInterrupt() {
    InterruptTest test = new InterruptTest();

    test.fut0.complete(111);
    test.exec1.fut.complete(111);
    test.exec2.fut.complete(111);

    test.interruptorFut1.complete(0);

    test.assertReleased();
    assertThat(test.intFut).isCompletedWithValue(111);
    assertThat(test.exec1.executed).isTrue();
    assertThat(test.exec2.executed).isTrue();
  }

  @Test
  public void orInterrupt_complexInterruptImmediately() throws Exception {
    InterruptTest test = new InterruptTest();

    test.interruptorFut2.complete(0);

    test.assertReleased();
    assertThat(test.intFut).isCompletedExceptionally();
    assertThatThrownBy(() -> test.intFut.get()).hasRootCause(new IllegalArgumentException());
    assertThat(test.exec1.executed).isFalse();
    assertThat(test.exec2.executed).isFalse();
  }

  @Test
  public void orInterrupt_complexInterruptInTheMiddle() {
    InterruptTest test = new InterruptTest();

    test.fut0.complete(111);
    test.interruptorFut1.complete(0);

    test.assertReleased();
    assertThat(test.intFut).isCompletedExceptionally();
    assertThatThrownBy(() -> test.intFut.get()).hasRootCause(new IllegalStateException());
    assertThat(test.exec1.executed).isTrue();
    assertThat(test.exec2.executed).isFalse();
  }

  @Test
  public void notInterrupted_shouldThrowIfInterrupted() {
    SafeFuture<Void> interruptorFut = new SafeFuture<>();
    Interruptor interruptor =
        SafeFuture.createInterruptor(interruptorFut, () -> new RuntimeException("test"));
    interruptorFut.complete(null);
    SafeFuture<String> future = SafeFuture.notInterrupted(interruptor).thenApply(__ -> "aaa");

    assertThatThrownBy(future::get).hasMessageContaining("test");
  }

  @Test
  void alwaysRun_shouldRunWhenFutureCompletesSuccessfully() {
    final Runnable action = mock(Runnable.class);
    SafeFuture<String> source = new SafeFuture<>();

    final SafeFuture<String> result = source.alwaysRun(action);

    verifyNoInteractions(action);

    source.complete("Yay");
    verify(action).run();
    assertThat(result).isCompletedWithValue("Yay");
  }

  @Test
  void alwaysRun_shouldRunWhenFutureCompletesExceptionally() {
    final Runnable action = mock(Runnable.class);
    SafeFuture<String> source = new SafeFuture<>();

    final SafeFuture<String> result = source.alwaysRun(action);

    verifyNoInteractions(action);

    final RuntimeException exception = new RuntimeException("Sigh");
    source.completeExceptionally(exception);
    verify(action).run();
    assertThatSafeFuture(result).isCompletedExceptionallyWith(exception);
  }

  @Test
  void alwaysRun_shouldReturnFailedFutureWhenRunnableThrows() {
    final Runnable action = mock(Runnable.class);
    final RuntimeException exception = new RuntimeException("Oops");
    doThrow(exception).when(action).run();
    SafeFuture<String> source = new SafeFuture<>();

    final SafeFuture<String> result = source.alwaysRun(action);

    source.complete("Yay");
    verify(action).run();
    assertThatSafeFuture(result).isCompletedExceptionallyWith(exception);
  }

  @Test
  void handleComposed_shouldComposeTheNewFuture() {
    SafeFuture<String> source = new SafeFuture<>();
    SafeFuture<String> result =
        source.handleComposed(
            (string, err) -> {
              if (err != null) {
                throw new IllegalStateException();
              }
              return SafeFuture.completedFuture(string);
            });

    source.complete("yo");
    assertThat(result).isCompleted();
    assertThat(result).isCompletedWithValue("yo");
  }

  @Test
  void handleComposed_shouldPassTheErrorToTheNextFunction() {
    SafeFuture<String> source = new SafeFuture<>();
    SafeFuture<String> result =
        source.handleComposed(
            (string, err) -> {
              if (err != null) {
                return SafeFuture.completedFuture("yo");
              }
              return SafeFuture.completedFuture(string);
            });

    source.completeExceptionally(new UnsupportedOperationException());
    assertThat(result).isCompleted();
    assertThat(result).isCompletedWithValue("yo");
  }

  private List<Throwable> collectUncaughtExceptions() {
    final List<Throwable> caughtExceptions = new ArrayList<>();
    Thread.currentThread().setUncaughtExceptionHandler((t, e) -> caughtExceptions.add(e));
    return caughtExceptions;
  }
}
