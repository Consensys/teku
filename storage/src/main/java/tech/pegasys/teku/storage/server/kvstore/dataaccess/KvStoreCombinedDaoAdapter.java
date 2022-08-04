/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedKvStoreDao.V4FinalizedUpdater;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4HotKvStoreDao.V4HotUpdater;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;

public class KvStoreCombinedDaoAdapter
    implements KvStoreCombinedDaoBlinded, KvStoreCombinedDaoUnblinded, V4MigratableSourceDao {
  private final V4HotKvStoreDao hotDao;
  private final V4FinalizedKvStoreDao finalizedDao;

  public KvStoreCombinedDaoAdapter(
      final V4HotKvStoreDao hotDao, final V4FinalizedKvStoreDao finalizedDao) {
    this.hotDao = hotDao;
    this.finalizedDao = finalizedDao;
  }

  @Override
  public Optional<UInt64> getGenesisTime() {
    return hotDao.getGenesisTime();
  }

  @Override
  public Optional<Checkpoint> getAnchor() {
    return hotDao.getAnchor();
  }

  @Override
  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return hotDao.getJustifiedCheckpoint();
  }

  @Override
  public Optional<Checkpoint> getBestJustifiedCheckpoint() {
    return hotDao.getBestJustifiedCheckpoint();
  }

  @Override
  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return hotDao.getFinalizedCheckpoint();
  }

  @Override
  public Optional<BeaconState> getLatestFinalizedState() {
    return hotDao.getLatestFinalizedState();
  }

  @Override
  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return hotDao.getWeakSubjectivityCheckpoint();
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 root) {
    return hotDao.getHotBlock(root);
  }

  @Override
  public Optional<BlockCheckpoints> getHotBlockCheckpointEpochs(final Bytes32 root) {
    return hotDao.getHotBlockCheckpointEpochs(root);
  }

  @Override
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return hotDao.getHotState(root);
  }

  @Override
  public List<Bytes32> getStateRootsBeforeSlot(final UInt64 slot) {
    return hotDao.getStateRootsBeforeSlot(slot);
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return hotDao.getSlotAndBlockRootFromStateRoot(stateRoot);
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamHotBlocks() {
    return hotDao.streamHotBlocks();
  }

  @Override
  @MustBeClosed
  public Stream<Bytes> streamUnblindedHotBlocksAsSsz() {
    return hotDao.streamUnblindedHotBlocksAsSsz();
  }

  @Override
  @MustBeClosed
  public Stream<Bytes> streamBlindedHotBlocksAsSsz() {
    return hotDao
        .streamBlockCheckpoints()
        .map(Map.Entry::getKey)
        .flatMap(root -> finalizedDao.getBlindedBlockAsSsz(root).stream());
  }

  @Override
  public long countUnblindedHotBlocks() {
    return hotDao.countUnblindedHotBlocks();
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
    return hotDao.getVotes();
  }

  @Override
  @MustBeClosed
  public HotUpdaterBlinded hotUpdaterBlinded() {
    return hotDao.hotUpdater();
  }

  @Override
  @MustBeClosed
  public HotUpdaterUnblinded hotUpdaterUnblinded() {
    return hotDao.hotUpdater();
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return finalizedDao.getFinalizedBlock(root);
  }

  @Override
  @MustBeClosed
  public FinalizedUpdaterBlinded finalizedUpdaterBlinded() {
    return finalizedDao.finalizedUpdater();
  }

  @Override
  @MustBeClosed
  public FinalizedUpdaterUnblinded finalizedUpdaterUnblinded() {
    return finalizedDao.finalizedUpdater();
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return finalizedDao.getFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<UInt64> getEarliestFinalizedBlockSlot() {
    return finalizedDao.getEarliestFinalizedBlockSlot();
  }

  @Override
  public Optional<SignedBeaconBlock> getEarliestFinalizedBlock() {
    return finalizedDao.getEarliestFinalizedBlock();
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return finalizedDao.getLatestFinalizedBlockAtSlot(slot);
  }

  @Override
  public List<SignedBeaconBlock> getNonCanonicalUnblindedBlocksAtSlot(final UInt64 slot) {
    return finalizedDao.getNonCanonicalBlocksAtSlot(slot);
  }

  @Override
  public List<SignedBeaconBlock> getBlindedNonCanonicalBlocksAtSlot(final UInt64 slot) {
    return finalizedDao.getBlindedNonCanonicalBlocksAtSlot(slot);
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return finalizedDao.getLatestAvailableFinalizedState(maxSlot);
  }

  @Override
  public Set<Bytes32> getNonCanonicalBlockRootsAtSlot(final UInt64 slot) {
    return finalizedDao.getNonCanonicalBlockRootsAtSlot(slot);
  }

  @Override
  public long countNonCanonicalSlots() {
    return finalizedDao.countNonCanonicalSlots();
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamUnblindedFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return finalizedDao.streamFinalizedBlocks(startSlot, endSlot);
  }

  @Override
  @MustBeClosed
  public Stream<Map.Entry<Bytes, Bytes>> streamUnblindedFinalizedBlocksRaw() {
    return finalizedDao.streamUnblindedFinalizedBlocksRaw();
  }

  @Override
  public Optional<SignedBeaconBlock> getBlindedBlock(final Bytes32 root) {
    return finalizedDao.getBlindedBlock(root);
  }

  @Override
  public Optional<Bytes> getExecutionPayload(final Bytes32 root) {
    return finalizedDao.getExecutionPayload(root);
  }

  @Override
  public Optional<UInt64> getEarliestBlindedBlockSlot() {
    return finalizedDao.getEarliestBlindedBlockSlot();
  }

  @Override
  public Optional<Bytes32> getFinalizedBlockRootAtSlot(final UInt64 slot) {
    return finalizedDao.getFinalizedBlockRootAtSlot(slot);
  }

  @Override
  public Optional<SignedBeaconBlock> getEarliestBlindedBlock() {
    return finalizedDao.getEarliestBlindedBlock();
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestBlindedBlockAtSlot(final UInt64 slot) {
    return finalizedDao.getLatestBlindedBlockAtSlot(slot);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return finalizedDao.getSlotForFinalizedBlockRoot(blockRoot);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return finalizedDao.getSlotForFinalizedStateRoot(stateRoot);
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootForFinalizedStateRoot(
      final Bytes32 stateRoot) {
    return finalizedDao.getSlotAndBlockRootForFinalizedStateRoot(stateRoot);
  }

  @Override
  public Optional<UInt64> getOptimisticTransitionBlockSlot() {
    return finalizedDao.getOptimisticTransitionBlockSlot();
  }

  @Override
  public Map<String, Long> getColumnCounts() {
    final HashMap<String, Long> result = new LinkedHashMap<>(hotDao.getColumnCounts());
    result.putAll(finalizedDao.getColumnCounts());
    return result;
  }

  @Override
  @MustBeClosed
  public Stream<UInt64> streamFinalizedStateSlots(final UInt64 startSlot, final UInt64 endSlot) {
    return finalizedDao.streamFinalizedStateSlots(startSlot, endSlot);
  }

  @Override
  public Optional<? extends SignedBeaconBlock> getNonCanonicalBlock(final Bytes32 root) {
    return finalizedDao.getNonCanonicalBlock(root);
  }

  @Override
  @MustBeClosed
  public Stream<Bytes32> streamFinalizedBlockRoots(final UInt64 startSlot, final UInt64 endSlot) {
    return finalizedDao.streamFinalizedBlockRoots(startSlot, endSlot);
  }

  @Override
  @MustBeClosed
  public Stream<Map.Entry<Bytes32, SignedBeaconBlock>> streamUnblindedNonCanonicalBlocks() {
    return finalizedDao.streamUnblindedNonCanonicalBlocks();
  }

  @Override
  @MustBeClosed
  public Stream<Map.Entry<Bytes32, UInt64>> streamUnblindedFinalizedBlockRoots() {
    return finalizedDao.streamUnblindedFinalizedBlockRoots();
  }

  @MustBeClosed
  @Override
  public Stream<SignedBeaconBlock> streamBlindedBlocks() {
    return finalizedDao.streamBlindedBlocks();
  }

  @Override
  public void ingest(
      final KvStoreCombinedDaoCommon dao, final int batchSize, final Consumer<String> logger) {
    throw new UnsupportedOperationException("Cannot migrate to a split database format");
  }

  @Override
  @MustBeClosed
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return hotDao.streamDepositsFromBlocks();
  }

  @Override
  @MustBeClosed
  public Stream<Map.Entry<Bytes32, BlockCheckpoints>> streamBlockCheckpoints() {
    return hotDao.streamBlockCheckpoints();
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return hotDao.getMinGenesisTimeBlock();
  }

  @Override
  public void close() throws Exception {
    hotDao.close();
    hotDao.close();
    finalizedDao.close();
  }

  @Override
  @MustBeClosed
  @SuppressWarnings("MustBeClosedChecker")
  public CombinedUpdaterBlinded combinedUpdaterBlinded() {
    return combinedUpdater();
  }

  @Override
  @MustBeClosed
  @SuppressWarnings("MustBeClosedChecker")
  public CombinedUpdaterUnblinded combinedUpdaterUnblinded() {
    return combinedUpdater();
  }

  @MustBeClosed
  @SuppressWarnings("MustBeClosedChecker")
  private CombinedUpdaterAdapter combinedUpdater() {
    return new CombinedUpdaterAdapter(hotDao.hotUpdater(), finalizedDao.finalizedUpdater());
  }

  @Override
  public Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    final Map<String, KvStoreColumn<?, ?>> allColumns = new HashMap<>();
    allColumns.putAll(hotDao.getColumnMap());
    allColumns.putAll(finalizedDao.getColumnMap());
    return allColumns;
  }

  @Override
  public Map<String, KvStoreVariable<?>> getVariableMap() {
    final Map<String, KvStoreVariable<?>> allVariables = new HashMap<>();
    allVariables.putAll(hotDao.getVariableMap());
    allVariables.putAll(finalizedDao.getVariableMap());
    return allVariables;
  }

  @Override
  public <T> Optional<Bytes> getRawVariable(final KvStoreVariable<T> var) {
    if (hotDao.getVariableMap().containsValue(var)) {
      return hotDao.getRawVariable(var);
    } else {
      return finalizedDao.getRawVariable(var);
    }
  }

  @Override
  @MustBeClosed
  public <K, V> Stream<ColumnEntry<Bytes, Bytes>> streamRawColumn(
      final KvStoreColumn<K, V> kvStoreColumn) {
    if (hotDao.getColumnMap().containsValue(kvStoreColumn)) {
      return hotDao.streamRawColumn(kvStoreColumn);
    } else {
      return finalizedDao.streamRawColumn(kvStoreColumn);
    }
  }

  @Override
  public <K, V> Optional<Bytes> getRaw(final KvStoreColumn<K, V> kvStoreColumn, final K key) {
    if (hotDao.getColumnMap().containsValue(kvStoreColumn)) {
      return hotDao.getRaw(kvStoreColumn, key);
    } else {
      return finalizedDao.getRaw(kvStoreColumn, key);
    }
  }

  private static class CombinedUpdaterAdapter
      implements CombinedUpdaterUnblinded, CombinedUpdaterBlinded {
    private final V4HotUpdater hotUpdater;
    private final V4FinalizedUpdater finalizedUpdater;

    private CombinedUpdaterAdapter(
        final V4HotUpdater hotUpdater, final V4FinalizedUpdater finalizedUpdater) {
      this.hotUpdater = hotUpdater;
      this.finalizedUpdater = finalizedUpdater;
    }

    @Override
    public void setGenesisTime(final UInt64 genesisTime) {
      hotUpdater.setGenesisTime(genesisTime);
    }

    @Override
    public void setAnchor(final Checkpoint anchorPoint) {
      hotUpdater.setAnchor(anchorPoint);
    }

    @Override
    public void setJustifiedCheckpoint(final Checkpoint checkpoint) {
      hotUpdater.setJustifiedCheckpoint(checkpoint);
    }

    @Override
    public void setBestJustifiedCheckpoint(final Checkpoint checkpoint) {
      hotUpdater.setBestJustifiedCheckpoint(checkpoint);
    }

    @Override
    public void setFinalizedCheckpoint(final Checkpoint checkpoint) {
      hotUpdater.setFinalizedCheckpoint(checkpoint);
    }

    @Override
    public void setWeakSubjectivityCheckpoint(final Checkpoint checkpoint) {
      hotUpdater.setWeakSubjectivityCheckpoint(checkpoint);
    }

    @Override
    public void clearWeakSubjectivityCheckpoint() {
      hotUpdater.clearWeakSubjectivityCheckpoint();
    }

    @Override
    public void setLatestFinalizedState(final BeaconState state) {
      hotUpdater.setLatestFinalizedState(state);
    }

    @Override
    public void addHotBlock(final BlockAndCheckpoints blockAndCheckpoints) {
      hotUpdater.addHotBlock(blockAndCheckpoints);
    }

    @Override
    public void addHotBlockCheckpointEpochs(
        final Bytes32 blockRoot, final BlockCheckpoints blockCheckpoints) {
      hotUpdater.addHotBlockCheckpointEpochs(blockRoot, blockCheckpoints);
    }

    @Override
    public void addHotState(final Bytes32 blockRoot, final BeaconState state) {
      hotUpdater.addHotState(blockRoot, state);
    }

    @Override
    public void addHotStates(final Map<Bytes32, BeaconState> states) {
      hotUpdater.addHotStates(states);
    }

    @Override
    public void addVotes(final Map<UInt64, VoteTracker> states) {
      hotUpdater.addVotes(states);
    }

    @Override
    public void addHotBlocks(final Map<Bytes32, BlockAndCheckpoints> blocks) {
      hotUpdater.addHotBlocks(blocks);
    }

    @Override
    public void addHotStateRoots(
        final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {
      hotUpdater.addHotStateRoots(stateRootToSlotAndBlockRootMap);
    }

    @Override
    public void pruneHotBlockContext(final Bytes32 blockRoot) {
      hotUpdater.pruneHotBlockContext(blockRoot);
    }

    @Override
    public void pruneHotStateRoots(final List<Bytes32> stateRoots) {
      hotUpdater.pruneHotStateRoots(stateRoots);
    }

    @Override
    public void deleteHotBlock(final Bytes32 blockRoot) {
      hotUpdater.deleteHotBlock(blockRoot);
    }

    @Override
    public void deleteUnblindedHotBlockOnly(final Bytes32 blockRoot) {
      hotUpdater.deleteUnblindedHotBlockOnly(blockRoot);
    }

    @Override
    public void deleteHotState(final Bytes32 blockRoot) {
      hotUpdater.deleteHotState(blockRoot);
    }

    @Override
    public void addFinalizedBlock(final SignedBeaconBlock block) {
      finalizedUpdater.addFinalizedBlock(block);
    }

    @Override
    public void addFinalizedBlockRootBySlot(final UInt64 slot, final Bytes32 root) {
      finalizedUpdater.addFinalizedBlockRootBySlot(slot, root);
    }

    @Override
    public void addBlindedFinalizedBlock(
        final SignedBeaconBlock block, final Bytes32 root, final Spec spec) {
      finalizedUpdater.addBlindedFinalizedBlock(block, root, spec);
    }

    @Override
    public void addBlindedFinalizedBlockRaw(
        final Bytes blockBytes, final Bytes32 root, final UInt64 slot) {
      finalizedUpdater.addBlindedFinalizedBlockRaw(blockBytes, root, slot);
    }

    @Override
    public void addBlindedBlock(
        final SignedBeaconBlock block, final Bytes32 blockRoot, final Spec spec) {
      finalizedUpdater.addBlindedBlock(block, blockRoot, spec);
    }

    @Override
    public void addExecutionPayload(final ExecutionPayload payload) {
      finalizedUpdater.addExecutionPayload(payload);
    }

    @Override
    public void deleteBlindedBlock(final Bytes32 blockRoot) {
      finalizedUpdater.deleteBlindedBlock(blockRoot);
    }

    @Override
    public void deleteExecutionPayload(final Bytes32 payloadHash) {
      finalizedUpdater.deleteExecutionPayload(payloadHash);
    }

    @Override
    public void addNonCanonicalBlock(final SignedBeaconBlock block) {
      finalizedUpdater.addNonCanonicalBlock(block);
    }

    @Override
    public void deleteUnblindedFinalizedBlock(final UInt64 slot, final Bytes32 blockRoot) {
      finalizedUpdater.deleteUnblindedFinalizedBlock(slot, blockRoot);
    }

    @Override
    public void deleteUnblindedNonCanonicalBlockOnly(final Bytes32 blockRoot) {
      finalizedUpdater.deleteUnblindedNonCanonicalBlockOnly(blockRoot);
    }

    @Override
    public void addNonCanonicalRootAtSlot(final UInt64 slot, final Set<Bytes32> blockRoots) {
      finalizedUpdater.addNonCanonicalRootAtSlot(slot, blockRoots);
    }

    @Override
    public void addFinalizedState(final Bytes32 blockRoot, final BeaconState state) {
      finalizedUpdater.addFinalizedState(blockRoot, state);
    }

    @Override
    public void addFinalizedStateRoot(final Bytes32 stateRoot, final UInt64 slot) {
      finalizedUpdater.addFinalizedStateRoot(stateRoot, slot);
    }

    @Override
    public void setOptimisticTransitionBlockSlot(final Optional<UInt64> transitionBlockSlot) {
      finalizedUpdater.setOptimisticTransitionBlockSlot(transitionBlockSlot);
    }

    @Override
    public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
      hotUpdater.addMinGenesisTimeBlock(event);
    }

    @Override
    public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
      hotUpdater.addDepositsFromBlockEvent(event);
    }

    @Override
    public void commit() {
      finalizedUpdater.commit();
      hotUpdater.commit();
    }

    @Override
    public void cancel() {
      finalizedUpdater.cancel();
      hotUpdater.cancel();
    }

    @Override
    public void close() {
      finalizedUpdater.close();
      hotUpdater.close();
    }
  }
}
