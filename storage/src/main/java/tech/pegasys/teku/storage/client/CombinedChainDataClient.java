/*
 * Copyright Consensys Software Inc., 2022
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verifyNotNull;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.PayloadTimelinessProvider.PayloadStatusAndBeaconBlockRoot;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class CombinedChainDataClient {
  private static final Logger LOG = LogManager.getLogger();

  private static final SafeFuture<Optional<BeaconState>> STATE_NOT_AVAILABLE =
      completedFuture(Optional.empty());
  private static final SafeFuture<Optional<SignedBeaconBlock>> BLOCK_NOT_AVAILABLE =
      completedFuture(Optional.empty());
  private static final SafeFuture<Optional<SignedExecutionPayloadEnvelope>>
      EXECUTION_PAYLOAD_NOT_AVAILABLE = completedFuture(Optional.empty());

  private final RecentChainData recentChainData;
  private final StorageQueryChannel historicalChainData;
  private final Spec spec;

  private final EarliestAvailableBlockSlot earliestAvailableBlockSlot;

  public CombinedChainDataClient(
      final RecentChainData recentChainData,
      final StorageQueryChannel historicalChainData,
      final Spec spec,
      final EarliestAvailableBlockSlot earliestAvailableBlockSlot) {
    this.recentChainData = recentChainData;
    this.historicalChainData = historicalChainData;
    this.spec = spec;
    this.earliestAvailableBlockSlot = earliestAvailableBlockSlot;
  }

  /**
   * Returns the block proposed at the requested slot. If the slot is empty, no block is returned.
   *
   * @param slot the slot to get the block for
   * @return the block at the requested slot or empty if the slot was empty
   */
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockAtSlotExact(final UInt64 slot) {
    if (!isChainDataFullyAvailable()) {
      return BLOCK_NOT_AVAILABLE;
    }

    // Try to pull root from recent data
    final Optional<Bytes32> recentRoot = recentChainData.getBlockRootInEffectBySlot(slot);
    if (recentRoot.isPresent()) {
      return getBlockByBlockRoot(recentRoot.get())
          .thenApply(maybeBlock -> maybeBlock.filter(block -> block.getSlot().equals(slot)));
    }

    return historicalChainData.getFinalizedBlockAtSlot(slot);
  }

  /**
   * Returns the block proposed for the requested slot on the chain identified by <code>
   * headBlockRoot</code>. If the slot was empty, no block is returned.
   *
   * @param slot the slot to get the block for
   * @param headBlockRoot the block root of the head of the chain
   * @return the block at the requested slot or empty if the slot was empty
   */
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockAtSlotExact(
      final UInt64 slot, final Bytes32 headBlockRoot) {
    if (!isStoreAvailable()) {
      return BLOCK_NOT_AVAILABLE;
    }

    // Try to pull root from recent data
    final Optional<Bytes32> recentRoot =
        recentChainData.getBlockRootInEffectBySlot(slot, headBlockRoot);
    if (recentRoot.isPresent()) {
      return getBlockByBlockRoot(recentRoot.get())
          .thenApply(maybeBlock -> maybeBlock.filter(block -> block.getSlot().equals(slot)));
    }

    return historicalChainData.getFinalizedBlockAtSlot(slot);
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockInEffectAtSlot(final UInt64 slot) {
    if (!isChainDataFullyAvailable()) {
      return BLOCK_NOT_AVAILABLE;
    }

    // Try to pull root from recent data
    final Optional<Bytes32> recentRoot = recentChainData.getBlockRootInEffectBySlot(slot);
    if (recentRoot.isPresent()) {
      return getBlockByBlockRoot(recentRoot.get());
    }

    return historicalChainData.getLatestFinalizedBlockAtSlot(slot);
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlotExact(final UInt64 slot) {
    return historicalChainData.getFinalizedBlockAtSlot(slot);
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockInEffectAtSlot(
      final UInt64 slot) {
    return historicalChainData.getLatestFinalizedBlockAtSlot(slot);
  }

  public SafeFuture<Optional<BeaconBlockAndState>> getBlockAndStateInEffectAtSlot(
      final UInt64 slot) {
    return getSignedBlockAndStateInEffectAtSlot(slot)
        .thenApply(result -> result.map(SignedBlockAndState::toUnsigned));
  }

  public SafeFuture<Optional<SignedBlockAndState>> getSignedBlockAndStateInEffectAtSlot(
      final UInt64 slot) {
    return getBlockInEffectAtSlot(slot)
        .thenCompose(
            maybeBlock ->
                maybeBlock
                    .map(this::getStateForBlock)
                    .orElseGet(() -> SafeFuture.completedFuture(Optional.empty())));
  }

  /**
   * Retrieves blob sidecars for the given slot. If the slot is not finalized, it could return
   * non-canonical blob sidecars.
   */
  public SafeFuture<List<BlobSidecar>> getBlobSidecars(
      final UInt64 slot, final List<UInt64> indices) {
    return historicalChainData
        .getBlobSidecarKeys(slot)
        .thenApply(keys -> filterBlobSidecarKeys(keys, indices))
        .thenCompose(this::getBlobSidecars);
  }

  public SafeFuture<List<BlobSidecar>> getBlobSidecars(
      final SlotAndBlockRoot slotAndBlockRoot, final List<UInt64> indices) {
    final Optional<List<BlobSidecar>> maybeBlobSidecars =
        recentChainData.getBlobSidecars(slotAndBlockRoot);
    if (maybeBlobSidecars.isPresent()) {
      return SafeFuture.completedFuture(filterBlobSidecars(maybeBlobSidecars.get(), indices));
    }
    return historicalChainData
        .getBlobSidecarKeys(slotAndBlockRoot)
        .thenApply(keys -> filterBlobSidecarKeys(keys, indices))
        .thenCompose(this::getBlobSidecars);
  }

  public SafeFuture<List<BlobSidecar>> getAllBlobSidecars(
      final UInt64 slot, final List<UInt64> indices) {
    return historicalChainData
        .getAllBlobSidecarKeys(slot)
        .thenApply(keys -> filterBlobSidecarKeys(keys, indices))
        .thenCompose(this::getAllBlobSidecars);
  }

  public SafeFuture<Optional<BeaconState>> getStateAtSlotExact(final UInt64 slot) {
    final Optional<Bytes32> recentBlockRoot = recentChainData.getBlockRootInEffectBySlot(slot);
    if (recentBlockRoot.isPresent()) {
      return getStore()
          .retrieveStateAtSlot(new SlotAndBlockRoot(slot, recentBlockRoot.get()))
          .thenCompose(
              maybeState ->
                  maybeState.isPresent()
                      ? SafeFuture.completedFuture(maybeState)
                      // Check if we can get it from historical state
                      : regenerateStateAndSlotExact(slot));
    }
    return regenerateStateAndSlotExact(slot);
  }

  public SafeFuture<Optional<SignedExecutionPayloadEnvelope>> getExecutionPayloadAtSlotExact(
      final UInt64 slot) {
    if (!isStoreAvailable()) {
      return EXECUTION_PAYLOAD_NOT_AVAILABLE;
    }

    final Optional<Bytes32> recentRoot = recentChainData.getBlockRootInEffectBySlot(slot);
    if (recentRoot.isPresent()) {
      return getExecutionPayloadByBlockRoot(recentRoot.get())
          .thenApply(
              maybeExecutionPayload ->
                  maybeExecutionPayload.filter(
                      executionPayload -> executionPayload.getMessage().getSlot().equals(slot)));
    }

    return historicalChainData.getFinalizedExecutionPayloadAtSlot(slot);
  }

  // EIP-7732 TODO: FIX implement regenerate similar to getStateAtSlotExact
  public SafeFuture<Optional<BeaconState>> getExecutionPayloadStateAtSlotExact(final UInt64 slot) {
    final Optional<Bytes32> recentBlockRoot = recentChainData.getBlockRootInEffectBySlot(slot);
    if (recentBlockRoot.isPresent()) {
      return getStore()
          .retrieveExecutionPayloadStateAtSlot(new SlotAndBlockRoot(slot, recentBlockRoot.get()))
          .thenCompose(
              maybeState ->
                  maybeState.isPresent()
                      ? SafeFuture.completedFuture(maybeState)
                      : completedFuture(Optional.empty()));
    }
    return completedFuture(Optional.empty());
  }

  // EIP-7732 TODO: hacky way of retrieving the pre state of the block
  public SafeFuture<Optional<BeaconState>> getStateAtSlotExactForBlockProduction(
      final UInt64 slot) {
    if (spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.EIP7732)) {
      return getExecutionPayloadStateAtSlotExact(slot)
          .thenCombine(
              getStateAtSlotExact(slot),
              (executionPayloadState, state) -> {
                if (executionPayloadState.isPresent()) {
                  return executionPayloadState;
                }
                return state;
              });
    }
    return getStateAtSlotExact(slot);
  }

  public SafeFuture<Optional<BeaconState>> getStateForBlockProduction(
      final UInt64 slot, final boolean isForkChoiceLateBlockReorgEnabled) {
    if (!isForkChoiceLateBlockReorgEnabled) {
      return getStateAtSlotExactForBlockProduction(slot);
    }
    final Optional<Bytes32> headRoot = getBestBlockRoot();
    if (headRoot.isEmpty()) {
      return getStateAtSlotExactForBlockProduction(slot);
    }
    final Bytes32 root = recentChainData.getProposerHead(headRoot.get(), slot);
    if (root.equals(headRoot.get())) {
      return getStateAtSlotExactForBlockProduction(slot);
    }
    // otherwise we're looking for the parent slot
    // EIP-7732 TODO: edge case, will cover later
    return getStateByBlockRoot(root)
        .thenCompose(
            maybeState ->
                maybeState
                    .map(
                        beaconState ->
                            SafeFuture.completedFuture(regenerateBeaconState(beaconState, slot)))
                    .orElseGet(() -> getStateAtSlotExact(slot)));
  }

  public SafeFuture<Optional<BeaconState>> getStateAtSlotExact(
      final UInt64 slot, final Bytes32 chainHead) {
    final Optional<Bytes32> recentBlockRoot =
        recentChainData.getBlockRootInEffectBySlot(slot, chainHead);
    if (recentBlockRoot.isPresent()) {
      return getStore()
          .retrieveStateAtSlot(new SlotAndBlockRoot(slot, recentBlockRoot.get()))
          .thenCompose(
              maybeState ->
                  maybeState.isPresent()
                      ? SafeFuture.completedFuture(maybeState)
                      // Check if we can get it from historical state
                      : regenerateStateAndSlotExact(slot));
    }
    if (isFinalized(slot)) {
      return regenerateStateAndSlotExact(slot);
    } else {
      return SafeFuture.completedFuture(Optional.empty());
    }
  }

  public SafeFuture<Optional<CheckpointState>> getCheckpointStateAtEpoch(final UInt64 epoch) {
    final UInt64 epochSlot = spec.computeStartSlotAtEpoch(epoch);
    return getSignedBlockAndStateInEffectAtSlot(epochSlot)
        .thenCompose(
            maybeBlockAndState ->
                maybeBlockAndState
                    .map(
                        blockAndState ->
                            getCheckpointState(epoch, blockAndState).thenApply(Optional::of))
                    .orElse(SafeFuture.completedFuture(Optional.empty())));
  }

  public SafeFuture<CheckpointState> getCheckpointState(
      final UInt64 epoch, final SignedBlockAndState latestBlockAndState) {
    final Checkpoint checkpoint = new Checkpoint(epoch, latestBlockAndState.getRoot());
    return recentChainData
        .getStore()
        .retrieveCheckpointState(checkpoint, latestBlockAndState.getState())
        .thenApply(
            checkpointState -> {
              final SignedBeaconBlock block = latestBlockAndState.getBlock();
              return CheckpointState.create(spec, checkpoint, block, checkpointState.orElseThrow());
            });
  }

  public boolean isFinalized(final UInt64 slot) {
    final UInt64 finalizedEpoch = recentChainData.getFinalizedEpoch();
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);
    return finalizedSlot.compareTo(slot) >= 0;
  }

  public boolean isFinalizedEpoch(final UInt64 epoch) {
    final UInt64 finalizedEpoch = recentChainData.getFinalizedEpoch();
    return finalizedEpoch.compareTo(epoch) >= 0;
  }

  /**
   * Returns the latest state at the given slot on the current chain.
   *
   * @param slot the slot to get the state for
   * @return the State at slot
   */
  public SafeFuture<Optional<BeaconState>> getLatestStateAtSlot(final UInt64 slot) {
    if (!isChainDataFullyAvailable()) {
      return STATE_NOT_AVAILABLE;
    }

    if (isRecentData(slot)) {
      return recentChainData
          .retrieveStateInEffectAtSlot(slot)
          .thenCompose(
              recentState -> {
                if (recentState.isPresent()) {
                  return completedFuture(recentState);
                }
                // Fall-through to historical query in case state has moved into historical range
                // during processing
                return historicalChainData.getLatestFinalizedStateAtSlot(slot);
              });
    }

    return historicalChainData.getLatestFinalizedStateAtSlot(slot);
  }

  /**
   * Returns the latest available state prior to the given slot on the current chain.
   *
   * @param slot the latest slot to get the state for
   * @return the State at or before slot
   */
  public SafeFuture<Optional<BeaconState>> getLatestAvailableFinalizedState(final UInt64 slot) {
    if (!isChainDataFullyAvailable()) {
      return STATE_NOT_AVAILABLE;
    }

    return historicalChainData.getLatestAvailableFinalizedState(slot);
  }

  public SafeFuture<Optional<BeaconState>> getStateByBlockRoot(final Bytes32 blockRoot) {
    final UpdatableStore store = getStore();
    if (store == null) {
      LOG.trace("No state at blockRoot {} because the store is not set", blockRoot);
      return STATE_NOT_AVAILABLE;
    }

    return store
        .retrieveBlockState(blockRoot)
        .thenCompose(
            maybeState -> {
              if (maybeState.isPresent()) {
                return completedFuture(maybeState);
              }
              LOG.debug(
                  "State not found in store, querying historicalData for {}", () -> blockRoot);
              return historicalChainData.getFinalizedStateByBlockRoot(blockRoot);
            });
  }

  public SafeFuture<Optional<BeaconState>> getStateByStateRoot(final Bytes32 stateRoot) {
    final UpdatableStore store = getStore();
    if (store == null) {
      LOG.trace("No state at stateRoot {} because the store is not set", stateRoot);
      return STATE_NOT_AVAILABLE;
    }

    return historicalChainData
        .getSlotAndBlockRootByStateRoot(stateRoot)
        .thenCompose(
            maybeSlotAndBlockRoot ->
                maybeSlotAndBlockRoot
                    .map(this::getStateFromSlotAndBlock)
                    .orElseGet(() -> getFinalizedStateFromStateRoot(stateRoot)));
  }

  public SafeFuture<Optional<UInt64>> getFinalizedSlotByBlockRoot(final Bytes32 blockRoot) {
    return historicalChainData.getFinalizedSlotByBlockRoot(blockRoot);
  }

  public Optional<SafeFuture<BeaconState>> getBestState() {
    return recentChainData.getBestState();
  }

  public Optional<Bytes32> getBestBlockRoot() {
    return recentChainData.getBestBlockRoot();
  }

  public Optional<ChainHead> getChainHead() {
    return recentChainData.getChainHead();
  }

  public boolean isChainHeadOptimistic() {
    return recentChainData.isChainHeadOptimistic();
  }

  public boolean isStoreAvailable() {
    return recentChainData != null && recentChainData.getStore() != null;
  }

  private boolean isChainDataFullyAvailable() {
    return !recentChainData.isPreGenesis() && !recentChainData.isPreForkChoice();
  }

  public List<CommitteeAssignment> getCommitteesFromState(
      final BeaconState state, final UInt64 epoch) {
    List<CommitteeAssignment> result = new ArrayList<>();
    final int slotsPerEpoch = spec.slotsPerEpoch(epoch);
    final UInt64 startingSlot = spec.computeStartSlotAtEpoch(epoch);
    int committeeCount = spec.getCommitteeCountPerSlot(state, epoch).intValue();
    for (int i = 0; i < slotsPerEpoch; i++) {
      UInt64 slot = startingSlot.plus(i);
      for (int j = 0; j < committeeCount; j++) {
        UInt64 idx = UInt64.valueOf(j);
        IntList committee = spec.getBeaconCommittee(state, slot, idx);
        result.add(new CommitteeAssignment(committee, idx, slot));
      }
    }
    return result;
  }

  /**
   * @return The slot at which the chain head block was proposed
   */
  public UInt64 getHeadSlot() {
    return this.recentChainData.getHeadSlot();
  }

  /**
   * @return The epoch in which the chain head block was proposed
   */
  public UInt64 getHeadEpoch() {
    final UInt64 headSlot = getHeadSlot();
    return spec.computeEpochAtSlot(headSlot);
  }

  public Optional<ForkInfo> getCurrentForkInfo() {
    return recentChainData.getCurrentForkInfo();
  }

  /**
   * @return The current slot according to clock time
   */
  public UInt64 getCurrentSlot() {
    return this.recentChainData.getCurrentSlot().orElseGet(this::getHeadSlot);
  }

  /**
   * @return The current epoch according to clock time
   */
  public UInt64 getCurrentEpoch() {
    final UInt64 headSlot = getCurrentSlot();
    return spec.computeEpochAtSlot(headSlot);
  }

  @VisibleForTesting
  public UpdatableStore getStore() {
    return recentChainData.getStore();
  }

  public NavigableMap<UInt64, Bytes32> getAncestorRoots(
      final UInt64 startSlot, final UInt64 step, final UInt64 count) {
    return recentChainData.getAncestorRootsOnHeadChain(startSlot, step, count);
  }

  /**
   * @return The earliest available block's slot
   */
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlockSlot() {
    return earliestAvailableBlockSlot.get();
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return recentChainData
        .retrieveSignedBlockByRoot(blockRoot)
        .thenCompose(
            maybeBlock -> {
              if (maybeBlock.isPresent()) {
                return SafeFuture.completedFuture(maybeBlock);
              }
              return historicalChainData.getBlockByBlockRoot(blockRoot);
            });
  }

  public SafeFuture<Optional<SignedExecutionPayloadEnvelope>> getExecutionPayloadByBlockRoot(
      final Bytes32 blockRoot) {
    return recentChainData
        .retrieveExecutionPayloadEnvelopeByBlockRoot(blockRoot)
        .thenCompose(
            maybeExecutionPayload -> {
              if (maybeExecutionPayload.isPresent()) {
                return SafeFuture.completedFuture(maybeExecutionPayload);
              }
              return historicalChainData.getExecutionPayloadByBlockRoot(blockRoot);
            });
  }

  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlobSidecarSlot() {
    return historicalChainData.getEarliestAvailableBlobSidecarSlot();
  }

  /**
   * This is supposed to be consumed by RPC only, because it returns gossip-validated but not yet
   * imported sidecars from the blobSidecar pool.
   */
  public SafeFuture<Optional<BlobSidecar>> getBlobSidecarByBlockRootAndIndex(
      final Bytes32 blockRoot, final UInt64 index) {
    final Optional<BlobSidecar> recentlyValidatedBlobSidecar =
        recentChainData.getRecentlyValidatedBlobSidecar(blockRoot, index);
    if (recentlyValidatedBlobSidecar.isPresent()) {
      return SafeFuture.completedFuture(recentlyValidatedBlobSidecar);
    }
    final Optional<UInt64> hotSlotForBlockRoot = recentChainData.getSlotForBlockRoot(blockRoot);
    if (hotSlotForBlockRoot.isPresent()) {
      return getBlobSidecarByKey(
          new SlotAndBlockRootAndBlobIndex(hotSlotForBlockRoot.get(), blockRoot, index));
    }
    return historicalChainData
        .getBlockByBlockRoot(blockRoot)
        .thenCompose(
            blockOptional -> {
              if (blockOptional.isPresent()) {
                return getBlobSidecarByKey(
                    new SlotAndBlockRootAndBlobIndex(
                        blockOptional.get().getSlot(), blockRoot, index));
              } else {
                return SafeFuture.completedFuture(Optional.empty());
              }
            });
  }

  public SafeFuture<Optional<BlobSidecar>> getBlobSidecarByKey(
      final SlotAndBlockRootAndBlobIndex key) {
    final Optional<List<BlobSidecar>> maybeBlobSidecars =
        recentChainData.getBlobSidecars(key.getSlotAndBlockRoot());
    if (maybeBlobSidecars.isPresent()) {
      return key.getBlobIndex().isLessThan(maybeBlobSidecars.get().size())
          ? SafeFuture.completedFuture(
              Optional.of(maybeBlobSidecars.get().get(key.getBlobIndex().intValue())))
          : SafeFuture.completedFuture(Optional.empty());
    }
    return historicalChainData.getBlobSidecar(key);
  }

  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot, final long limit) {
    return historicalChainData.getBlobSidecarKeys(startSlot, endSlot, limit);
  }

  public Optional<UInt64> getFinalizedBlockSlot() {
    if (recentChainData.isPreGenesis()) {
      return Optional.empty();
    }

    return Optional.of(getStore().getLatestFinalizedBlockSlot());
  }

  public Optional<SignedBeaconBlock> getFinalizedBlock() {
    if (recentChainData.isPreGenesis()) {
      return Optional.empty();
    }

    return getStore().getLatestFinalized().getSignedBeaconBlock();
  }

  public Optional<AnchorPoint> getLatestFinalized() {
    return Optional.ofNullable(getStore()).map(ReadOnlyStore::getLatestFinalized);
  }

  // in line with spec on_block, computing finalized slot as the
  // first slot of the epoch stored in store.finalized_checkpoint
  public SafeFuture<Optional<BeaconState>> getBestFinalizedState() {
    final Optional<Checkpoint> maybeFinalizedCheckpoint = getFinalizedCheckpoint();
    if (maybeFinalizedCheckpoint.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final UInt64 finalizedSlot =
        spec.computeStartSlotAtEpoch(maybeFinalizedCheckpoint.get().getEpoch());
    return getStateAtSlotExact(finalizedSlot);
  }

  public SafeFuture<Optional<BeaconState>> getJustifiedState() {
    if (recentChainData.isPreGenesis()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    return getStore().retrieveCheckpointState(getStore().getJustifiedCheckpoint());
  }

  public Optional<GenesisData> getGenesisData() {
    return recentChainData.getGenesisData();
  }

  public SafeFuture<Optional<BeaconBlockSummary>> getEarliestAvailableBlockSummary() {
    // Pull the latest finalized first, so that we're sure to return a consistent result if the
    // Store is updated while we're pulling historical data
    final Optional<BeaconBlockSummary> latestFinalized =
        Optional.ofNullable(getStore())
            .map(ReadOnlyStore::getLatestFinalized)
            .map(StateAndBlockSummary::getBlockSummary);

    return historicalChainData
        .getEarliestAvailableBlock()
        .thenApply(res -> res.<BeaconBlockSummary>map(b -> b).or(() -> latestFinalized));
  }

  public SafeFuture<List<BlockAndMetaData>> getAllBlocksAtSlot(
      final UInt64 slot, final ChainHead chainHead) {
    if (isFinalized(slot)) {
      return historicalChainData
          .getNonCanonicalBlocksBySlot(slot)
          .thenCombine(
              getBlockAtSlotExact(slot),
              (blocks, canonicalBlock) ->
                  mergeNonCanonicalAndCanonicalBlocks(blocks, chainHead, canonicalBlock));
    }

    return getBlocksByRoots(recentChainData.getAllBlockRootsAtSlot(slot), chainHead);
  }

  public boolean isCanonicalBlock(
      final UInt64 slot, final Bytes32 blockRoot, final Bytes32 chainHeadRoot) {
    if (isFinalized(slot)) {
      return true;
    }
    return isCanonicalBlockCalculated(slot, blockRoot, chainHeadRoot);
  }

  public boolean isOptimisticBlock(final Bytes32 blockRoot) {
    return recentChainData
        .getForkChoiceStrategy()
        .map(forkChoice -> isOptimistic(blockRoot, forkChoice))
        // Can't be optimistically imported if we don't have a Store yet.
        .orElse(false);
  }

  public RecentChainData getRecentChainData() {
    return recentChainData;
  }

  private SafeFuture<Optional<BlobSidecar>> getAllBlobSidecarByKey(
      final SlotAndBlockRootAndBlobIndex key) {
    return historicalChainData
        .getBlobSidecar(key)
        .thenCompose(
            maybeBlobSidecar -> {
              if (maybeBlobSidecar.isPresent()) {
                return SafeFuture.completedFuture(maybeBlobSidecar);
              } else {
                return historicalChainData.getNonCanonicalBlobSidecar(key);
              }
            });
  }

  private boolean isRecentData(final UInt64 slot) {
    checkNotNull(slot);
    if (recentChainData.isPreGenesis()) {
      return false;
    }
    final UInt64 finalizedSlot = getStore().getLatestFinalizedBlockSlot();
    return slot.compareTo(finalizedSlot) >= 0;
  }

  private boolean isCanonicalBlockCalculated(
      final UInt64 slot, final Bytes32 blockRoot, final Bytes32 chainHeadRoot) {
    return spec.getAncestor(
            recentChainData.getForkChoiceStrategy().orElseThrow(), chainHeadRoot, slot)
        .map(ancestor -> ancestor.equals(blockRoot))
        .orElse(false);
  }

  @SuppressWarnings("unchecked")
  private SafeFuture<List<BlockAndMetaData>> getBlocksByRoots(
      final List<Bytes32> blockRoots, final ChainHead chainHead) {

    final SafeFuture<Optional<SignedBeaconBlock>>[] futures =
        blockRoots.stream().map(this::getBlockByBlockRoot).toArray(SafeFuture[]::new);
    return SafeFuture.collectAll(futures)
        .thenApply(
            optionalBlocks ->
                optionalBlocks.stream()
                    .flatMap(Optional::stream)
                    .map(
                        block ->
                            toBlockAndMetaData(
                                block,
                                chainHead,
                                isCanonicalBlockCalculated(
                                    block.getSlot(), block.getRoot(), chainHead.getRoot()),
                                isFinalized(block.getSlot())))
                    .toList());
  }

  List<BlockAndMetaData> mergeNonCanonicalAndCanonicalBlocks(
      final List<SignedBeaconBlock> signedBeaconBlocks,
      final ChainHead chainHead,
      final Optional<SignedBeaconBlock> canonicalBlock) {
    verifyNotNull(signedBeaconBlocks, "Expected empty set but got null");
    return Stream.concat(
            signedBeaconBlocks.stream()
                .map(block -> toBlockAndMetaData(block, chainHead, false, false)),
            canonicalBlock.stream()
                .map(
                    block ->
                        toBlockAndMetaData(block, chainHead, true, isFinalized(block.getSlot()))))
        .toList();
  }

  private BlockAndMetaData toBlockAndMetaData(
      final SignedBeaconBlock signedBeaconBlock,
      final ChainHead chainHead,
      final boolean canonical,
      final boolean finalized) {
    return new BlockAndMetaData(
        signedBeaconBlock,
        spec.atSlot(signedBeaconBlock.getSlot()).getMilestone(),
        chainHead.isOptimistic() || isOptimisticBlock(signedBeaconBlock.getRoot()),
        canonical,
        finalized);
  }

  private boolean isOptimistic(
      final Bytes32 blockRoot, final ReadOnlyForkChoiceStrategy forkChoice) {
    return forkChoice
        .isOptimistic(blockRoot)
        // If the block root is unknown, use the optimistic state of the finalized checkpoint
        // As the block is either canonical and finalized or may have been rejected because it
        // didn't descend from the finalized root. In either case, if the finalized checkpoint is
        // optimistic the status of the block is affected by the optimistic sync
        .orElseGet(
            () ->
                forkChoice
                    .isOptimistic(recentChainData.getFinalizedCheckpoint().orElseThrow().getRoot())
                    .orElseThrow());
  }

  public SafeFuture<Optional<Checkpoint>> getInitialAnchor() {
    return historicalChainData.getAnchor();
  }

  public SafeFuture<Optional<PayloadStatusAndBeaconBlockRoot>> getTimelyExecutionPayload(
      final UInt64 slot) {
    return recentChainData.getTimelyExecutionPayload(slot);
  }

  private SafeFuture<Optional<BeaconState>> getStateFromSlotAndBlock(
      final SlotAndBlockRoot slotAndBlockRoot) {
    final UpdatableStore store = getStore();
    if (store == null) {
      LOG.trace(
          "No state at slot and block root {} because the store is not set", slotAndBlockRoot);
      return STATE_NOT_AVAILABLE;
    }
    return store
        .retrieveStateAtSlot(slotAndBlockRoot)
        .thenCompose(
            maybeState -> {
              if (maybeState.isPresent()) {
                return SafeFuture.completedFuture(maybeState);
              }
              LOG.debug(
                  "State not found in store, querying historicalData for {}:{}",
                  slotAndBlockRoot::getSlot,
                  slotAndBlockRoot::getBlockRoot);
              return getFinalizedStateFromSlotAndBlock(slotAndBlockRoot);
            });
  }

  private SafeFuture<Optional<BeaconState>> getFinalizedStateFromSlotAndBlock(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return historicalChainData
        .getFinalizedStateByBlockRoot(slotAndBlockRoot.getBlockRoot())
        .thenApply(
            maybeState ->
                maybeState.flatMap(
                    preState -> regenerateBeaconState(preState, slotAndBlockRoot.getSlot())));
  }

  private Optional<BeaconState> regenerateBeaconState(
      final BeaconState preState, final UInt64 slot) {
    if (preState.getSlot().equals(slot)) {
      return Optional.of(preState);
    } else if (slot.compareTo(getCurrentSlot()) > 0) {
      LOG.debug("Attempted to wind forward to a future state: {}", slot.toString());
      return Optional.empty();
    }
    try {
      LOG.debug(
          "Processing slots to regenerate state from slot {}, target slot {}",
          preState::getSlot,
          () -> slot);
      return Optional.of(spec.processSlots(preState, slot));
    } catch (SlotProcessingException | EpochProcessingException | IllegalArgumentException e) {
      LOG.debug("State Transition error", e);
      return Optional.empty();
    }
  }

  private SafeFuture<Optional<SignedBlockAndState>> getStateForBlock(
      final SignedBeaconBlock block) {
    return getStateByBlockRoot(block.getRoot())
        .thenApply(maybeState -> maybeState.map(state -> new SignedBlockAndState(block, state)));
  }

  private SafeFuture<Optional<BeaconState>> getFinalizedStateFromStateRoot(
      final Bytes32 stateRoot) {
    return historicalChainData
        .getFinalizedSlotByStateRoot(stateRoot)
        .thenCompose(
            maybeSlot -> maybeSlot.map(this::getStateAtSlotExact).orElse(STATE_NOT_AVAILABLE));
  }

  private SafeFuture<Optional<BeaconState>> regenerateStateAndSlotExact(final UInt64 slot) {
    return getBlockAndStateInEffectAtSlot(slot)
        .thenApplyChecked(
            maybeBlockAndState ->
                maybeBlockAndState.flatMap(
                    blockAndState -> regenerateBeaconState(blockAndState.getState(), slot)));
  }

  private List<BlobSidecar> filterBlobSidecars(
      final List<BlobSidecar> blobSidecars, final List<UInt64> indices) {
    if (indices.isEmpty()) {
      return blobSidecars;
    }
    return blobSidecars.stream().filter(key -> indices.contains(key.getIndex())).toList();
  }

  private Stream<SlotAndBlockRootAndBlobIndex> filterBlobSidecarKeys(
      final List<SlotAndBlockRootAndBlobIndex> keys, final List<UInt64> indices) {
    if (indices.isEmpty()) {
      return keys.stream();
    }
    return keys.stream().filter(key -> indices.contains(key.getBlobIndex()));
  }

  private SafeFuture<List<BlobSidecar>> getBlobSidecars(
      final Stream<SlotAndBlockRootAndBlobIndex> keys) {
    return SafeFuture.collectAll(keys.map(this::getAllBlobSidecarByKey))
        .thenApply(blobSidecars -> blobSidecars.stream().flatMap(Optional::stream).toList());
  }

  private SafeFuture<List<BlobSidecar>> getAllBlobSidecars(
      final Stream<SlotAndBlockRootAndBlobIndex> keys) {
    return SafeFuture.collectAll(keys.map(this::getAllBlobSidecarByKey))
        .thenApply(blobSidecars -> blobSidecars.stream().flatMap(Optional::stream).toList());
  }

  private Optional<Checkpoint> getFinalizedCheckpoint() {
    return Optional.ofNullable(getStore()).map(ReadOnlyStore::getFinalizedCheckpoint);
  }
}
