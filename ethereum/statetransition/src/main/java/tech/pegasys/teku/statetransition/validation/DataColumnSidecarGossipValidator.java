/*
 * Copyright Consensys Software Inc., 2026
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
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarTrackingKey;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil.InclusionProofInfo;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError;

/**
 * Gossip validator for Data Column Sidecars supporting both Fulu and Gloas forks.
 *
 * <p>Uses fork-specific {@link DataColumnSidecarUtil} implementations to handle validation
 * differences between forks. Fulu sidecars contain signed block headers and validate parent block
 * availability and header signatures. Gloas sidecars (ePBS) have no headers and validate against
 * execution payload bids instead.
 *
 * @see DataColumnSidecarUtil
 * @see <a
 *     href="https://github.com/ethereum/consensus-specs/blob/master/specs/fulu/p2p-interface.md#data_column_sidecar_subnet_id">Fulu
 *     Spec</a>
 * @see <a
 *     href="https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/p2p-interface.md#data_column_sidecar_subnet_id">Gloas
 *     Spec</a>
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
    return Collections.unmodifiableSet(receivedValidDataColumnSidecarInfoSet);
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

    totalDataColumnSidecarsProcessingRequestsCounter.inc();
    final DataColumnSidecarUtil dataColumnSidecarUtil =
        spec.getDataColumnSidecarUtil(dataColumnSidecar.getSlot());

    /*
     * [REJECT] The sidecar is valid as verified by verify_data_column_sidecar(sidecar).
     */
    if (!dataColumnSidecarUtil.verifyDataColumnSidecarStructure(dataColumnSidecar)) {
      return completedFuture(reject("DataColumnSidecar has invalid structure"));
    }

    /*
     * [IGNORE] The sidecar is the first sidecar for the tuple with valid cryptographic proofs.
     * - Fulu: (block_header.slot, block_header.proposer_index, sidecar.index)
     * - Gloas: (sidecar.beacon_block_root, sidecar.index)
     */
    if (!isFirstValidForTrackingKey(dataColumnSidecarUtil, dataColumnSidecar)) {
      return completedFuture(
          ignore(
              "DataColumnSidecar is not the first valid for its tracking key. It will be dropped."));
    }

    /*
     * [REJECT] The sidecar is for the correct subnet -- i.e. compute_subnet_for_data_column_sidecar(sidecar.index) == subnet_id.
     * Already done in {@link tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.DataColumnSidecarTopicHandler.TopicSubnetIdAwareOperationProcessor#process(
     *        DataColumnSidecar, Optional<UInt64>)}
     */

    /*
     * [IGNORE] The sidecar is not from a future slot (with a MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance)
     * -- i.e. validate that block_header.slot <= current_slot (a client MAY queue future sidecars for processing at the
     * appropriate slot).
     */

    final Optional<DataColumnSidecarValidationError> maybeSlotTimingValidationError =
        dataColumnSidecarUtil.performSlotTimingValidation(
            dataColumnSidecar, gossipValidationHelper::isSlotFromFuture);

    if (maybeSlotTimingValidationError.isPresent()) {
      return SafeFuture.completedFuture(
          toInternalValidationResult(maybeSlotTimingValidationError.get()));
    }

    /*
     * [IGNORE] The sidecar is from a slot greater than the latest finalized slot
     * -- i.e. validate that block_header.slot > compute_start_slot_at_epoch(state.finalized_checkpoint.epoch)
     */
    final Optional<DataColumnSidecarValidationError> maybeSlotFinalizationValidationError =
        dataColumnSidecarUtil.performSlotFinalizationValidation(
            dataColumnSidecar, gossipValidationHelper::isSlotFinalized);

    if (maybeSlotFinalizationValidationError.isPresent()) {
      return SafeFuture.completedFuture(
          toInternalValidationResult(maybeSlotFinalizationValidationError.get()));
    }

    /*
     * [IGNORE] The sidecar's block's parent (defined by block_header.parent_root) has been seen (via gossip or non-gossip sources)
     * (a client MAY queue sidecars for processing once the parent block is retrieved).
     */
    if (!dataColumnSidecarUtil.isBlockParentSeen(
        dataColumnSidecar, gossipValidationHelper::isBlockAvailable)) {
      LOG.trace(
          "Data column sidecar's referenced parent block block has not been seen. Saving for future processing");
      return completedFuture(SAVE_FOR_FUTURE);
    }

    /*
     * [IGNORE] A valid block for the sidecar's slot has been seen (via gossip or non-gossip sources).
     * If not yet seen, a client MUST queue the sidecar for deferred validation and possible processing once the block is received or retrieved.
     */
    if (!dataColumnSidecarUtil.isBlockSeen(
        dataColumnSidecar, gossipValidationHelper::isBlockAvailable)) {
      LOG.trace(
          "Data column sidecar's referenced block has not been seen via a valid signed execution payload bid. Saving for future processing");
      return completedFuture(SAVE_FOR_FUTURE);
    }

    /*
     * [REJECT] The sidecar's slot matches the slot of the block with root beacon_block_root
     */
    final Optional<DataColumnSidecarValidationError> maybeBlockSlotMatchValidationError =
        dataColumnSidecarUtil.validateBlockSlot(
            dataColumnSidecar, gossipValidationHelper::getSlotForBlockRoot);
    if (maybeBlockSlotMatchValidationError.isPresent()) {
      return completedFuture(toInternalValidationResult(maybeBlockSlotMatchValidationError.get()));
    }

    /*
     *  [REJECT] The sidecar's block's parent (defined by block_header.parent_root) passes validation.
     *  [REJECT] The sidecar is from a higher slot than the sidecar's block's parent (defined by block_header.parent_root).
     *  [REJECT] The current finalized_checkpoint is an ancestor of the sidecar's block -- i.e.
     *       get_checkpoint_block(store, block_header.parent_root, store.finalized_checkpoint.epoch)
     *       == store.finalized_checkpoint.root.
     */
    final Optional<DataColumnSidecarValidationError> maybeParentBlockValidationError =
        dataColumnSidecarUtil.validateParentBlock(
            dataColumnSidecar,
            invalidBlockRoots,
            gossipValidationHelper::getSlotForBlockRoot,
            gossipValidationHelper::currentFinalizedCheckpointIsAncestorOfBlock);
    if (maybeParentBlockValidationError.isPresent()) {
      return completedFuture(toInternalValidationResult(maybeParentBlockValidationError.get()));
    }

    /*
     * [REJECT] The sidecar's kzg_commitments field inclusion proof is valid
     * as verified by verify_data_column_sidecar_inclusion_proof(sidecar).
     */
    final Optional<InternalValidationResult> inclusionProofResult =
        verifyInclusionProofWithMetrics(dataColumnSidecarUtil, dataColumnSidecar);
    if (inclusionProofResult.isPresent()) {
      return completedFuture(inclusionProofResult.get());
    }

    /*
     * [REJECT] The sidecar's column data is valid as verified by verify_data_column_sidecar_kzg_proofs(sidecar).
     */
    final Optional<InternalValidationResult> kzgProofResult =
        verifyKzgProofsWithMetrics(dataColumnSidecarUtil, dataColumnSidecar);
    if (kzgProofResult.isPresent()) {
      return completedFuture(kzgProofResult.get());
    }

    /*
     * [REJECT] The sidecar is proposed by the expected proposer_index for the block's slot
     * in the context of the current shuffling (defined by block_header.parent_root/block_header.slot).
     * If the proposer_index cannot immediately be verified against the expected shuffling,
     * the sidecar MAY be queued for later processing while proposers for the block's branch
     * are calculated -- in such a case do not REJECT, instead IGNORE this message.
     *
     * [REJECT] The proposer signature of sidecar.signed_block_header,
     * is valid with respect to the block_header.proposer_index pubkey.
     */

    final SafeFuture<Optional<DataColumnSidecarValidationError>> maybeStateValidationErrorFuture =
        dataColumnSidecarUtil.validateWithState(
            dataColumnSidecar,
            spec,
            receivedValidDataColumnSidecarInfoSet,
            validInclusionProofInfoSet,
            validSignedBlockHeaders,
            gossipValidationHelper::getSlotForBlockRoot,
            gossipValidationHelper::getParentStateInBlockEpoch,
            gossipValidationHelper::isProposerTheExpectedProposer,
            gossipValidationHelper::isSignatureValidWithRespectToProposerIndex);

    /*
     * [REJECT] The hash of the sidecar's kzg_commitments matches the blob_kzg_commitments_root in the corresponding builder's
     * bid for sidecar.beacon_block_root.
     */
    final SafeFuture<Optional<DataColumnSidecarValidationError>> maybeBlockValidationErrorFuture =
        dataColumnSidecarUtil.validateWithBlock(
            dataColumnSidecar, gossipValidationHelper::retrieveBlockByRoot);

    return maybeStateValidationErrorFuture.thenCombine(
        maybeBlockValidationErrorFuture,
        (maybeStateValidationResult, maybeBlockValidationResult) -> {
          if (maybeStateValidationResult.isPresent()) {
            return toInternalValidationResult(maybeStateValidationResult.get());
          } else if (maybeBlockValidationResult.isPresent()) {
            return toInternalValidationResult(maybeBlockValidationResult.get());
          }
          // Final equivocation check
          final DataColumnSidecarTrackingKey key =
              dataColumnSidecarUtil.extractTrackingKey(dataColumnSidecar);
          if (!receivedValidDataColumnSidecarInfoSet.add(key)) {
            return ignore(
                "DataColumnSidecar is not the first valid for its tracking key. It will be dropped.");
          }
          return accept();
        });
  }

  private InternalValidationResult toInternalValidationResult(
      final DataColumnSidecarValidationError dataColumnSidecarValidationError) {
    return switch (dataColumnSidecarValidationError) {
      case DataColumnSidecarValidationError.Critical c -> reject(c.description());
      case DataColumnSidecarValidationError.Transient t -> ignore(t.description());
      case DataColumnSidecarValidationError.Timing tm -> saveForFuture(tm.description());
    };
  }

  private Optional<InternalValidationResult> verifyInclusionProofWithMetrics(
      final DataColumnSidecarUtil dataColumnSidecarUtil,
      final DataColumnSidecar dataColumnSidecar) {

    /*
     * [REJECT] The sidecar's kzg_commitments field inclusion proof is valid
     * as verified by verify_data_column_sidecar_inclusion_proof(sidecar).
     */
    try (MetricsHistogram.Timer ignored =
        dataColumnSidecarInclusionProofVerificationTimeSeconds.startTimer()) {
      if (!dataColumnSidecarUtil.verifyInclusionProof(
          dataColumnSidecar, validInclusionProofInfoSet)) {
        return Optional.of(reject("DataColumnSidecar inclusion proof validation failed"));
      }
    } catch (final Throwable t) {
      return Optional.of(reject("DataColumnSidecar inclusion proof validation failed"));
    }

    return Optional.empty();
  }

  private Optional<InternalValidationResult> verifyKzgProofsWithMetrics(
      final DataColumnSidecarUtil dataColumnSidecarUtil,
      final DataColumnSidecar dataColumnSidecar) {
    try (MetricsHistogram.Timer ignored =
        dataColumnSidecarKzgBatchVerificationTimeSeconds.startTimer()) {
      if (!dataColumnSidecarUtil.verifyDataColumnSidecarKzgProofs(dataColumnSidecar)) {
        return Optional.of(reject("DataColumnSidecar does not pass kzg validation"));
      }
    } catch (final Throwable t) {
      return Optional.of(reject("DataColumnSidecar does not pass kzg validation"));
    }

    return Optional.empty();
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

    final DataColumnSidecarUtil dataColumnSidecarUtil =
        spec.getDataColumnSidecarUtil(beaconBlockHeader.getSlot());
    sidecars.forEach(
        sidecar -> markForEquivocation(dataColumnSidecarUtil, beaconBlockHeader, sidecar));
  }

  private boolean markForEquivocation(
      final DataColumnSidecarUtil dataColumnSidecarUtil,
      final BeaconBlockHeader beaconBlockHeader,
      final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarTrackingKey key =
        dataColumnSidecarUtil.extractTrackingKeyFromHeader(beaconBlockHeader, dataColumnSidecar);
    return receivedValidDataColumnSidecarInfoSet.add(key);
  }

  private boolean isFirstValidForTrackingKey(
      final DataColumnSidecarUtil dataColumnSidecarUtil,
      final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarTrackingKey key =
        dataColumnSidecarUtil.extractTrackingKey(dataColumnSidecar);
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

    return SAVE_FOR_FUTURE;
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
