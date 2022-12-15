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

package tech.pegasys.teku.storage.server.kvstore;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoUnblinded;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoUnblinded.CombinedUpdaterUnblinded;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoUnblinded.FinalizedUpdaterUnblinded;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoUnblinded.HotUpdaterUnblinded;

public class UnblindedBlockKvStoreDatabase
    extends KvStoreDatabase<
        KvStoreCombinedDaoUnblinded,
        CombinedUpdaterUnblinded,
        HotUpdaterUnblinded,
        FinalizedUpdaterUnblinded> {
  private static final Logger LOG = LogManager.getLogger();

  UnblindedBlockKvStoreDatabase(
      final KvStoreCombinedDaoUnblinded dao,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    super(dao, stateStorageMode, storeNonCanonicalBlocks, spec);
  }

  @Override
  @MustBeClosed
  protected CombinedUpdaterUnblinded combinedUpdater() {
    return dao.combinedUpdaterUnblinded();
  }

  @Override
  @MustBeClosed
  protected HotUpdaterUnblinded hotUpdater() {
    return dao.hotUpdaterUnblinded();
  }

  @Override
  @MustBeClosed
  protected FinalizedUpdaterUnblinded finalizedUpdater() {
    return dao.finalizedUpdaterUnblinded();
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return dao.getSlotForFinalizedBlockRoot(blockRoot);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return dao.getSlotForFinalizedStateRoot(stateRoot);
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return dao.getFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<UInt64> getEarliestAvailableBlockSlot() {
    return dao.getEarliestFinalizedBlockSlot();
  }

  @Override
  public Optional<SignedBeaconBlock> getEarliestAvailableBlock() {
    return dao.getEarliestFinalizedBlock();
  }

  @Override
  public Optional<SignedBeaconBlock> getLastAvailableFinalizedBlock() {
    return dao.getFinalizedCheckpoint()
        .flatMap(
            checkpoint -> getFinalizedBlock(checkpoint.toSlotAndBlockRoot(spec).getBlockRoot()));
  }

  @Override
  public Optional<Bytes32> getFinalizedBlockRootBySlot(final UInt64 slot) {
    return dao.getFinalizedBlockAtSlot(slot).map(SignedBeaconBlock::getRoot);
  }

  @Override
  public Optional<ExecutionPayload> getExecutionPayload(
      final Bytes32 blockRoot, final UInt64 slot) {
    return getFinalizedBlock(blockRoot)
        .flatMap(
            signedBeaconBlock ->
                signedBeaconBlock.getMessage().getBody().getOptionalExecutionPayload());
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return dao.getLatestFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return dao.getHotBlock(root)
        .or(() -> dao.getFinalizedBlock(root))
        .or(() -> dao.getNonCanonicalBlock(root));
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots) {
    return blockRoots.stream()
        .flatMap(root -> dao.getHotBlock(root).stream())
        .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 blockRoot) {
    return dao.getHotBlock(blockRoot);
  }

  @Override
  public Stream<Map.Entry<Bytes, Bytes>> streamHotBlocksAsSsz() {
    return dao.streamUnblindedHotBlocksAsSsz();
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return dao.streamUnblindedFinalizedBlocks(startSlot, endSlot);
  }

  @Override
  protected Map<Bytes32, StoredBlockMetadata> buildHotBlockMetadata() {
    final Map<Bytes32, StoredBlockMetadata> blockInformation = new HashMap<>();
    try (final Stream<SignedBeaconBlock> hotBlocks = dao.streamHotBlocks()) {
      hotBlocks.forEach(
          b -> {
            final Optional<BlockCheckpoints> checkpointEpochs =
                dao.getHotBlockCheckpointEpochs(b.getRoot());
            blockInformation.put(
                b.getRoot(),
                new StoredBlockMetadata(
                    b.getSlot(),
                    b.getRoot(),
                    b.getParentRoot(),
                    b.getStateRoot(),
                    b.getMessage()
                        .getBody()
                        .getOptionalExecutionPayload()
                        .map(ExecutionPayload::getBlockHash),
                    checkpointEpochs));
          });
    }
    return blockInformation;
  }

  @Override
  protected void storeAnchorStateAndBlock(
      final CombinedUpdaterUnblinded updater,
      final BeaconState anchorState,
      final SignedBeaconBlock block) {
    updater.addHotBlock(
        new BlockAndCheckpoints(
            block,
            new BlockCheckpoints(
                anchorState.getCurrentJustifiedCheckpoint(),
                anchorState.getFinalizedCheckpoint(),
                anchorState.getCurrentJustifiedCheckpoint(),
                anchorState.getFinalizedCheckpoint())));
    // Save to cold storage
    updater.addFinalizedBlock(block);
  }

  @Override
  public List<SignedBeaconBlock> getNonCanonicalBlocksAtSlot(final UInt64 slot) {
    return dao.getNonCanonicalUnblindedBlocksAtSlot(slot);
  }

  @Override
  protected void storeFinalizedBlocksToDao(final Collection<SignedBeaconBlock> blocks) {
    try (final FinalizedUpdaterUnblinded updater = finalizedUpdater()) {
      blocks.forEach(updater::addFinalizedBlock);
      updater.commit();
    }
  }

  @Override
  protected Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return dao.getHotBlock(root);
  }

  @Override
  public Map<String, Long> getColumnCounts() {
    return dao.getColumnCounts();
  }

  @Override
  public void migrate() {}

  @MustBeClosed
  @Override
  public Stream<SignedBeaconBlock> streamBlindedBlocks() {
    return Stream.empty();
  }

  @Override
  public void deleteHotBlocks(final Set<Bytes32> blockRootsToDelete) {
    try (final HotUpdaterUnblinded updater = hotUpdater()) {
      blockRootsToDelete.forEach(updater::deleteHotBlock);
      updater.commit();
    }
  }

  @Override
  public void pruneFinalizedBlocks(final UInt64 lastSlotToPrune) {
    // TODO: Very likely need to reapply batching here.
    try (final FinalizedUpdaterUnblinded updater = finalizedUpdater()) {
      updater.pruneFinalizedUnblindedBlocks(UInt64.ZERO, lastSlotToPrune);
      updater.commit();
    }
  }

  @Override
  protected void updateHotBlocks(
      final HotUpdaterUnblinded updater,
      final Map<Bytes32, BlockAndCheckpoints> addedBlocks,
      final Set<Bytes32> deletedHotBlockRoots,
      final Set<Bytes32> finalizedBlockRoots) {
    updater.addHotBlocks(addedBlocks);
    deletedHotBlockRoots.forEach(updater::deleteHotBlock);
  }

  @Override
  protected void addFinalizedBlock(
      final SignedBeaconBlock block,
      final boolean isRemovedFromHotBlocks,
      final FinalizedUpdaterUnblinded updater) {
    updater.addFinalizedBlock(block);
  }

  @Override
  protected void storeNonCanonicalBlocks(
      final Set<Bytes32> blockRoots, final Map<Bytes32, Bytes32> finalizedChildToParentMap) {
    if (storeNonCanonicalBlocks) {
      final Set<SignedBeaconBlock> nonCanonicalBlocks =
          blockRoots.stream()
              .filter(root -> !finalizedChildToParentMap.containsKey(root))
              .flatMap(root -> getHotBlock(root).stream())
              .collect(Collectors.toSet());
      int i = 0;
      final Iterator<SignedBeaconBlock> it = nonCanonicalBlocks.iterator();
      while (it.hasNext()) {
        final Map<UInt64, Set<Bytes32>> nonCanonicalRootsBySlotBuffer = new HashMap<>();
        final int start = i;
        try (final FinalizedUpdaterUnblinded updater = finalizedUpdater()) {
          while (it.hasNext() && (i - start) < TX_BATCH_SIZE) {
            final SignedBeaconBlock block = it.next();
            LOG.debug("Non canonical block {}:{}", block.getRoot().toHexString(), block.getSlot());
            updater.addNonCanonicalBlock(block);
            nonCanonicalRootsBySlotBuffer
                .computeIfAbsent(block.getSlot(), dao::getNonCanonicalBlockRootsAtSlot)
                .add(block.getRoot());
            i++;
          }
          nonCanonicalRootsBySlotBuffer.forEach(updater::addNonCanonicalRootAtSlot);
          updater.commit();
        }
      }
    }
  }
}
