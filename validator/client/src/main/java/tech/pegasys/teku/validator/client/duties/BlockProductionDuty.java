/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.validator.client.duties;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.logging.Converter.gweiToEth;
import static tech.pegasys.teku.infrastructure.logging.Converter.weiToEth;
import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7732.ExecutionPayloadHeaderEip7732;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.signer.BlockContainerSigner;

public class BlockProductionDuty implements Duty {

  private static final Logger LOG = LogManager.getLogger();

  private final Validator validator;
  private final UInt64 slot;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final BlockContainerSigner blockContainerSigner;
  private final Spec spec;
  private final ValidatorDutyMetrics validatorDutyMetrics;

  public BlockProductionDuty(
      final Validator validator,
      final UInt64 slot,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final BlockContainerSigner blockContainerSigner,
      final Spec spec,
      final ValidatorDutyMetrics validatorDutyMetrics) {
    this.validator = validator;
    this.slot = slot;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.blockContainerSigner = blockContainerSigner;
    this.spec = spec;
    this.validatorDutyMetrics = validatorDutyMetrics;
  }

  @Override
  public DutyType getType() {
    return DutyType.BLOCK_PRODUCTION;
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Creating block for validator {} at slot {}", validator.getPublicKey(), slot);
    return forkProvider.getForkInfo(slot).thenCompose(this::produceBlock);
  }

  // EIP-7732 TODO: "naive" ePBS block production, need to think about a proper builder flow duty
  // abstraction
  private SafeFuture<DutyResult> produceBlock(final ForkInfo forkInfo) {
    final SpecMilestone milestone = spec.atSlot(slot).getMilestone();

    final SafeFuture<Void> ePBSBidPreparation;
    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.EIP7732)) {
      final SchemaDefinitionsEip7732 schemaDefinitions =
          SchemaDefinitionsEip7732.required(spec.atSlot(slot).getSchemaDefinitions());
      ePBSBidPreparation = processExecutionPayloadHeader(schemaDefinitions, forkInfo);
    } else {
      ePBSBidPreparation = SafeFuture.COMPLETE;
    }
    return ePBSBidPreparation
        .thenCompose(__ -> createRandaoReveal(forkInfo))
        .thenCompose(
            signature ->
                validatorDutyMetrics.record(
                    () -> createUnsignedBlock(signature), this, ValidatorDutyMetricsSteps.CREATE))
        .thenCompose(this::validateBlock)
        .thenCompose(
            blockContainerAndMetaData ->
                validatorDutyMetrics.record(
                    () -> signBlockContainer(forkInfo, blockContainerAndMetaData),
                    this,
                    ValidatorDutyMetricsSteps.SIGN))
        .thenCompose(
            signedBlockContainer ->
                validatorDutyMetrics.record(
                    () -> sendBlock(signedBlockContainer, milestone),
                    this,
                    ValidatorDutyMetricsSteps.SEND))
        .thenCompose(
            dutyResult -> {
              if (milestone.isGreaterThanOrEqualTo(SpecMilestone.EIP7732)) {
                final SchemaDefinitionsEip7732 schemaDefinitions =
                    SchemaDefinitionsEip7732.required(spec.atSlot(slot).getSchemaDefinitions());
                return processExecutionPayloadEnvelope(dutyResult, schemaDefinitions, forkInfo);
              } else {
                return SafeFuture.completedFuture(dutyResult);
              }
            })
        .exceptionally(error -> DutyResult.forError(validator.getPublicKey(), error));
  }

  private SafeFuture<Void> processExecutionPayloadHeader(
      final SchemaDefinitionsEip7732 schemaDefinitions, final ForkInfo forkInfo) {
    return validatorApiChannel
        .getHeader(slot, validator.getPublicKey())
        .thenCompose(
            maybeHeader -> {
              final ExecutionPayloadHeader header =
                  maybeHeader.orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Node was not syncing but could not create header"));
              return validator
                  .getSigner()
                  .signExecutionPayloadHeader(header, forkInfo)
                  .thenApply(
                      signature ->
                          schemaDefinitions
                              .getSignedExecutionPayloadHeaderSchema()
                              .create(header, signature));
            })
        .thenCompose(
            signedHeader ->
                validatorApiChannel.sendSignedHeader(signedHeader).thenApply(__ -> signedHeader))
        .thenAccept(
            publishedHeader -> {
              final ExecutionPayloadHeaderEip7732 bid =
                  ExecutionPayloadHeaderEip7732.required(publishedHeader.getMessage());
              VALIDATOR_LOGGER.logPublishedBid(
                  bid.getSlot(),
                  bid.getBuilderIndex(),
                  bid.getBlockHash(),
                  bid.getParentBlockHash(),
                  bid.getParentBlockRoot(),
                  gweiToEth(bid.getValue()));
            });
  }

  private SafeFuture<BLSSignature> createRandaoReveal(final ForkInfo forkInfo) {
    return validator.getSigner().createRandaoReveal(spec.computeEpochAtSlot(slot), forkInfo);
  }

  private SafeFuture<Optional<BlockContainerAndMetaData>> createUnsignedBlock(
      final BLSSignature randaoReveal) {
    return validatorApiChannel.createUnsignedBlock(
        slot, randaoReveal, validator.getGraffiti(), Optional.empty());
  }

  private SafeFuture<BlockContainer> validateBlock(
      final Optional<BlockContainerAndMetaData> maybeBlockContainer) {
    final BlockContainerAndMetaData blockContainerAndMetaData =
        maybeBlockContainer.orElseThrow(
            () -> new IllegalStateException("Node was not syncing but could not create block"));
    final BlockContainer unsignedBlockContainer = blockContainerAndMetaData.blockContainer();
    checkArgument(
        unsignedBlockContainer.getSlot().equals(slot),
        "Unsigned block slot (%s) does not match expected slot %s",
        unsignedBlockContainer.getSlot(),
        slot);

    if (!blockContainerAndMetaData.consensusBlockValue().isZero()) {
      LOG.info(
          "Validator client received block for slot {}, block rewards {} ETH, execution payload value {} ETH",
          slot,
          weiToEth(blockContainerAndMetaData.consensusBlockValue()),
          weiToEth(blockContainerAndMetaData.executionPayloadValue()));
    }
    return SafeFuture.completedFuture(unsignedBlockContainer);
  }

  private SafeFuture<SignedBlockContainer> signBlockContainer(
      final ForkInfo forkInfo, final BlockContainer blockContainer) {
    return blockContainerSigner.sign(blockContainer, validator, forkInfo);
  }

  private SafeFuture<DutyResult> sendBlock(
      final SignedBlockContainer signedBlockContainer, final SpecMilestone milestone) {
    return validatorApiChannel
        .sendSignedBlock(
            signedBlockContainer,
            // EIP-7732 TODO: need to make sure block is imported for ePBS (at least for the "naive"
            // flow)
            milestone.isGreaterThanOrEqualTo(SpecMilestone.EIP7732)
                ? BroadcastValidationLevel.CONSENSUS
                : BroadcastValidationLevel.GOSSIP)
        .thenApply(
            result -> {
              if (result.isPublished()) {
                return DutyResult.success(
                    signedBlockContainer.getRoot(),
                    getBlockSummary(signedBlockContainer.getSignedBlock().getMessage().getBody()));
              }
              return DutyResult.forError(
                  validator.getPublicKey(),
                  new IllegalArgumentException(
                      "Block was rejected by the beacon node: "
                          + result.getRejectionReason().orElse("<reason unknown>")));
            });
  }

  private SafeFuture<DutyResult> processExecutionPayloadEnvelope(
      final DutyResult blockProductionDutyResult,
      final SchemaDefinitionsEip7732 schemaDefinitions,
      final ForkInfo forkInfo) {
    return validatorApiChannel
        .getExecutionPayloadEnvelope(slot, validator.getPublicKey())
        .thenCompose(
            maybeEnvelope -> {
              final ExecutionPayloadEnvelope envelope =
                  maybeEnvelope.orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Node was not syncing but envelope was not available"));
              return validator
                  .getSigner()
                  .signExecutionPayloadEnvelope(slot, envelope, forkInfo)
                  .thenApply(
                      signature ->
                          schemaDefinitions
                              .getSignedExecutionPayloadEnvelopeSchema()
                              .create(envelope, signature))
                  .thenCompose(
                      signedEnvelope ->
                          validatorApiChannel
                              .sendSignedExecutionPayloadEnvelope(signedEnvelope)
                              .thenApply(__ -> signedEnvelope));
            })
        .thenApply(
            signedEnvelope -> {
              final ExecutionPayloadEnvelope envelope = signedEnvelope.getMessage();
              VALIDATOR_LOGGER.logPublishedExecutionPayload(
                  slot,
                  envelope.getBuilderIndex(),
                  envelope.getBeaconBlockRoot(),
                  envelope.getBlobKzgCommitments().size(),
                  getExecutionSummaryString(envelope.getPayload()));
              return blockProductionDutyResult;
            });
  }

  @VisibleForTesting
  List<String> getBlockSummary(final BeaconBlockBody blockBody) {
    final List<String> context = new ArrayList<>();
    getBlobsSummary(blockBody).ifPresent(context::add);
    getExecutionSummary(blockBody).ifPresent(context::add);
    return context;
  }

  private Optional<String> getBlobsSummary(final BeaconBlockBody blockBody) {
    return blockBody
        .getOptionalBlobKzgCommitments()
        .map(blobKzgCommitments -> "Blobs: " + blobKzgCommitments.size());
  }

  private Optional<String> getExecutionSummary(final BeaconBlockBody blockBody) {
    return blockBody.getOptionalExecutionPayloadSummary().map(this::getExecutionSummaryString);
  }

  private String getExecutionSummaryString(final ExecutionPayloadSummary summary) {
    UInt64 gasPercentage;
    try {
      gasPercentage =
          summary.getGasLimit().isGreaterThan(0L)
              ? summary.getGasUsed().times(100).dividedBy(summary.getGasLimit())
              : ZERO;
    } catch (final ArithmeticException e) {
      gasPercentage = UInt64.ZERO;
      LOG.debug("Failed to compute percentage", e);
    }
    return String.format(
        "%s (%s%%) gas, EL block: %s (%s)",
        summary.getGasUsed(),
        gasPercentage,
        summary.getBlockHash().toUnprefixedHexString(),
        summary.getBlockNumber());
  }

  @Override
  public String toString() {
    return "BlockProductionDuty{"
        + "validator="
        + validator
        + ", slot="
        + slot
        + ", forkProvider="
        + forkProvider
        + '}';
  }
}
