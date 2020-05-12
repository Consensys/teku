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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.storage.InMemoryStorageSystem;

public abstract class AbstractCombinedChainDataClientTest {

  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(2);

  protected InMemoryStorageSystem storageSystem;
  protected ChainUpdater chainUpdater;
  protected CombinedChainDataClient client;

  @BeforeEach
  public void setup() {
    storageSystem = createStorageSystem();
    chainUpdater =
        new ChainUpdater(storageSystem.recentChainData(), ChainBuilder.create(VALIDATOR_KEYS));
    client = storageSystem.combinedChainDataClient();
  }

  protected abstract InMemoryStorageSystem createStorageSystem();

  @Test
  public void getStateAtSlot_preGenesis() {
    assertThat(client.getLatestStateAtSlot(UnsignedLong.ZERO))
        .isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getStateAtSlot_genesis() {
    final SignedBlockAndState genesis = chainUpdater.initializeGenesis();
    assertThat(client.getLatestStateAtSlot(genesis.getSlot()))
        .isCompletedWithValue(Optional.of(genesis.getState()));
    assertThat(client.getLatestStateAtSlot(genesis.getSlot().plus(UnsignedLong.ONE)))
        .isCompletedWithValue(Optional.of(genesis.getState()));
  }

  @Test
  public void getStateAtSlot_shouldRetrieveLatestFinalizedState() {
    final UnsignedLong finalizedEpoch = UnsignedLong.valueOf(2);
    final UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    // Setup chain with finalized block
    chainUpdater.initializeGenesis();
    final SignedBlockAndState blockAtEpoch = chainUpdater.advanceChain(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainUpdater.finalizeEpoch(finalizedEpoch);
    chainUpdater.addNewBestBlock();

    // Sanity check
    assertThat(blockAtEpoch).isEqualTo(finalizedBlock);

    assertThat(client.getLatestStateAtSlot(finalizedSlot))
        .isCompletedWithValue(Optional.of(blockAtEpoch.getState()));
  }

  @Test
  public void getStateAtSlot_shouldRetrieveHeadState() {
    final UnsignedLong finalizedEpoch = UnsignedLong.valueOf(2);
    final UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    chainUpdater.initializeGenesis();
    chainUpdater.advanceChain(finalizedSlot);
    chainUpdater.finalizeEpoch(finalizedEpoch);
    final SignedBlockAndState bestBlock = chainUpdater.addNewBestBlock();

    assertThat(client.getLatestStateAtSlot(bestBlock.getSlot()))
        .isCompletedWithValue(Optional.of(bestBlock.getState()));
  }

  @Test
  public void getStateAtSlot_shouldRetrieveRecentState() {
    final UnsignedLong finalizedEpoch = UnsignedLong.valueOf(2);
    final UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    chainUpdater.initializeGenesis();
    chainUpdater.advanceChain(finalizedSlot);
    chainUpdater.finalizeEpoch(finalizedEpoch);
    final SignedBlockAndState recentBlock = chainUpdater.advanceChain();
    final SignedBlockAndState bestBlock = chainUpdater.addNewBestBlock();
    // Sanity check
    assertThat(recentBlock.getSlot()).isLessThan(bestBlock.getSlot());

    assertThat(client.getLatestStateAtSlot(recentBlock.getSlot()))
        .isCompletedWithValue(Optional.of(recentBlock.getState()));
  }

  @Test
  public void getStateAtSlot_shouldRetrieveRecentStateInEffectAtSkippedSlot() {
    final UnsignedLong finalizedEpoch = UnsignedLong.valueOf(2);
    final UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    chainUpdater.initializeGenesis();
    chainUpdater.advanceChain(finalizedSlot);
    chainUpdater.finalizeEpoch(finalizedEpoch);
    final SignedBlockAndState recentBlock = chainUpdater.advanceChain();
    final UnsignedLong skippedSlot = recentBlock.getSlot().plus(UnsignedLong.ONE);
    final SignedBlockAndState bestBlock =
        chainUpdater.advanceChain(skippedSlot.plus(UnsignedLong.ONE));
    chainUpdater.updateBestBlock(bestBlock);

    assertThat(client.getLatestStateAtSlot(skippedSlot))
        .isCompletedWithValue(Optional.of(recentBlock.getState()));
  }
}
