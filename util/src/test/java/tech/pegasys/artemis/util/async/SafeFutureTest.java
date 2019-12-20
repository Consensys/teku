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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SafeFutureTest {

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
    assertThat(safeFuture).isCompletedExceptionally();
    assertThatThrownBy(safeFuture::join)
        .isInstanceOf(CompletionException.class)
        .extracting(Throwable::getCause)
        .isSameAs(exception);
  }

  @Test
  void completedFuture_isCompletedWithValue() {
    assertThat(SafeFuture.completedFuture("Yay")).isCompletedWithValue("Yay");
  }

  @Test
  void failedFuture_isExceptionallyCompleted() {
    final RuntimeException exception = new RuntimeException("Oh no");
    final SafeFuture<String> safeFuture = SafeFuture.failedFuture(exception);

    assertThat(safeFuture).isCompletedExceptionally();
    assertThatThrownBy(safeFuture::join)
        .isInstanceOf(CompletionException.class)
        .extracting(Throwable::getCause)
        .isSameAs(exception);
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
}
