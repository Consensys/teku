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
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarTrackingKey;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil.InclusionProofInfo;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationResult;

/**
 * This class supposed to implement gossip validation rules as per <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/fulu/p2p-interface.md#data_column_sidecar_subnet_id">spec</a>
 */
public class DataColumnSidecarGossipValidator {

  private static final Logger LOG = LogManager.getLogger();
  public static final BiFunction<MetricsSystem, TimeProvider, MetricsHistogram>
      DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_HISTOGRAM =
          (metricsSystem, timeProvider) ->
              new MetricsHistogram(
                  metricsSystem,
                  timeProvider,
                  TekuMetricCategory.BEACON,
                  "data_column_sidecar_inclusion_proof_verification_seconds",
                  "Time taken to verify data column sidecar inclusion proof",
                  0.001,
                  0.002,
                  0.003,
                  0.004,
                  0.005,
                  0.01,
                  0.015,
                  0.02,
                  0.025,
                  0.03,
                  0.04,
                  0.05,
                  0.1,
                  0.5,
                  1.0);
  public static final BiFunction<MetricsSystem, TimeProvider, MetricsHistogram>
      DATA_COLUMN_SIDECAR_KZG_BATCH_VERIFICATION_HISTOGRAM =
          (metricsSystem, timeProvider) ->
              new MetricsHistogram(
                  metricsSystem,
                  timeProvider,
                  TekuMetricCategory.BEACON,
                  "kzg_verification_data_column_batch_seconds",
                  "Runtime of batched data column kzg verification",
                  0.005,
                  0.01,
                  0.025,
                  0.05,
                  0.075,
                  0.1,
                  0.25,
                  0.5,
                  0.75,
                  1.0,
                  1.25,
                  1.5,
                  1.75,
                  2.0);

  private final Spec spec;
  private final Set<DataColumnSidecarTrackingKey> receivedValidDataColumnSidecarInfoSet;
  private final Set<InclusionProofInfo> validInclusionProofInfoSet;
  private final Set<Bytes32> validSignedBlockHeaders;
  private final GossipValidationHelper gossipValidationHelper;
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots;
  private final Counter totalDataColumnSidecarsProcessingRequestsCounter;
  private final Counter totalDataColumnSidecarsProcessingSuccessesCounter;
  private final LabelledMetric<Counter> totalDataColumnSidecarsProcessingValidatedCounter;
  private final MetricsHistogram dataColumnSidecarInclusionProofVerificationTimeSeconds;
  private final MetricsHistogram dataColumnSidecarKzgBatchVerificationTimeSeconds;

  public static DataColumnSidecarGossipValidator create(
      final Spec spec,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final GossipValidationHelper gossipValidationHelper,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider) {

    final Optional<Integer> maybeNumberOfColumns = spec.getNumberOfDataColumns();

    final int validInfoSize = VALID_BLOCK_SET_SIZE * maybeNumberOfColumns.orElse(1);
    // It's not fatal if we miss something and we don't need finalized data
    final int validSignedBlockHeadersSize =
        spec.getGenesisSpec().getSlotsPerEpoch() * BEST_CASE_NON_FINALIZED_EPOCHS;

    return new DataColumnSidecarGossipValidator(
        spec,
        invalidBlockRoots,
        gossipValidationHelper,
        metricsSystem,
        timeProvider,
        LimitedSet.createSynchronized(validInfoSize),
        LimitedSet.createSynchronized(validSignedBlockHeadersSize),
        LimitedSet.createSynchronized(validSignedBlockHeadersSize));
  }

  @VisibleForTesting
  public Set<DataColumnSidecarTrackingKey> getReceivedValidDataColumnSidecarInfoSet() {
    return receivedValidDataColumnSidecarInfoSet;
  }

  private DataColumnSidecarGossipValidator(
      final Spec spec,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final GossipValidationHelper gossipValidationHelper,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final Set<DataColumnSidecarTrackingKey> receivedValidDataColumnSidecarInfoSet,
      final Set<InclusionProofInfo> validInclusionProofInfoSet,
      final Set<Bytes32> validSignedBlockHeaders) {
    this.spec = spec;
    this.invalidBlockRoots = invalidBlockRoots;
    this.gossipValidationHelper = gossipValidationHelper;
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

    totalDataColumnSidecarsProcessingValidatedCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            "data_column_sidecar_processing_validated_total",
            "Total number of data column sidecars validated. Includes a label validation_result.",
            "validation_result");

    this.dataColumnSidecarInclusionProofVerificationTimeSeconds =
        DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_HISTOGRAM.apply(
            metricsSystem, timeProvider);
    this.dataColumnSidecarKzgBatchVerificationTimeSeconds =
        DATA_COLUMN_SIDECAR_KZG_BATCH_VERIFICATION_HISTOGRAM.apply(metricsSystem, timeProvider);

    this.validInclusionProofInfoSet = validInclusionProofInfoSet;
    this.validSignedBlockHeaders = validSignedBlockHeaders;
  }

  public SafeFuture<InternalValidationResult> validate(final DataColumnSidecar dataColumnSidecar) {
    final UInt64 slot = dataColumnSidecar.getSlot();
    final SpecLogic specLogic = spec.atSlot(slot);
    final DataColumnSidecarUtil validationHelper = spec.getDataColumnSidecarUtil(slot);
    final Optional<BeaconBlockHeader> maybeBlockHeader =
        validationHelper.getBlockHeader(dataColumnSidecar);

    totalDataColumnSidecarsProcessingRequestsCounter.inc();

    return validateDataColumnSidecar(
        dataColumnSidecar, specLogic, validationHelper, maybeBlockHeader);
  }

  private SafeFuture<InternalValidationResult> validateDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar,
      final SpecLogic specLogic,
      final DataColumnSidecarUtil validationHelper,
      final Optional<BeaconBlockHeader> maybeBlockHeader) {

    /*
     * [REJECT] The sidecar is valid as verified by verify_data_column_sidecar(sidecar).
     *
     * Common to both Fulu and Gloas.
     */
    if (!validationHelper.verifyDataColumnSidecarStructure(specLogic, dataColumnSidecar)) {
      return SafeFuture.completedFuture(reject("DataColumnSidecar has invalid structure"));
    }

    /*
     * [IGNORE] The sidecar is the first sidecar for the tuple with valid cryptographic proofs.
     * - Fulu: (block_header.slot, block_header.proposer_index, sidecar.index)
     * - Gloas: (sidecar.beacon_block_root, sidecar.index)
     */
    if (!isFirstValidForTrackingKey(validationHelper, dataColumnSidecar)) {
      return completedFuture(
          ignore(
              "DataColumnSidecar is not the first valid for its tracking key. It will be dropped."));
    }

    /*
     * [REJECT] The sidecar is for the correct subnet -- i.e. compute_subnet_for_data_column_sidecar(sidecar.index) == subnet_id.
     * Already done in {@link tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.DataColumnSidecarTopicHandler.TopicSubnetIdAwareOperationProcessor#process(
     *        DataColumnSidecar, Optional<UInt64>)}
     */

    final DataColumnSidecarUtil dataColumnSidecarUtil =
        spec.getDataColumnSidecarUtil(dataColumnSidecar.getSlot());

    final Optional<DataColumnSidecarUtil.SlotInclusionGossipValidationResult>
        maybeSlotTimingValidationResult =
            dataColumnSidecarUtil.performSlotTimingValidation(
                dataColumnSidecar, gossipValidationHelper::isSlotFromFuture);

    if (maybeSlotTimingValidationResult.isPresent()) {
      return switch (maybeSlotTimingValidationResult.get()) {
        case IGNORE ->
            completedFuture(
                ignore("Unable to check DataColumnSidecar block header slot. Ignoring"));
        case SAVE_FOR_FUTURE ->
            completedFuture(
                saveForFuture(
                    "DataColumnSidecar block header slot is from the future. It will be saved for future processing"));
      };
    }

    final Optional<DataColumnSidecarUtil.SlotInclusionGossipValidationResult>
        maybeSlotFinalizationValidationResult =
            dataColumnSidecarUtil.performSlotFinalizationValidation(
                dataColumnSidecar, gossipValidationHelper::isSlotFinalized);

    if (maybeSlotFinalizationValidationResult.isPresent()) {
      return switch (maybeSlotFinalizationValidationResult.get()) {
        case IGNORE ->
            completedFuture(
                ignore(
                    "DataColumnSidecar is from a slot greater than the latest finalized slot. Ignoring"));
        case SAVE_FOR_FUTURE ->
            completedFuture(saveForFuture("DataColumnSidecar will be saved for future processing"));
      };
    }

    /*
     * Gloas-specific validations
     * [REJECT] The sidecar's slot matches the slot of the block with root beacon_block_root
     *
     * [REJECT] The hash of the sidecar's kzg_commitments matches the blob_kzg_commitments_root
     * in the corresponding builder's bid for sidecar.beacon_block_root.
     */

    final DataColumnSidecarValidationResult payloadRefResult =
        dataColumnSidecarUtil.validateExecutionPayloadReference(
            spec,
            dataColumnSidecar,
            gossipValidationHelper::getSlotForBlockRoot,
            beaconBlockRoot ->
                gossipValidationHelper
                    .getRecentlyValidatedSignedBlockByRoot(beaconBlockRoot)
                    .flatMap(
                        signedBeaconBlock ->
                            signedBeaconBlock
                                .getMessage()
                                .getBody()
                                .getOptionalSignedExecutionPayloadBid()
                                .map(
                                    signedExecutionPayloadBid ->
                                        signedExecutionPayloadBid
                                            .getMessage()
                                            .getBlobKzgCommitmentsRoot())));
    if (!payloadRefResult.isValid()) {
      return completedFuture(
          reject(payloadRefResult.getReason().orElse("Execution payload validation failed")));
    }

    /*
     * FULU
     * [IGNORE] The sidecar's block's parent (defined by block_header.parent_root) has been seen (via gossip or non-gossip sources)
     * (a client MAY queue sidecars for processing once the parent block is retrieved).
     *
     * GLOAS
     * [IGNORE] The sidecar's beacon_block_root has been seen via a valid signed execution payload bid.
     * A client MAY queue the sidecar for processing once the block is retrieved.
     */

    if (!dataColumnSidecarUtil.isBlockSeen(
        dataColumnSidecar, gossipValidationHelper::isBlockAvailable)) {
      LOG.trace(
          "Data column sidecar's referenced block has not been seen. Saving for future processing");
      return completedFuture(SAVE_FOR_FUTURE);
    }

    // For sidecars with headers (Fulu), perform additional validations
    if (maybeBlockHeader.isPresent()) {
      return validateWithBlockHeader(
          dataColumnSidecar, specLogic, dataColumnSidecarUtil, maybeBlockHeader.get());
    }

    // Common: KZG proof validation
    return validateKzgProofsAndFinalize(dataColumnSidecar, specLogic, validationHelper);
  }

  private SafeFuture<InternalValidationResult> validateWithBlockHeader(
      final DataColumnSidecar dataColumnSidecar,
      final SpecLogic specLogic,
      final DataColumnSidecarUtil dataColumnSidecarUtil,
      final BeaconBlockHeader blockHeader) {

    final DataColumnSidecarValidationResult parentBlockValidation =
        dataColumnSidecarUtil.validateParentBlock(
            blockHeader,
            gossipValidationHelper::getSlotForBlockRoot,
            invalidBlockRoots,
            gossipValidationHelper::currentFinalizedCheckpointIsAncestorOfBlock);

    if (!parentBlockValidation.isValid()) {
      return SafeFuture.completedFuture(
          reject(parentBlockValidation.getReason().orElse("Parent block validation failed")));
    }

    // Optimization: If we have already completely verified a sidecar with the same header
    final Bytes32 headerHash =
        DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader().hashTreeRoot();
    if (validSignedBlockHeaders.contains(headerHash)) {
      return validateWithKnownValidHeader(
          specLogic, dataColumnSidecarUtil, dataColumnSidecar, blockHeader);
    }
    final Optional<UInt64> maybeParentBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(blockHeader.getParentRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      return completedFuture(
          saveForFuture(
              "DataColumnSidecar block header parent block does not exist. It will be saved for future processing"));
    }
    final UInt64 parentBlockSlot = maybeParentBlockSlot.get();

    /*
     * [REJECT] The sidecar's kzg_commitments field inclusion proof is valid
     * as verified by verify_data_column_sidecar_inclusion_proof(sidecar).
     */
    try (MetricsHistogram.Timer ignored =
        dataColumnSidecarInclusionProofVerificationTimeSeconds.startTimer()) {
      if (!dataColumnSidecarUtil.verifyInclusionProof(
          specLogic, dataColumnSidecar, validInclusionProofInfoSet)) {
        return SafeFuture.completedFuture(
            reject("DataColumnSidecar inclusion proof validation failed"));
      }
    } catch (final Throwable t) {
      return SafeFuture.completedFuture(
          reject("DataColumnSidecar inclusion proof validation failed"));
    }

    /*
     * [REJECT] The sidecar's column data is valid
     *          as verified by verify_data_column_sidecar_kzg_proofs(sidecar).
     */
    try (MetricsHistogram.Timer ignored =
        dataColumnSidecarKzgBatchVerificationTimeSeconds.startTimer()) {
      if (!dataColumnSidecarUtil.verifyDataColumnSidecarKzgProofs(specLogic, dataColumnSidecar)) {
        return SafeFuture.completedFuture(reject("DataColumnSidecar does not pass kzg validation"));
      }
    } catch (final Throwable t) {
      return SafeFuture.completedFuture(reject("DataColumnSidecar does not pass kzg validation"));
    }

    return gossipValidationHelper
        .getParentStateInBlockEpoch(
            parentBlockSlot, blockHeader.getParentRoot(), blockHeader.getSlot())
        .thenApply(
            maybePostState -> {
              if (maybePostState.isEmpty()) {
                return ignore(
                    "DataColumnSidecar block header state wasn't available. Must have been pruned by finalized.");
              }
              final BeaconState postState = maybePostState.get();

              /*
               * [REJECT] The sidecar is proposed by the expected proposer_index for the block's slot
               * in the context of the current shuffling (defined by block_header.parent_root/block_header.slot).
               * If the proposer_index cannot immediately be verified against the expected shuffling,
               * the sidecar MAY be queued for later processing while proposers for the block's branch
               * are calculated -- in such a case do not REJECT, instead IGNORE this message.
               */
              if (!gossipValidationHelper.isProposerTheExpectedProposer(
                  blockHeader.getProposerIndex(), blockHeader.getSlot(), postState)) {
                return reject(
                    String.format(
                        "DataColumnSidecar block header proposed by incorrect proposer (%s)",
                        blockHeader.getProposerIndex()));
              }

              /*
               * [REJECT] The proposer signature of sidecar.signed_block_header,
               * is valid with respect to the block_header.proposer_index pubkey.
               */
              final Optional<DataColumnSidecarUtil.SignatureVerificationData> maybeSignatureData =
                  dataColumnSidecarUtil.getSignatureVerificationData(
                      spec, postState, dataColumnSidecar);
              if (maybeSignatureData.isPresent()) {
                final DataColumnSidecarUtil.SignatureVerificationData signatureData =
                    maybeSignatureData.get();
                if (!gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
                    signatureData.signingRoot(),
                    signatureData.proposerIndex(),
                    signatureData.signature(),
                    postState)) {
                  return reject("DataColumnSidecar block header signature is invalid");
                }
              }

              /*
               * Final check for equivocation
               */
              if (!markForEquivocation(dataColumnSidecarUtil, blockHeader, dataColumnSidecar)) {
                return ignore(
                    "DataColumnSidecar is not the first valid for its tracking key. It will be dropped.");
              }

              // Cache validated info for optimization
              dataColumnSidecarUtil.cacheValidatedInfo(
                  dataColumnSidecar, validSignedBlockHeaders, validInclusionProofInfoSet);

              return accept();
            });
  }

  private SafeFuture<InternalValidationResult> validateKzgProofsAndFinalize(
      final DataColumnSidecar dataColumnSidecar,
      final SpecLogic specLogic,
      final DataColumnSidecarUtil validationHelper) {

    /*
     * [REJECT] The sidecar's column data is valid as verified by verify_data_column_sidecar_kzg_proofs
     */
    try (MetricsHistogram.Timer ignored =
        dataColumnSidecarKzgBatchVerificationTimeSeconds.startTimer()) {
      if (!validationHelper.verifyDataColumnSidecarKzgProofs(specLogic, dataColumnSidecar)) {
        return SafeFuture.completedFuture(reject("DataColumnSidecar does not pass kzg validation"));
      }
    } catch (final Throwable t) {
      return SafeFuture.completedFuture(reject("DataColumnSidecar does not pass kzg validation"));
    }

    /*
     * Final check for equivocation
     */
    final DataColumnSidecarTrackingKey key = validationHelper.extractTrackingKey(dataColumnSidecar);
    if (!receivedValidDataColumnSidecarInfoSet.add(key)) {
      return completedFuture(
          ignore(
              "DataColumnSidecar is not the first valid for its tracking key. It will be dropped."));
    }

    return SafeFuture.completedFuture(accept());
  }

  private SafeFuture<InternalValidationResult> validateWithKnownValidHeader(
      final SpecLogic specLogic,
      final DataColumnSidecarUtil validationHelper,
      final DataColumnSidecar dataColumnSidecar,
      final BeaconBlockHeader blockHeader) {

    /*
     * [REJECT] The current finalized_checkpoint is an ancestor of the sidecar's block
     */
    if (!gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
        blockHeader.getSlot(), blockHeader.getParentRoot())) {
      return SafeFuture.completedFuture(
          reject("DataColumnSidecar block header does not descend from finalized checkpoint"));
    }

    /*
     * [REJECT] The sidecar's kzg_commitments field inclusion proof is valid
     * as verified by verify_data_column_sidecar_inclusion_proof(sidecar).
     */
    try (MetricsHistogram.Timer ignored =
        dataColumnSidecarInclusionProofVerificationTimeSeconds.startTimer()) {
      if (!validationHelper.verifyInclusionProof(
          specLogic, dataColumnSidecar, validInclusionProofInfoSet)) {
        return SafeFuture.completedFuture(
            reject("DataColumnSidecar inclusion proof validation failed"));
      }
    } catch (final Throwable t) {
      return SafeFuture.completedFuture(
          reject("DataColumnSidecar inclusion proof validation failed"));
    }

    /*
     * [REJECT] The current finalized_checkpoint is an ancestor of the sidecar's block
     * -- i.e. get_checkpoint_block(store, block_header.parent_root, store.finalized_checkpoint.epoch) == store.finalized_checkpoint.root.
     */
    try (MetricsHistogram.Timer ignored =
        dataColumnSidecarKzgBatchVerificationTimeSeconds.startTimer()) {
      if (!validationHelper.verifyDataColumnSidecarKzgProofs(specLogic, dataColumnSidecar)) {
        return SafeFuture.completedFuture(reject("DataColumnSidecar does not pass kzg validation"));
      }
    } catch (final Throwable t) {
      return SafeFuture.completedFuture(reject("DataColumnSidecar does not pass kzg validation"));
    }

    // Final equivocation check
    if (!markForEquivocation(validationHelper, blockHeader, dataColumnSidecar)) {
      return SafeFuture.completedFuture(
          ignore(
              "DataColumnSidecar is not the first valid for its tracking key. It will be dropped."));
    }

    return SafeFuture.completedFuture(accept());
  }

  public void markForEquivocation(
      final BeaconBlockHeader beaconBlockHeader, final List<DataColumnSidecar> sidecars) {
    LOG.debug(
        "Added recovered {} data column sidecars from block {} to gossip tracker",
        sidecars.size(),
        beaconBlockHeader.getRoot());
    if (sidecars.isEmpty()) {
      return;
    }

    final DataColumnSidecarUtil validationHelper =
        spec.getDataColumnSidecarUtil(beaconBlockHeader.getSlot());
    sidecars.forEach(sidecar -> markForEquivocation(validationHelper, beaconBlockHeader, sidecar));
  }

  private boolean markForEquivocation(
      final DataColumnSidecarUtil validationHelper,
      final BeaconBlockHeader beaconBlockHeader,
      final DataColumnSidecar sidecar) {
    final DataColumnSidecarTrackingKey key =
        validationHelper.extractTrackingKeyFromHeader(beaconBlockHeader, sidecar);
    return receivedValidDataColumnSidecarInfoSet.add(key);
  }

  private boolean isFirstValidForTrackingKey(
      final DataColumnSidecarUtil validationHelper, final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarTrackingKey key = validationHelper.extractTrackingKey(dataColumnSidecar);
    return !receivedValidDataColumnSidecarInfoSet.contains(key);
  }

  @SuppressWarnings("FormatStringAnnotation")
  private InternalValidationResult reject(final String reason) {
    totalDataColumnSidecarsProcessingValidatedCounter
        .labels(ValidationResultCode.REJECT.name())
        .inc();

    LOG.trace("DataColumnSidecar Gossip Validation Result: REJECT, reason: {}", reason);

    return InternalValidationResult.reject(reason);
  }

  @SuppressWarnings("FormatStringAnnotation")
  private InternalValidationResult ignore(final String reason) {
    totalDataColumnSidecarsProcessingValidatedCounter
        .labels(ValidationResultCode.IGNORE.name())
        .inc();

    LOG.trace("DataColumnSidecar Gossip Validation Result: IGNORE, reason: {}", reason);

    return InternalValidationResult.ignore(reason);
  }

  @SuppressWarnings("FormatStringAnnotation")
  private InternalValidationResult saveForFuture(final String reason) {
    totalDataColumnSidecarsProcessingValidatedCounter
        .labels(ValidationResultCode.SAVE_FOR_FUTURE.name())
        .inc();

    LOG.trace("DataColumnSidecar Gossip Validation Result: SAVE_FOR_FUTURE, reason: {}", reason);

    return InternalValidationResult.SAVE_FOR_FUTURE;
  }

  private InternalValidationResult accept() {
    totalDataColumnSidecarsProcessingSuccessesCounter.inc();
    totalDataColumnSidecarsProcessingValidatedCounter
        .labels(ValidationResultCode.ACCEPT.name())
        .inc();

    LOG.trace("DataColumnSidecar Gossip Validation Result: ACCEPT");

    return InternalValidationResult.ACCEPT;
  }
}
