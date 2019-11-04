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
    assertThat(
            wrapInFuture(
                () -> {
                  throw exception;
                }))
        .hasFailedWithThrowableThat()
        .isSameAs(exception);
  }
}
