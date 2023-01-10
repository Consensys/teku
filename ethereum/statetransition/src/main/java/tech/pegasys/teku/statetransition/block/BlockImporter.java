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

package tech.pegasys.teku.statetransition.block;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;

public class BlockImporter {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final BlockImportNotifications blockImportNotifications;
  private final RecentChainData recentChainData;
  private final ForkChoice forkChoice;
  private final WeakSubjectivityValidator weakSubjectivityValidator;
  private final ExecutionLayerChannel executionLayer;

  private final Subscribers<VerifiedBlockAttestationListener> attestationSubscribers =
      Subscribers.create(true);
  private final Subscribers<VerifiedBlockOperationsListener<AttesterSlashing>>
      attesterSlashingSubscribers = Subscribers.create(true);
  private final Subscribers<VerifiedBlockOperationsListener<ProposerSlashing>>
      proposerSlashingSubscribers = Subscribers.create(true);
  private final Subscribers<VerifiedBlockOperationsListener<SignedVoluntaryExit>>
      voluntaryExitSubscribers = Subscribers.create(true);

  private final AtomicReference<CheckpointState> latestFinalizedCheckpointState =
      new AtomicReference<>(null);

  public BlockImporter(
      final Spec spec,
      final BlockImportNotifications blockImportNotifications,
      final RecentChainData recentChainData,
      final ForkChoice forkChoice,
      final WeakSubjectivityValidator weakSubjectivityValidator,
      final ExecutionLayerChannel executionLayer) {
    this.spec = spec;
    this.blockImportNotifications = blockImportNotifications;
    this.recentChainData = recentChainData;
    this.forkChoice = forkChoice;
    this.weakSubjectivityValidator = weakSubjectivityValidator;
    this.executionLayer = executionLayer;
  }

  @CheckReturnValue
  public SafeFuture<BlockImportResult> importBlock(final SignedBeaconBlock block) {
    return importBlock(block, Optional.empty());
  }

  @CheckReturnValue
  public SafeFuture<BlockImportResult> importBlock(
      final SignedBeaconBlock block,
      final Optional<BlockImportPerformance> blockImportPerformance) {

    final Optional<Boolean> knownOptimistic = recentChainData.isBlockOptimistic(block.getRoot());
    if (knownOptimistic.isPresent()) {
      LOG.trace(
          "Importing known block {}.  Return successful result without re-processing.",
          block::toLogString);
      return SafeFuture.completedFuture(BlockImportResult.knownBlock(block, knownOptimistic.get()));
    }

    if (!weakSubjectivityValidator.isBlockValid(block, getForkChoiceStrategy())) {
      EventLogger.EVENT_LOG.weakSubjectivityFailedEvent(block.getRoot(), block.getSlot());
      return SafeFuture.completedFuture(BlockImportResult.FAILED_WEAK_SUBJECTIVITY_CHECKS);
    }

    return validateWeakSubjectivityPeriod()
        .thenCompose(__ -> forkChoice.onBlock(block, blockImportPerformance, executionLayer))
        .thenApply(
            result -> {
              if (!result.isSuccessful()) {
                LOG.trace(
                    "Failed to import block for reason {}: {}",
                    result::getFailureReason,
                    block::toLogString);
                return result;
              }
              LOG.trace("Successfully imported block {}", block::toLogString);

              blockImportNotifications.onBlockImported(block);

              // Notify operation pools to remove operations only
              // if the block is on our canonical chain
              if (result.isBlockOnCanonicalChain()) {
                notifyBlockOperationSubscribers(block);
              }

              return result;
            })
        .exceptionally(
            (e) -> {
              final String internalErrorMessage =
                  String.format(
                      "Internal error while importing block: %s. Block content: %s",
                      block.toLogString(), getBlockContent(block));
              LOG.error(internalErrorMessage, e);
              return BlockImportResult.internalError(e);
            });
  }

  private SafeFuture<?> validateWeakSubjectivityPeriod() {
    return getLatestCheckpointState()
        .thenCombine(
            SafeFuture.of(() -> recentChainData.getCurrentSlot().orElseThrow()),
            (finalizedCheckpointState, currentSlot) -> {
              // While the node is online, we can defer to fork-choice to choose the right chain.
              // If we have a recent chain head, skip validation since it appears we're online and
              // processing new blocks.
              final Optional<UInt64> wsPeriodInSlots =
                  weakSubjectivityValidator
                      .getWSPeriod(finalizedCheckpointState)
                      .map(epochs -> epochs.times(spec.getSlotsPerEpoch(currentSlot)));
              final UInt64 headSlot = recentChainData.getHeadSlot();
              if (wsPeriodInSlots
                  .map(wsp -> headSlot.plus(wsp).isGreaterThanOrEqualTo(currentSlot))
                  .orElse(false)) {
                return null;
              }

              weakSubjectivityValidator.validateLatestFinalizedCheckpoint(
                  finalizedCheckpointState, currentSlot);
              return null;
            });
  }

  @VisibleForTesting
  SafeFuture<CheckpointState> getLatestCheckpointState() {
    final CheckpointState finalizedCheckpoint = latestFinalizedCheckpointState.get();
    if (finalizedCheckpoint != null
        && recentChainData
            .getStore()
            .getLatestFinalized()
            .getRoot()
            .equals(finalizedCheckpoint.getRoot())) {
      return SafeFuture.completedFuture(finalizedCheckpoint);
    }

    return SafeFuture.of(() -> recentChainData.getStore().retrieveFinalizedCheckpointAndState())
        .thenApply(
            updatedCheckpoint ->
                latestFinalizedCheckpointState.updateAndGet(
                    curVal ->
                        Objects.equals(curVal, finalizedCheckpoint) ? updatedCheckpoint : curVal));
  }

  private void notifyBlockOperationSubscribers(SignedBeaconBlock block) {
    attestationSubscribers.forEach(
        listener ->
            listener.onOperationsFromBlock(
                block.getSlot(), block.getMessage().getBody().getAttestations()));
    attesterSlashingSubscribers.deliver(
        VerifiedBlockOperationsListener::onOperationsFromBlock,
        block.getMessage().getBody().getAttesterSlashings());
    proposerSlashingSubscribers.deliver(
        VerifiedBlockOperationsListener::onOperationsFromBlock,
        block.getMessage().getBody().getProposerSlashings());
    voluntaryExitSubscribers.deliver(
        VerifiedBlockOperationsListener::onOperationsFromBlock,
        block.getMessage().getBody().getVoluntaryExits());
  }

  public void subscribeToVerifiedBlockAttestations(
      VerifiedBlockAttestationListener verifiedBlockAttestationsListener) {
    attestationSubscribers.subscribe(verifiedBlockAttestationsListener);
  }

  public void subscribeToVerifiedBlockAttesterSlashings(
      VerifiedBlockOperationsListener<AttesterSlashing> verifiedBlockAttesterSlashingsListener) {
    attesterSlashingSubscribers.subscribe(verifiedBlockAttesterSlashingsListener);
  }

  public void subscribeToVerifiedBlockProposerSlashings(
      VerifiedBlockOperationsListener<ProposerSlashing> verifiedBlockProposerSlashingsListener) {
    proposerSlashingSubscribers.subscribe(verifiedBlockProposerSlashingsListener);
  }

  public void subscribeToVerifiedBlockVoluntaryExits(
      VerifiedBlockOperationsListener<SignedVoluntaryExit> verifiedBlockVoluntaryExitsListener) {
    voluntaryExitSubscribers.subscribe(verifiedBlockVoluntaryExitsListener);
  }

  private String getBlockContent(final SignedBeaconBlock block) {
    return block.sszSerialize().toHexString();
  }

  private ReadOnlyForkChoiceStrategy getForkChoiceStrategy() {
    return recentChainData
        .getForkChoiceStrategy()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Attempting to perform fork choice operations before store has been initialized"));
  }

  public interface VerifiedBlockAttestationListener {
    void onOperationsFromBlock(UInt64 slot, SszList<Attestation> attestations);
  }
}
