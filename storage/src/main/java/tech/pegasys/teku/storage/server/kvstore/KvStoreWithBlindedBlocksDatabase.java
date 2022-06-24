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
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreFinalizedDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreHotDao;

public class KvStoreWithBlindedBlocksDatabase extends KvStoreDatabase {
  private static final Logger LOG = LogManager.getLogger();

  KvStoreWithBlindedBlocksDatabase(
      final KvStoreCombinedDao dao,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    super(dao, stateStorageMode, storeNonCanonicalBlocks, spec);
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
    if (maybeBlock.isEmpty() || !maybeBlock.get().isBlinded()) {
      return maybeBlock;
    }
    final SignedBeaconBlock block = maybeBlock.get();
    final Optional<Bytes32> payloadRoot =
        block
            .getMessage()
            .getBody()
            .getOptionalExecutionPayloadSummary()
            .map(ExecutionPayloadSummary::getPayloadHash);
    final Optional<Bytes> maybePayload = payloadRoot.flatMap(dao::getExecutionPayload);
    if (maybePayload.isEmpty()) {
      return maybeBlock;
    }

    final ExecutionPayload executionPayload =
        spec.deserializeExecutionPayload(maybePayload.get(), block.getSlot());
    return Optional.of(
        block.unblind(spec.atSlot(block.getSlot()).getSchemaDefinitions(), executionPayload));
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots) {
    return blockRoots.stream()
        .flatMap(root -> dao.getBlindedBlock(root).stream())
        .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));
  }

  @Override
  protected void storeAnchorStateAndBlock(
      final KvStoreCombinedDao.CombinedUpdater updater,
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
    try (final KvStoreFinalizedDao.FinalizedUpdater updater = dao.finalizedUpdater()) {
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
  public Optional<SignedBeaconBlock> getEarliestAvailableBlock() {
    return getUnblindedBlock(dao.getEarliestBlindedBlock());
  }

  @Override
  protected void addFinalizedBlock(
      final SignedBeaconBlock block, final KvStoreFinalizedDao.FinalizedUpdater updater) {
    updater.addFinalizedBlockRootBySlot(block);
  }

  @Override
  protected void updateHotBlocks(
      final KvStoreHotDao.HotUpdater updater,
      final Map<Bytes32, BlockAndCheckpointEpochs> addedBlocks,
      final Set<Bytes32> deletedHotBlockRoots) {
    try (final KvStoreFinalizedDao.FinalizedUpdater finalizedUpdater = dao.finalizedUpdater()) {
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
        try (final KvStoreFinalizedDao.FinalizedUpdater updater = dao.finalizedUpdater()) {
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
