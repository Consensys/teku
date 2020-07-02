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

package tech.pegasys.teku.statetransition.forkchoice;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.storage.api.TrackingReorgEventChannel.ReorgEvent;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.util.config.StateStorageMode;

class ForkChoiceTest {

  private final StateTransition stateTransition = mock(StateTransition.class);
  private final StorageSystem storageSystem =
      InMemoryStorageSystem.createEmptyV3StorageSystem(StateStorageMode.PRUNE);
  private final ChainBuilder chainBuilder = storageSystem.chainBuilder();
  private final SignedBlockAndState genesis = chainBuilder.generateGenesis();
  private final RecentChainData recentChainData = storageSystem.recentChainData();

  private final ForkChoice forkChoice = new ForkChoice(recentChainData, stateTransition);

  @BeforeEach
  public void setup() {
    recentChainData.initializeFromGenesis(genesis.getState());
  }

  @Test
  void shouldTriggerReorgWhenEmptyHeadSlotFilled() {
    // Run fork choice with an empty slot 1
    forkChoice.processHead(Optional.of(ONE));

    // Then rerun with a filled slot 1
    final SignedBlockAndState slot1Block = storageSystem.chainUpdater().advanceChain(ONE);
    forkChoice.processHead(Optional.of(ONE));

    final List<ReorgEvent> reorgEvents = storageSystem.reorgEventChannel().getReorgEvents();
    assertThat(reorgEvents).hasSize(1);
    assertThat(reorgEvents.get(0).getBestSlot()).isEqualTo(ONE);
    assertThat(reorgEvents.get(0).getBestBlockRoot()).isEqualTo(slot1Block.getRoot());
  }
}
