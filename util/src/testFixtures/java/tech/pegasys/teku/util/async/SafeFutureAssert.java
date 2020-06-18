/*
 * Copyright 2020 ConsenSys AG.
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
