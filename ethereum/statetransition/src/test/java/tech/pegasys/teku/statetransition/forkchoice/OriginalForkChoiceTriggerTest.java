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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class OriginalForkChoiceTriggerTest {

  protected final ForkChoice forkChoice = mock(ForkChoice.class);
  private final ForkChoiceTrigger trigger = ForkChoiceTrigger.create(forkChoice, false);

  @BeforeEach
  void setUp() {
    when(forkChoice.processHead(any())).thenReturn(SafeFuture.completedFuture(true));
  }

  @Test
  void shouldProcessHeadOnSlotStartedWhileSyncing() {
    trigger.onSlotStartedWhileSyncing(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ONE);
  }

  @Test
  void shouldProcessHeadOnAttestationDue() {
    trigger.onAttestationsDueForSlot(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ONE);
  }

  @Test
  void shouldNotProcessHeadOnSlotStart() {
    trigger.onSlotStarted(UInt64.ONE);
    verifyNoInteractions(forkChoice);
  }

  @Test
  void shouldNotRunForkChoicePriorToBlockProduction() {
    assertThat(trigger.prepareForBlockProduction(UInt64.ONE)).isCompleted();
    verifyNoInteractions(forkChoice);
  }
}
