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

class BalanceAttackMitigationForkChoiceTriggerTest {

  private final ForkChoice forkChoice = mock(ForkChoice.class);
  private final SafeFuture<Boolean> processHeadResult = new SafeFuture<>();
  private final ForkChoiceTrigger trigger = ForkChoiceTrigger.create(forkChoice, true);

  @BeforeEach
  void setUp() {
    when(forkChoice.processHead(any())).thenReturn(processHeadResult);
  }

  @Test
  void shouldNotifyBlockDueWhenAttestationsDue() {
    trigger.onAttestationsDueForSlot(UInt64.ONE);
    verify(forkChoice).onBlocksDueForSlot(UInt64.ONE);
    verifyNoMoreInteractions(forkChoice);
  }

  @Test
  void shouldProcessHeadForEndingSlotOnSlotStart() {
    trigger.onSlotStarted(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ZERO);
  }

  @Test
  void shouldProcessHeadForEndingSlotOnSlotStartAvoidingUnderflow() {
    trigger.onSlotStarted(UInt64.ZERO);
    verify(forkChoice).processHead(UInt64.ZERO);
  }

  @Test
  void shouldProcessHeadOnSlotWhileSyncing() {
    trigger.onSlotStartedWhileSyncing(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ZERO);
  }

  @Test
  void shouldProcessHeadOnSlotWhileSyncingAvoidingUnderflow() {
    trigger.onSlotStartedWhileSyncing(UInt64.ZERO);
    verify(forkChoice).processHead(UInt64.ZERO);
  }

  @Test
  void shouldNotRunForkChoiceAgainForTheSameSlot() {
    trigger.onSlotStartedWhileSyncing(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ZERO);

    trigger.onSlotStartedWhileSyncing(UInt64.ONE);
    verifyNoMoreInteractions(forkChoice);
  }

  @Test
  void shouldNotRunForkChoiceWhenSlotIsLessThanPreviousRun() {
    trigger.onSlotStartedWhileSyncing(UInt64.valueOf(3));
    verify(forkChoice).processHead(UInt64.valueOf(2));

    trigger.onSlotStartedWhileSyncing(UInt64.ONE);
    verifyNoMoreInteractions(forkChoice);
  }

  @Test
  void requireForkChoiceCompleteForSlot_shouldBeCompleteWhenLastForkChoiceForLaterSlot() {
    trigger.onSlotStartedWhileSyncing(UInt64.valueOf(3));

    final SafeFuture<Void> result = trigger.prepareForBlockProduction(UInt64.ONE);
    assertThat(result).isCompleted();
  }

  @Test
  void requireForkChoiceCompleteForSlot_shouldCompleteWhenCurrentRunCompletesIfSlotIsTheSame() {
    trigger.onSlotStartedWhileSyncing(UInt64.valueOf(2));
    verify(forkChoice).processHead(UInt64.ONE);

    final SafeFuture<Void> result = trigger.prepareForBlockProduction(UInt64.ONE);
    verifyNoMoreInteractions(forkChoice);
    assertThat(result).isNotDone();

    processHeadResult.complete(true);
    assertThat(result).isCompleted();
  }

  @Test
  void requiredForkChoiceCompleteForSlot_shouldRunForkChoiceWhenSlotIsGreaterThanLastRun() {
    trigger.onSlotStartedWhileSyncing(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ZERO);

    final SafeFuture<Void> result = trigger.prepareForBlockProduction(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ONE);
    assertThat(result).isNotDone();

    processHeadResult.complete(true);
    assertThat(result).isCompleted();
  }
}
