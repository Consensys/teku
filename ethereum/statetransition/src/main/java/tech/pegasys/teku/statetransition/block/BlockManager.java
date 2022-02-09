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

package tech.pegasys.teku.statetransition.block;

import static tech.pegasys.teku.infrastructure.logging.LogFormatter.formatBlock;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.datastructures.blocks.ReceivedBlockListener;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason.Validity;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockManager extends Service
    implements SlotEventsChannel, BlockImportChannel, BlockImportNotifications {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final BlockImporter blockImporter;
  private final PendingPool<SignedBeaconBlock> pendingBlocks;
  private final BlockValidator validator;

  private final FutureItems<SignedBeaconBlock> futureBlocks;
  private final Map<Bytes32, Validity> invalidBlockRoots = LimitedMap.create(500);
  private final Subscribers<ReceivedBlockListener> receivedBlockSubscribers =
      Subscribers.create(true);

  public BlockManager(
      final RecentChainData recentChainData,
      final BlockImporter blockImporter,
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final FutureItems<SignedBeaconBlock> futureBlocks,
      final BlockValidator validator) {
    this.recentChainData = recentChainData;
    this.blockImporter = blockImporter;
    this.pendingBlocks = pendingBlocks;
    this.futureBlocks = futureBlocks;
    this.validator = validator;
  }

  @Override
  public SafeFuture<?> doStart() {
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<BlockImportResult> importBlock(final SignedBeaconBlock block) {
    LOG.trace("Preparing to import block: {}", () -> formatBlock(block.getSlot(), block.getRoot()));
    return doImportBlock(block);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<InternalValidationResult> validateAndImportBlock(
      final SignedBeaconBlock block) {
    SafeFuture<InternalValidationResult> validationResult =
        validator.validate(block, this::getStrictBlockInvalidity);
    validationResult.thenAccept(
        result -> {
          if (result.code().equals(ValidationResultCode.ACCEPT)
              || result.code().equals(ValidationResultCode.SAVE_FOR_FUTURE)) {
            importBlock(block).finish(err -> LOG.error("Failed to process received block.", err));
          }
        });
    return validationResult;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    pendingBlocks.onSlot(slot);
    futureBlocks.onSlot(slot);
    futureBlocks.prune(slot).forEach(this::importBlockIgnoringResult);
  }

  public void subscribeToReceivedBlocks(ReceivedBlockListener receivedBlockListener) {
    receivedBlockSubscribers.subscribe(receivedBlockListener);
  }

  private void notifyReceivedBlockSubscribers(SignedBeaconBlock signedBeaconBlock) {
    receivedBlockSubscribers.forEach(s -> s.accept(signedBeaconBlock));
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block) {
    // Check if any pending blocks can now be imported
    final Bytes32 blockRoot = block.getRoot();
    pendingBlocks.remove(block);
    final List<SignedBeaconBlock> children = pendingBlocks.getItemsDependingOn(blockRoot, false);
    children.forEach(pendingBlocks::remove);
    children.forEach(this::importBlockIgnoringResult);
  }

  private void importBlockIgnoringResult(final SignedBeaconBlock block) {
    doImportBlock(block).reportExceptions();
  }

  private SafeFuture<BlockImportResult> doImportBlock(final SignedBeaconBlock block) {
    notifyReceivedBlockSubscribers(block);
    if (!shouldImportBlock(block)) {
      return SafeFuture.completedFuture(BlockImportResult.knownBlock(block));
    }

    return blockImporter
        .importBlock(block)
        .thenPeek(
            result -> {
              if (result.isSuccessful()) {
                LOG.trace("Imported block: {}", block);
              } else {
                final FailureReason failureReason = result.getFailureReason();
                switch (failureReason) {
                  case UNKNOWN_PARENT:
                    // Add to the pending pool so it is triggered once the parent is imported
                    pendingBlocks.add(block);
                    // Check if the parent was imported while we were trying to import this block
                    // and if
                    // so, remove from the pendingPool again and process now We must add the block
                    // to
                    // the pending pool before this check happens to avoid race conditions between
                    // performing the check and the parent importing.
                    if (recentChainData.containsBlock(block.getParentRoot())) {
                      pendingBlocks.remove(block);
                      importBlockIgnoringResult(block);
                    }
                    break;
                  case BLOCK_IS_FROM_FUTURE:
                    futureBlocks.add(block);
                    break;
                  case FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING:
                    LOG.warn("Unable to import block: Execution Client is still syncing");
                    break;
                  case FAILED_EXECUTION_PAYLOAD_EXECUTION:
                    LOG.error(
                        "Unable to import block: Execution Client communication error.",
                        result.getFailureCause().orElse(null));
                    break;
                  default:
                    LOG.trace("Unable to import block for reason {}: {}", failureReason, block);
                }

                switch (failureReason.validity) {
                  case OPTIMISTIC:
                  case INVALID:
                    applyFailureValidity(block, failureReason.validity);
                    break;
                  default:
                }
              }
            });
  }

  private boolean shouldImportBlock(final SignedBeaconBlock block) {
    if (blockIsKnown(block)) {
      return false;
    }
    final Optional<Validity> failureValidity = getBlockInvalidity(block);
    failureValidity.ifPresent(validity -> applyFailureValidity(block, validity));
    return failureValidity.map(validity -> !validity.equals(Validity.INVALID)).orElse(true);
  }

  private boolean blockIsKnown(final SignedBeaconBlock block) {
    return pendingBlocks.contains(block)
        || futureBlocks.contains(block)
        || recentChainData.containsBlock(block.getRoot());
  }

  private Optional<Validity> getBlockInvalidity(final SignedBeaconBlock block) {
    return Optional.ofNullable(invalidBlockRoots.get(block.getRoot()))
        .or(() -> Optional.ofNullable(invalidBlockRoots.get(block.getParentRoot())));
  }

  private Optional<Validity> getStrictBlockInvalidity(final Bytes32 blockRoot) {
    return Optional.ofNullable(invalidBlockRoots.get(blockRoot));
  }

  private void applyFailureValidity(final SignedBeaconBlock block, final Validity failureValidity) {
    final Bytes32 blockRoot = block.getRoot();
    final Set<SignedBeaconBlock> blocksToProcess = new HashSet<>();
    blocksToProcess.add(block);
    blocksToProcess.addAll(pendingBlocks.getItemsDependingOn(blockRoot, true));

    final Consumer<SignedBeaconBlock> processor =
        failureValidity == Validity.INVALID
            ? blockToDrop -> {
              addInvalidBlock(blockToDrop, failureValidity);
              pendingBlocks.remove(blockToDrop);
            }
            : blockToProcess -> addInvalidBlock(blockToProcess, failureValidity);

    blocksToProcess.forEach(processor);
  }

  private void addInvalidBlock(
      final SignedBeaconBlock failedBlock, final Validity failureValidity) {
    invalidBlockRoots.merge(
        failedBlock.getMessage().hashTreeRoot(),
        failureValidity,
        (oldValidity, newValidity) ->
            oldValidity.ordinal() < newValidity.ordinal() ? oldValidity : newValidity);
  }
}
