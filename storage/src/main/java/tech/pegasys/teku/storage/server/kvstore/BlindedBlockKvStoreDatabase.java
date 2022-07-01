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
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.CheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoBlinded;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoBlinded.CombinedUpdaterBlinded;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoBlinded.FinalizedUpdaterBlinded;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoBlinded.HotUpdaterBlinded;

public class BlindedBlockKvStoreDatabase
    extends KvStoreDatabase<
        KvStoreCombinedDaoBlinded,
        CombinedUpdaterBlinded,
        HotUpdaterBlinded,
        FinalizedUpdaterBlinded> {
  private static final Logger LOG = LogManager.getLogger();

  BlindedBlockKvStoreDatabase(
      final KvStoreCombinedDaoBlinded dao,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    super(dao, stateStorageMode, storeNonCanonicalBlocks, spec);
  }

  @Override
  @MustBeClosed
  protected CombinedUpdaterBlinded combinedUpdater() {
    return dao.combinedUpdaterBlinded();
  }

  @Override
  @MustBeClosed
  protected HotUpdaterBlinded hotUpdater() {
    return dao.hotUpdaterBlinded();
  }

  @Override
  @MustBeClosed
  protected FinalizedUpdaterBlinded finalizedUpdater() {
    return dao.finalizedUpdaterBlinded();
  }

  @Override
  public List<SignedBeaconBlock> getNonCanonicalBlocksAtSlot(final UInt64 slot) {
    return dao.getBlindedNonCanonicalBlocksAtSlot(slot).stream()
        .flatMap(block -> getUnblindedBlock(Optional.of(block)).stream())
        .collect(Collectors.toList());
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return getUnblindedBlock(dao.getBlindedBlock(root));
  }

  private Optional<SignedBeaconBlock> getUnblindedBlock(
      final Optional<SignedBeaconBlock> maybeBlock) {
    if (maybeBlock.isEmpty()) {
      return maybeBlock;
    }
    final SignedBeaconBlock block = maybeBlock.get();
    return Optional.of(getUnblindedBlock(block));
  }

  private SignedBeaconBlock getUnblindedBlock(final SignedBeaconBlock block) {
    if (!block.isBlinded()) {
      return block;
    }
    final Optional<Bytes32> payloadRoot =
        block
            .getMessage()
            .getBody()
            .getOptionalExecutionPayloadSummary()
            .map(ExecutionPayloadSummary::getPayloadHash);
    final Optional<Bytes> maybePayload = payloadRoot.flatMap(dao::getExecutionPayload);
    if (maybePayload.isEmpty()) {
      return block;
    }

    final ExecutionPayload executionPayload =
        spec.deserializeExecutionPayload(maybePayload.get(), block.getSlot());
    return block.unblind(spec.atSlot(block.getSlot()).getSchemaDefinitions(), executionPayload);
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 blockRoot) {
    return getUnblindedBlock(dao.getBlindedBlock(blockRoot));
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamHotBlocks() {
    return dao.streamBlindedHotBlocks().map(this::getUnblindedBlock);
  }

  @Override
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return null;
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots) {
    return blockRoots.stream()
        .flatMap(root -> dao.getBlindedBlock(root).stream())
        .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));
  }

  @Override
  protected void storeAnchorStateAndBlock(
      final CombinedUpdaterBlinded updater,
      final BeaconState anchorState,
      final SignedBeaconBlock block) {
    updater.addBlindedBlock(block, spec);
    updater.addHotBlockCheckpointEpochs(
        block.getRoot(),
        new CheckpointEpochs(
            anchorState.getCurrentJustifiedCheckpoint().getEpoch(),
            anchorState.getFinalizedCheckpoint().getEpoch()));
  }

  @Override
  protected void storeFinalizedBlocksToDao(final Collection<SignedBeaconBlock> blocks) {
    try (final FinalizedUpdaterBlinded updater = finalizedUpdater()) {
      blocks.forEach(
          block -> {
            updater.addBlindedBlock(block, spec);
            block
                .getMessage()
                .getBody()
                .getOptionalExecutionPayload()
                .ifPresent(updater::addExecutionPayload);
            updater.addFinalizedBlockRootBySlot(block);
          });
      updater.commit();
    }
  }

  @Override
  protected Map<Bytes32, StoredBlockMetadata> buildHotBlockMetadata() {
    final Map<Bytes32, StoredBlockMetadata> blockInformation = new HashMap<>();
    try (final Stream<Map.Entry<Bytes32, CheckpointEpochs>> checkpoints =
        dao.streamCheckpointEpochs()) {
      checkpoints.forEach(
          (entry) -> {
            final Optional<SignedBeaconBlock> maybeBlock = dao.getBlindedBlock(entry.getKey());
            maybeBlock.ifPresent(
                signedBeaconBlock ->
                    blockInformation.put(
                        entry.getKey(),
                        StoredBlockMetadata.fromBlockAndCheckpointEpochs(
                            signedBeaconBlock, entry.getValue())));
          });
    }
    return blockInformation;
  }

  @Override
  protected Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return getUnblindedBlock(dao.getBlindedBlock(root));
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return getUnblindedBlock(dao.getLatestBlindedBlockAtSlot(slot));
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return dao.getBlindedBlock(blockRoot).map(SignedBeaconBlock::getSlot);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return dao.getSlotAndBlockRootForFinalizedStateRoot(stateRoot).map(SlotAndBlockRoot::getSlot);
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return dao.getFinalizedBlockRootAtSlot(slot)
        .flatMap(dao::getBlindedBlock)
        .map(this::getUnblindedBlock);
  }

  @Override
  public Optional<UInt64> getEarliestAvailableBlockSlot() {
    return dao.getEarliestBlindedBlockSlot();
  }

  @Override
  public Optional<SignedBeaconBlock> getEarliestAvailableBlock() {
    return getUnblindedBlock(dao.getEarliestBlindedBlock());
  }

  @Override
  public long countBlindedBlocks() {
    return dao.countBlindedBlocks();
  }

  @Override
  protected void addFinalizedBlock(
      final SignedBeaconBlock block, final FinalizedUpdaterBlinded updater) {
    updater.addFinalizedBlockRootBySlot(block);
  }

  @Override
  protected void updateHotBlocks(
      final HotUpdaterBlinded updater,
      final Map<Bytes32, BlockAndCheckpointEpochs> addedBlocks,
      final Set<Bytes32> deletedHotBlockRoots) {
    try (final FinalizedUpdaterBlinded finalizedUpdater = dao.finalizedUpdaterBlinded()) {
      addedBlocks
          .values()
          .forEach(block -> finalizedUpdater.addBlindedBlock(block.getBlock(), spec));
      deletedHotBlockRoots.forEach(finalizedUpdater::deleteBlindedBlock);
      finalizedUpdater.commit();
    }
    updater.addCheckpointEpochs(addedBlocks);
    deletedHotBlockRoots.forEach(updater::pruneHotBlockContext);
  }

  @Override
  protected void storeNonCanonicalBlocks(
      final Set<Bytes32> blockRoots, final Map<Bytes32, Bytes32> finalizedChildToParentMap) {
    if (!storeNonCanonicalBlocks) {
      final Set<SignedBeaconBlock> nonCanonicalBlocks =
          blockRoots.stream()
              .filter(root -> !finalizedChildToParentMap.containsKey(root))
              .flatMap(root -> getHotBlock(root).stream())
              .collect(Collectors.toSet());
      int i = 0;
      final Iterator<SignedBeaconBlock> it = nonCanonicalBlocks.iterator();
      while (it.hasNext()) {
        final int start = i;
        try (final FinalizedUpdaterBlinded updater = dao.finalizedUpdaterBlinded()) {
          while (it.hasNext() && (i - start) < TX_BATCH_SIZE) {
            final SignedBeaconBlock block = it.next();
            LOG.debug(
                "DELETE non canonical block {}:{}", block.getRoot().toHexString(), block.getSlot());
            updater.deleteBlindedBlock(block.getRoot());

            block
                .getMessage()
                .getBody()
                .getOptionalExecutionPayloadSummary()
                .ifPresent(
                    payload -> {
                      LOG.debug(
                          "DELETE payload({}) from block {}",
                          payload.getPayloadHash(),
                          block.getRoot().toHexString());
                      updater.deleteExecutionPayload(payload.getPayloadHash());
                    });
            i++;
          }
          updater.commit();
        }
      }
    }
  }
}
