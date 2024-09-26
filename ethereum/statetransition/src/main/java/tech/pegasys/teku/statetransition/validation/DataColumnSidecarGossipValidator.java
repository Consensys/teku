/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.config.Constants.BEST_CASE_NON_FINALIZED_EPOCHS;
import static tech.pegasys.teku.spec.config.Constants.VALID_BLOCK_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import com.google.common.annotations.VisibleForTesting;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;

/**
 * This class supposed to implement gossip validation rules as per <a
 * href="https://github.com/ethereum/consensus-specs/blob/peer-das/specs/_features/eip7594/p2p-interface.md#data_column_sidecar_subnet_id">spec</a>
 */
public class DataColumnSidecarGossipValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Set<SlotProposerIndexAndColumnIndex> receivedValidDataColumnSidecarInfoSet;
  private final Set<InclusionProofInfo> validInclusionProofInfoSet;
  private final Set<Bytes32> validSignedBlockHeaders;
  private final GossipValidationHelper gossipValidationHelper;
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots;
  private final MiscHelpersEip7594 miscHelpersEip7594;
  private final KZG kzg;
  private final Counter totalDataColumnSidecarsProcessingRequestsCounter;
  private final Counter totalDataColumnSidecarsProcessingSuccessesCounter;

  public static DataColumnSidecarGossipValidator create(
      final Spec spec,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final GossipValidationHelper validationHelper,
      final MiscHelpersEip7594 miscHelpersEip7594,
      final KZG kzg,
      final MetricsSystem metricsSystem) {

    final Optional<Integer> maybeNumberOfColumns = spec.getNumberOfDataColumns();

    final int validInfoSize = VALID_BLOCK_SET_SIZE * maybeNumberOfColumns.orElse(1);
    // It's not fatal if we miss something and we don't need finalized data
    final int validSignedBlockHeadersSize =
        spec.getGenesisSpec().getSlotsPerEpoch() * BEST_CASE_NON_FINALIZED_EPOCHS;

    return new DataColumnSidecarGossipValidator(
        spec,
        invalidBlockRoots,
        validationHelper,
        miscHelpersEip7594,
        kzg,
        metricsSystem,
        LimitedSet.createSynchronized(validInfoSize),
        LimitedSet.createSynchronized(validSignedBlockHeadersSize),
        LimitedSet.createSynchronized(validSignedBlockHeadersSize));
  }

  @VisibleForTesting
  public Set<SlotProposerIndexAndColumnIndex> getReceivedValidDataColumnSidecarInfoSet() {
    return receivedValidDataColumnSidecarInfoSet;
  }

  private DataColumnSidecarGossipValidator(
      final Spec spec,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final GossipValidationHelper gossipValidationHelper,
      final MiscHelpersEip7594 miscHelpersEip7594,
      final KZG kzg,
      final MetricsSystem metricsSystem,
      final Set<SlotProposerIndexAndColumnIndex> receivedValidDataColumnSidecarInfoSet,
      final Set<InclusionProofInfo> validInclusionProofInfoSet,
      final Set<Bytes32> validSignedBlockHeaders) {
    this.spec = spec;
    this.invalidBlockRoots = invalidBlockRoots;
    this.gossipValidationHelper = gossipValidationHelper;
    this.miscHelpersEip7594 = miscHelpersEip7594;
    this.kzg = kzg;
    this.receivedValidDataColumnSidecarInfoSet = receivedValidDataColumnSidecarInfoSet;
    this.totalDataColumnSidecarsProcessingRequestsCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "data_column_sidecar_processing_requests_total",
            "Total number of data column sidecars submitted for processing");
    this.totalDataColumnSidecarsProcessingSuccessesCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "data_column_sidecar_processing_successes_total",
            "Total number of data column sidecars verified for gossip");
    this.validInclusionProofInfoSet = validInclusionProofInfoSet;
    this.validSignedBlockHeaders = validSignedBlockHeaders;
  }

  public SafeFuture<InternalValidationResult> validate(final DataColumnSidecar dataColumnSidecar) {
    final BeaconBlockHeader blockHeader =
        dataColumnSidecar.getSignedBeaconBlockHeader().getMessage();

    totalDataColumnSidecarsProcessingRequestsCounter.inc();

    /*
     * [IGNORE] The sidecar is the first sidecar for the tuple (block_header.slot, block_header.proposer_index, sidecar.index) with valid header signature, sidecar inclusion proof, and kzg proof.
     */
    if (!isFirstValidForSlotProposerIndexAndColumnIndex(dataColumnSidecar, blockHeader)) {
      LOG.trace(
          "DataColumnSidecar is not the first valid for its slot and index. It will be dropped");
      return completedFuture(InternalValidationResult.IGNORE);
    }

    /*
     * [REJECT] The sidecar's index is consistent with NUMBER_OF_COLUMNS -- i.e. sidecar.index < NUMBER_OF_COLUMNS.
     */
    final Optional<Integer> maybeNumberOfColumns = spec.getNumberOfDataColumns();
    if (maybeNumberOfColumns.isEmpty()) {
      return completedFuture(reject("DataColumnSidecar's slot is pre-EIP7594"));
    }
    if (!dataColumnSidecar.getIndex().isLessThan(maybeNumberOfColumns.get())) {
      return completedFuture(reject("DataColumnSidecar index not less than number of columns."));
    }

    /*
     * [REJECT] The sidecar is for the correct subnet -- i.e. compute_subnet_for_data_column_sidecar(sidecar.index) == subnet_id.
     * Already done in {@link tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.DataColumnSidecarTopicHandler.TopicSubnetIdAwareOperationProcessor#process(
     *        DataColumnSidecar, Optional<UInt64>)}
     */

    /*
     * [IGNORE] The sidecar is not from a future slot (with a MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. validate that block_header.slot <= current_slot (a client MAY queue future sidecars for processing at the appropriate slot).
     */
    if (gossipValidationHelper.isSlotFromFuture(blockHeader.getSlot())) {
      LOG.trace("DataColumnSidecar is from the future. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    /*
     * [IGNORE] The sidecar is from a slot greater than the latest finalized slot -- i.e. validate that block_header.slot > compute_start_slot_at_epoch(state.finalized_checkpoint.epoch)
     */
    if (gossipValidationHelper.isSlotFinalized(blockHeader.getSlot())) {
      LOG.trace("DataColumnSidecar is too old (slot already finalized)");
      return completedFuture(InternalValidationResult.IGNORE);
    }

    // Optimization: If we have already completely verified DataColumnSidecar with the same
    // SignedBlockHeader, we can skip most steps and jump to shortened validation
    if (validSignedBlockHeaders.contains(
        dataColumnSidecar.getSignedBeaconBlockHeader().hashTreeRoot())) {
      return validateDataColumnSidecarWithKnownValidHeader(dataColumnSidecar, blockHeader);
    }

    /*
     * [REJECT] The proposer signature of sidecar.signed_block_header, is valid with respect to the block_header.proposer_index pubkey.
     *
     * Verified later after all checks not involving state are passed
     */

    /*
     * [IGNORE] The sidecar's block's parent (defined by block_header.parent_root) has been seen (via both gossip and non-gossip sources) (a client MAY queue sidecars for processing once the parent block is retrieved).
     */
    if (!gossipValidationHelper.isBlockAvailable(blockHeader.getParentRoot())) {
      LOG.trace(
          "DataColumnSidecar block header parent block is not available. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }
    final Optional<UInt64> maybeParentBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(blockHeader.getParentRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      LOG.trace(
          "DataColumnSidecar block header parent block does not exist. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }
    final UInt64 parentBlockSlot = maybeParentBlockSlot.get();

    /*
     * [REJECT] The sidecar's block's parent (defined by block_header.parent_root) passes validation.
     */
    if (invalidBlockRoots.containsKey(blockHeader.getParentRoot())) {
      return completedFuture(reject("DataColumnSidecar block header has an invalid parent root"));
    }

    /*
     * [REJECT] The sidecar is from a higher slot than the sidecar's block's parent (defined by block_header.parent_root).
     */
    if (!blockHeader.getSlot().isGreaterThan(parentBlockSlot)) {
      return completedFuture(reject("Parent block is after DataColumnSidecar slot."));
    }

    /*
     * [REJECT] The current finalized_checkpoint is an ancestor of the sidecar's block -- i.e. get_checkpoint_block(store, block_header.parent_root, store.finalized_checkpoint.epoch) == store.finalized_checkpoint.root.
     */
    if (!gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
        blockHeader.getSlot(), blockHeader.getParentRoot())) {
      return completedFuture(
          reject("DataColumnSidecar block header does not descend from finalized checkpoint"));
    }

    /*
     * [REJECT] The sidecar's kzg_commitments field inclusion proof is valid as verified by verify_data_column_sidecar_inclusion_proof(sidecar).
     */
    if (!verifyDataColumnSidecarInclusionProof(dataColumnSidecar)) {
      return completedFuture(reject("DataColumnSidecar inclusion proof validation failed"));
    }

    /*
     * [REJECT] The sidecar's column data is valid as verified by verify_data_column_sidecar_kzg_proofs(sidecar).
     */
    if (!miscHelpersEip7594.verifyDataColumnSidecarKzgProof(kzg, dataColumnSidecar)) {
      return completedFuture(reject("DataColumnSidecar does not pass kzg validation"));
    }

    return gossipValidationHelper
        .getParentStateInBlockEpoch(
            parentBlockSlot, blockHeader.getParentRoot(), blockHeader.getSlot())
        .thenApply(
            maybePostState -> {
              /*
               * [REJECT] [REJECT] The sidecar is proposed by the expected proposer_index for the block's slot in the context of the current shuffling (defined by block_header.parent_root/block_header.slot).
               *
               * If the proposer_index cannot immediately be verified against the expected shuffling, the sidecar MAY be queued for later processing while proposers for the block's branch are calculated -- in such a case do not REJECT, instead IGNORE this message.
               */
              if (maybePostState.isEmpty()) {
                LOG.trace(
                    "DataColumnSidecar block header state wasn't available. Must have been pruned by finalized.");
                return InternalValidationResult.IGNORE;
              }
              final BeaconState postState = maybePostState.get();
              if (!gossipValidationHelper.isProposerTheExpectedProposer(
                  blockHeader.getProposerIndex(), blockHeader.getSlot(), postState)) {
                return reject(
                    "DataColumnSidecar block header proposed by incorrect proposer (%s).",
                    blockHeader.getProposerIndex());
              }

              /*
               * [REJECT] The proposer signature of sidecar.signed_block_header, is valid with respect to the block_header.proposer_index pubkey.
               */
              if (!verifyBlockHeaderSignature(
                  postState, dataColumnSidecar.getSignedBeaconBlockHeader())) {
                return reject("DataColumnSidecar block header signature is invalid.");
              }

              /*
               * Checking it again at the very end because whole method is not synchronized
               *
               * [IGNORE] The sidecar is the first sidecar for the tuple (block_header.slot, block_header.proposer_index, sidecar.index) with valid header signature, sidecar inclusion proof, and kzg proof.
               */
              if (!receivedValidDataColumnSidecarInfoSet.add(
                  new SlotProposerIndexAndColumnIndex(
                      blockHeader.getSlot(),
                      blockHeader.getProposerIndex(),
                      dataColumnSidecar.getIndex()))) {
                return ignore(
                    "DataColumnSidecar is not the first valid for its slot and index. It will be dropped.");
              }

              validSignedBlockHeaders.add(
                  dataColumnSidecar.getSignedBeaconBlockHeader().hashTreeRoot());
              validInclusionProofInfoSet.add(
                  new InclusionProofInfo(
                      dataColumnSidecar.getSszKZGCommitments().hashTreeRoot(),
                      dataColumnSidecar.getKzgCommitmentsInclusionProof().hashTreeRoot(),
                      dataColumnSidecar.getBlockBodyRoot()));

              totalDataColumnSidecarsProcessingSuccessesCounter.inc();
              return ACCEPT;
            });
  }

  private SafeFuture<InternalValidationResult> validateDataColumnSidecarWithKnownValidHeader(
      final DataColumnSidecar dataColumnSidecar, final BeaconBlockHeader blockHeader) {

    /*
     * [REJECT] The sidecar's kzg_commitments field inclusion proof is valid as verified by verify_data_column_sidecar_inclusion_proof(sidecar).
     */
    if (!verifyDataColumnSidecarInclusionProof(dataColumnSidecar)) {
      return completedFuture(reject("DataColumnSidecar inclusion proof validation failed"));
    }

    /*
     * [REJECT] The sidecar's column data is valid as verified by verify_data_column_sidecar_kzg_proofs(sidecar).
     */
    if (!miscHelpersEip7594.verifyDataColumnSidecarKzgProof(kzg, dataColumnSidecar)) {
      return completedFuture(reject("DataColumnSidecar does not pass kzg validation"));
    }

    // This can be changed between two received DataColumnSidecars from one block, so checking
    /*
     * [REJECT] The current finalized_checkpoint is an ancestor of the sidecar's block -- i.e. get_checkpoint_block(store, block_header.parent_root, store.finalized_checkpoint.epoch) == store.finalized_checkpoint.root.
     */
    if (!gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
        blockHeader.getSlot(), blockHeader.getParentRoot())) {
      return completedFuture(
          reject("DataColumnSidecar block header does not descend from finalized checkpoint"));
    }

    /*
     * [IGNORE] The sidecar is the first sidecar for the tuple (block_header.slot, block_header.proposer_index, sidecar.index) with valid header signature, sidecar inclusion proof, and kzg proof.
     */
    if (!receivedValidDataColumnSidecarInfoSet.add(
        new SlotProposerIndexAndColumnIndex(
            blockHeader.getSlot(), blockHeader.getProposerIndex(), dataColumnSidecar.getIndex()))) {
      return SafeFuture.completedFuture(
          ignore(
              "DataColumnSidecar is not the first valid for its slot and index. It will be dropped."));
    }

    totalDataColumnSidecarsProcessingSuccessesCounter.inc();

    return SafeFuture.completedFuture(ACCEPT);
  }

  private boolean verifyDataColumnSidecarInclusionProof(final DataColumnSidecar dataColumnSidecar) {
    if (validInclusionProofInfoSet.contains(
        new InclusionProofInfo(
            dataColumnSidecar.getSszKZGCommitments().hashTreeRoot(),
            dataColumnSidecar.getKzgCommitmentsInclusionProof().hashTreeRoot(),
            dataColumnSidecar.getBlockBodyRoot()))) {
      return true;
    }

    return miscHelpersEip7594.verifyDataColumnSidecarInclusionProof(dataColumnSidecar);
  }

  private boolean verifyBlockHeaderSignature(
      final BeaconState state, final SignedBeaconBlockHeader signedBlockHeader) {
    final Bytes32 domain =
        spec.getDomain(
            Domain.BEACON_PROPOSER,
            spec.getCurrentEpoch(state),
            state.getFork(),
            state.getGenesisValidatorsRoot());
    final Bytes signingRoot = spec.computeSigningRoot(signedBlockHeader.getMessage(), domain);

    return gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
        signingRoot,
        signedBlockHeader.getMessage().getProposerIndex(),
        signedBlockHeader.getSignature(),
        state);
  }

  private boolean isFirstValidForSlotProposerIndexAndColumnIndex(
      final DataColumnSidecar dataColumnSidecar, final BeaconBlockHeader blockHeader) {
    return !receivedValidDataColumnSidecarInfoSet.contains(
        new SlotProposerIndexAndColumnIndex(
            blockHeader.getSlot(), blockHeader.getProposerIndex(), dataColumnSidecar.getIndex()));
  }

  record SlotProposerIndexAndColumnIndex(UInt64 slot, UInt64 proposerIndex, UInt64 columnIndex) {}

  record InclusionProofInfo(
      Bytes32 commitmentsRoot, Bytes32 inclusionProofRoot, Bytes32 bodyRoot) {}
}
