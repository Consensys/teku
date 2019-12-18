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
import static tech.pegasys.artemis.util.async.FutureUtil.asyncFinally;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class FutureUtilTest {
  @Test
  public void asyncFinally_success() {
    final AtomicBoolean finallyCalled = new AtomicBoolean(false);
    final CompletableFuture<Object> input = new CompletableFuture<>();
    final CompletableFuture<Object> result = asyncFinally(input, () -> finallyCalled.set(true));

    assertThat(result).isNotDone();

    input.complete("Foo");
    assertThat(result).isCompletedWithValue("Foo");
    assertThat(finallyCalled).isTrue();
  }

  @Test
  public void asyncFinally_runtimeException() {
    final AtomicBoolean finallyCalled = new AtomicBoolean(false);
    final CompletableFuture<Object> input = new CompletableFuture<>();
    final CompletableFuture<Object> result = asyncFinally(input, () -> finallyCalled.set(true));

    assertThat(result).isNotDone();

    final RuntimeException exception = new RuntimeException("Foo");
    input.completeExceptionally(exception);
    assertExceptionallyCompletedWith(result, exception);
    assertThat(finallyCalled).isTrue();
  }

  @Test
  public void asyncFinally_checkedException() {
    final AtomicBoolean finallyCalled = new AtomicBoolean(false);
    final CompletableFuture<Object> input = new CompletableFuture<>();
    final CompletableFuture<Object> result = asyncFinally(input, () -> finallyCalled.set(true));

    assertThat(result).isNotDone();

    final IOException exception = new IOException("Foo");
    input.completeExceptionally(exception);
    assertExceptionallyCompletedWith(result, exception);
    assertThat(finallyCalled).isTrue();
  }

  @Test
  public void asyncFinally_error() {
    final AtomicBoolean finallyCalled = new AtomicBoolean(false);
    final CompletableFuture<Object> input = new CompletableFuture<>();
    final CompletableFuture<Object> result = asyncFinally(input, () -> finallyCalled.set(true));

    assertThat(result).isNotDone();

    final Error exception = new OutOfMemoryError();
    input.completeExceptionally(exception);
    assertExceptionallyCompletedWith(result, exception);
    assertThat(finallyCalled).isTrue();
  }

  private void assertExceptionallyCompletedWith(
      final CompletableFuture<?> future, final Throwable t) {
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::join)
        .isInstanceOf(CompletionException.class)
        .extracting(Throwable::getCause)
        .isSameAs(t);
  }
}
