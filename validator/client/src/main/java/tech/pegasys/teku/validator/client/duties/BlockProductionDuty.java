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
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
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
  private final boolean useBlindedBlock;
  private final boolean blockV3Enabled;
  private final Spec spec;
  private final ValidatorDutyMetrics validatorDutyMetrics;

  public BlockProductionDuty(
      final Validator validator,
      final UInt64 slot,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final BlockContainerSigner blockContainerSigner,
      final boolean useBlindedBlock,
      final boolean blockV3Enabled,
      final Spec spec,
      final ValidatorDutyMetrics validatorDutyMetrics) {
    this.validator = validator;
    this.slot = slot;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.blockContainerSigner = blockContainerSigner;
    this.useBlindedBlock = useBlindedBlock;
    this.blockV3Enabled = blockV3Enabled;
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

  private SafeFuture<DutyResult> produceBlock(final ForkInfo forkInfo) {
    return createRandaoReveal(forkInfo)
        .thenCompose(
            signature ->
                validatorDutyMetrics.record(
                    () -> createUnsignedBlock(signature), this, ValidatorDutyMetricsSteps.CREATE))
        .thenCompose(this::validateBlock)
        .thenCompose(
            blockContainer ->
                validatorDutyMetrics.record(
                    () -> signBlockContainer(forkInfo, blockContainer),
                    this,
                    ValidatorDutyMetricsSteps.SIGN))
        .thenCompose(
            signedBlockContainer ->
                validatorDutyMetrics.record(
                    () -> sendBlock(signedBlockContainer), this, ValidatorDutyMetricsSteps.SEND))
        .exceptionally(error -> DutyResult.forError(validator.getPublicKey(), error));
  }

  private SafeFuture<BLSSignature> createRandaoReveal(final ForkInfo forkInfo) {
    return validator.getSigner().createRandaoReveal(spec.computeEpochAtSlot(slot), forkInfo);
  }

  private SafeFuture<Optional<BlockContainer>> createUnsignedBlock(
      final BLSSignature randaoReveal) {
    if (blockV3Enabled) {
      return validatorApiChannel.createUnsignedBlock(
          slot, randaoReveal, validator.getGraffiti(), Optional.empty());
    } else {
      return validatorApiChannel.createUnsignedBlock(
          slot, randaoReveal, validator.getGraffiti(), Optional.of(useBlindedBlock));
    }
  }

  private SafeFuture<BlockContainer> validateBlock(
      final Optional<BlockContainer> maybeBlockContainer) {
    final BlockContainer unsignedBlockContainer =
        maybeBlockContainer.orElseThrow(
            () -> new IllegalStateException("Node was not syncing but could not create block"));
    checkArgument(
        unsignedBlockContainer.getSlot().equals(slot),
        "Unsigned block slot (%s) does not match expected slot %s",
        unsignedBlockContainer.getSlot(),
        slot);
    return SafeFuture.completedFuture(unsignedBlockContainer);
  }

  private SafeFuture<SignedBlockContainer> signBlockContainer(
      final ForkInfo forkInfo, final BlockContainer blockContainer) {
    return blockContainerSigner.sign(blockContainer, validator, forkInfo);
  }

  private SafeFuture<DutyResult> sendBlock(final SignedBlockContainer signedBlockContainer) {
    return validatorApiChannel
        .sendSignedBlock(signedBlockContainer, BroadcastValidationLevel.NOT_REQUIRED)
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
