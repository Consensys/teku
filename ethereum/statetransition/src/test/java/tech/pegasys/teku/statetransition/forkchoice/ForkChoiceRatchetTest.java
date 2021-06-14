/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class ForkChoiceRatchetTest {

  private final ForkChoice forkChoice = mock(ForkChoice.class);
  private final SafeFuture<Boolean> processHeadResult = new SafeFuture<>();
  private final ForkChoiceRatchet ratchet = new ForkChoiceRatchet(forkChoice);

  @BeforeEach
  void setUp() {
    when(forkChoice.processHead(any())).thenReturn(processHeadResult);
  }

  @Test
  void shouldNotRunForkChoiceAgainForTheSameSlot() {
    ratchet.scheduleForkChoiceForSlot(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ONE);

    ratchet.scheduleForkChoiceForSlot(UInt64.ONE);
    verifyNoMoreInteractions(forkChoice);
  }

  @Test
  void shouldNotRunForkChoiceWhenSlotIsLessThanPreviousRun() {
    ratchet.scheduleForkChoiceForSlot(UInt64.valueOf(2));
    verify(forkChoice).processHead(UInt64.valueOf(2));

    ratchet.scheduleForkChoiceForSlot(UInt64.ONE);
    verifyNoMoreInteractions(forkChoice);
  }

  @Test
  void ensureForkChoiceCompleteForSlot_shouldBeCompleteWhenLastForkChoiceForLaterSlot() {
    ratchet.scheduleForkChoiceForSlot(UInt64.valueOf(3));

    final SafeFuture<Void> result = ratchet.ensureForkChoiceCompleteForSlot(UInt64.ONE);
    assertThat(result).isCompleted();
  }

  @Test
  void ensureForkChoiceCompleteForSlot_shouldCompleteWhenCurrentRunCompletesIfSlotIsTheSame() {
    ratchet.scheduleForkChoiceForSlot(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ONE);

    final SafeFuture<Void> result = ratchet.ensureForkChoiceCompleteForSlot(UInt64.ONE);
    verifyNoMoreInteractions(forkChoice);
    assertThat(result).isNotDone();

    processHeadResult.complete(true);
    assertThat(result).isCompleted();
  }

  @Test
  void ensureForkChoiceCompleteForSlot_shouldRunForkChoiceWhenSlotIsGreaterThanLastRun() {
    ratchet.scheduleForkChoiceForSlot(UInt64.ZERO);
    verify(forkChoice).processHead(UInt64.ZERO);

    final SafeFuture<Void> result = ratchet.ensureForkChoiceCompleteForSlot(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ONE);
    assertThat(result).isNotDone();

    processHeadResult.complete(true);
    assertThat(result).isCompleted();
  }

  @Test
  void ensureForkChoiceCompleteForSlot_shouldNotFailWhenForkChoiceFails() {
    // Don't make block or attestation fail if fork choice fails, just go with the fork we're on
    final SafeFuture<Void> result = ratchet.ensureForkChoiceCompleteForSlot(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ONE);
    assertThat(result).isNotDone();

    processHeadResult.completeExceptionally(new RuntimeException("Ka-boom!"));
    assertThat(result).isCompleted();
  }
}
