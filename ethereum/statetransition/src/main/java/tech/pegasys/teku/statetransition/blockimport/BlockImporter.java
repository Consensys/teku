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

package tech.pegasys.teku.statetransition.blockimport;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Optional;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.data.BlockProcessingRecord;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.teku.statetransition.events.block.ProposedBlockEvent;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.events.Subscribers;

public class BlockImporter {
  private static final Logger LOG = LogManager.getLogger();
  private final RecentChainData recentChainData;
  private final ForkChoice forkChoice;
  private final EventBus eventBus;

  private final Subscribers<VerifiedBlockOperationsListener<Attestation>> attestationSubscribers =
      Subscribers.create(true);
  private final Subscribers<VerifiedBlockOperationsListener<AttesterSlashing>>
      attesterSlashingSubscribers = Subscribers.create(true);
  private final Subscribers<VerifiedBlockOperationsListener<ProposerSlashing>>
      proposerSlashingSubscribers = Subscribers.create(true);
  private final Subscribers<VerifiedBlockOperationsListener<SignedVoluntaryExit>>
      voluntaryExitSubscribers = Subscribers.create(true);

  public BlockImporter(
      final RecentChainData recentChainData, final ForkChoice forkChoice, final EventBus eventBus) {
    this.recentChainData = recentChainData;
    this.forkChoice = forkChoice;
    this.eventBus = eventBus;
    eventBus.register(this);
  }

  @CheckReturnValue
  public SafeFuture<BlockImportResult> importBlock(SignedBeaconBlock block) {
    if (recentChainData.containsBlock(block.getMessage().hash_tree_root())) {
      LOG.trace(
          "Importing known block {}.  Return successful result without re-processing.",
          () -> formatBlock(block));
      return SafeFuture.completedFuture(BlockImportResult.knownBlock(block));
    }

    return recentChainData
        .retrieveBlockState(block.getParent_root())
        .thenCompose(preState -> forkChoice.onBlock(block, preState))
        .thenApply(
            result -> {
              if (!result.isSuccessful()) {
                LOG.trace(
                    "Failed to import block for reason {}: {}",
                    result::getFailureReason,
                    () -> formatBlock(block));
                return result;
              }
              LOG.trace("Successfully imported block {}", () -> formatBlock(block));

              final Optional<BlockProcessingRecord> record = result.getBlockProcessingRecord();
              eventBus.post(new ImportedBlockEvent(block));
              notifyBlockOperationSubscribers(block);
              record.ifPresent(eventBus::post);

              return result;
            })
        .exceptionally(
            (e) -> {
              LOG.error("Internal error while importing block: {}", formatBlock(block), e);
              return BlockImportResult.internalError(e);
            });
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onBlockProposed(final ProposedBlockEvent blockProposedEvent) {
    LOG.trace("Preparing to import proposed block: {}", formatBlock(blockProposedEvent.getBlock()));
    importBlock(blockProposedEvent.getBlock())
        .thenAccept(
            (result) -> {
              if (result.isSuccessful()) {
                LOG.trace(
                    "Successfully imported proposed block: {}",
                    formatBlock(blockProposedEvent.getBlock()));
              } else {
                LOG.error(
                    "Failed to import proposed block for reason "
                        + result.getFailureReason()
                        + ": "
                        + formatBlock(blockProposedEvent.getBlock()),
                    result.getFailureCause().orElse(null));
              }
            })
        .reportExceptions();
  }

  private void notifyBlockOperationSubscribers(SignedBeaconBlock block) {
    attestationSubscribers.deliver(
        VerifiedBlockOperationsListener::onOperationsFromBlock,
        block.getMessage().getBody().getAttestations());
    attesterSlashingSubscribers.deliver(
        VerifiedBlockOperationsListener::onOperationsFromBlock,
        block.getMessage().getBody().getAttester_slashings());
    proposerSlashingSubscribers.deliver(
        VerifiedBlockOperationsListener::onOperationsFromBlock,
        block.getMessage().getBody().getProposer_slashings());
    voluntaryExitSubscribers.deliver(
        VerifiedBlockOperationsListener::onOperationsFromBlock,
        block.getMessage().getBody().getVoluntary_exits());
  }

  public void subscribeToVerifiedBlockAttestations(
      VerifiedBlockOperationsListener<Attestation> verifiedBlockAttestationsListener) {
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

  private String formatBlock(final SignedBeaconBlock block) {
    return LogFormatter.formatBlock(block.getSlot(), block.getRoot());
  }
}
