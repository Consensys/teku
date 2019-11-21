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

package tech.pegasys.artemis.storage;

import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.util.alogger.ALogger;

/** This class is the ChainStorage client-side logic */
public class ChainStorageClient implements ChainStorage {

  protected EventBus eventBus;

  private volatile Store store;
  private volatile Bytes32 bestBlockRoot =
      Bytes32.ZERO; // block chosen by lmd ghost to build and attest on
  private volatile UnsignedLong bestSlot =
      UnsignedLong.ZERO; // slot of the block chosen by lmd ghost to build and attest on
  // Time
  private volatile UnsignedLong genesisTime;

  public ChainStorageClient(EventBus eventBus) {
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

  public void setGenesisTime(UnsignedLong genesisTime) {
    this.genesisTime = genesisTime;
  }

  public UnsignedLong getGenesisTime() {
    return genesisTime;
  }

  @Subscribe
  public void setStore(Store store) {
    this.store = store;
  }

  public Store getStore() {
    return store;
  }

  // NETWORKING RELATED INFORMATION METHODS:

  /**
   * Update Best Block
   *
   * @param root
   * @param slot
   */
  public void updateBestBlock(Bytes32 root, UnsignedLong slot) {
    this.bestBlockRoot = root;
    this.bestSlot = slot;
  }

  /**
   * Retrives the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public Bytes32 getBestBlockRoot() {
    return this.bestBlockRoot;
  }

  /**
   * Retrives the state of the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public BeaconState getBestBlockRootState() {
    return this.store.getBlockState(this.bestBlockRoot);
  }

  /**
   * Retrives the slot of the block chosen by fork choice to build and attest on
   *
   * @return
   */
  public UnsignedLong getBestSlot() {
    return this.bestSlot;
  }

  @Subscribe
  public void onNewUnprocessedBlock(BeaconBlock block) {
    STDOUT.log(
        Level.INFO,
        "New BeaconBlock with state root:  " + block.getState_root().toHexString() + " detected.",
        ALogger.Color.GREEN);
  }

  @Subscribe
  public void onNewUnprocessedAttestation(Attestation attestation) {
    STDOUT.log(
        Level.INFO,
        "New Attestation with block root:  "
            + attestation.getData().getBeacon_block_root()
            + " detected.",
        ALogger.Color.GREEN);
  }

  @Subscribe
  public void onNewAggregateAndProof(AggregateAndProof attestation) {
    STDOUT.log(
        Level.INFO,
        "New AggregateAndProof with block root:  "
            + attestation.getAggregate().getData().getBeacon_block_root()
            + " detected.",
        ALogger.Color.BLUE);
  }

  public Optional<BeaconBlock> getBlockBySlot(final UnsignedLong slot) {
    return getBlockRootBySlot(slot).map(blockRoot -> store.getBlock(blockRoot));
  }

  public boolean isIncludedInBestState(final Bytes32 blockRoot) {
    if (store == null) {
      return false;
    }
    final BeaconBlock block = store.getBlock(blockRoot);
    if (block == null) {
      return false;
    }
    return getBlockRootBySlot(block.getSlot())
        .map(actualRoot -> actualRoot.equals(block.hash_tree_root()))
        .orElse(false);
  }

  private Optional<Bytes32> getBlockRootBySlot(final UnsignedLong slot) {
    if (store == null || Bytes32.ZERO.equals(bestBlockRoot)) {
      return Optional.empty();
    }
    final UnsignedLong slotsPerHistoricalRoot =
        UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT);

    if (isSlotStillAvailable(slot, slotsPerHistoricalRoot)) {
      return Optional.empty();
    }
    final BeaconState bestState = store.getBlockState(bestBlockRoot);
    if (bestState == null) {
      return Optional.empty();
    }

    int historicalIndex = slot.mod(slotsPerHistoricalRoot).intValue();
    return Optional.ofNullable(bestState.getBlock_roots().get(historicalIndex));
  }

  private boolean isSlotStillAvailable(
      final UnsignedLong slot, final UnsignedLong slotsPerHistoricalRoot) {
    return bestSlot.compareTo(slotsPerHistoricalRoot) >= 0
        && bestSlot.minus(slotsPerHistoricalRoot).compareTo(slot) >= 0;
  }
}
