/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.attestation.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

public class TimeLimitingIteratorTest {
  private final LongSupplier nanosSupplier = mock(LongSupplier.class);

  @SuppressWarnings("unchecked")
  private final Iterator<String> delegateIterator = mock(Iterator.class);

  private final LongConsumer onTimeLimitCallback = mock(LongConsumer.class);

  private static final long TIME_LIMIT_NANOS = 1000L;
  private final TimeLimitingIterator<String> timeLimitingIterator =
      new TimeLimitingIterator<>(
          nanosSupplier, TIME_LIMIT_NANOS, delegateIterator, onTimeLimitCallback);

  @Test
  void hasNext_shouldReturnTrueAndDelegateWhenWithinTimeLimitAndDelegateHasNext() {
    when(nanosSupplier.getAsLong()).thenReturn(TIME_LIMIT_NANOS - 1);
    when(delegateIterator.hasNext()).thenReturn(true);

    assertThat(timeLimitingIterator.hasNext()).isTrue();

    verify(delegateIterator).hasNext();
    verifyNoInteractions(onTimeLimitCallback);
  }

  @Test
  void hasNext_shouldReturnFalseAndInvokeCallbackWhenTimeLimitExceededAndDelegateHasNext() {
    when(nanosSupplier.getAsLong()).thenReturn(TIME_LIMIT_NANOS + 1);
    assertThat(timeLimitingIterator.hasNext()).isFalse();

    verify(onTimeLimitCallback).accept(TIME_LIMIT_NANOS);
    verify(delegateIterator, never()).hasNext();
  }

  @Test
  void next_shouldAlwaysDelegateToUnderlyingIterator() {
    final String expectedValue = "testValue";
    when(delegateIterator.next()).thenReturn(expectedValue);

    assertThat(timeLimitingIterator.next()).isEqualTo(expectedValue);

    verify(delegateIterator).next();
    verifyNoInteractions(nanosSupplier);
    verifyNoInteractions(onTimeLimitCallback);
  }
}
