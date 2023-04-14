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

package tech.pegasys.teku.validator.client.duties;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;

public class BlockProductionDuty implements Duty {
  private static final Logger LOG = LogManager.getLogger();
  private final Validator validator;
  private final UInt64 slot;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final boolean useBlindedBlock;
  private final Spec spec;

  public BlockProductionDuty(
      final Validator validator,
      final UInt64 slot,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final boolean useBlindedBlock,
      final Spec spec) {
    this.validator = validator;
    this.slot = slot;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.useBlindedBlock = useBlindedBlock;
    this.spec = spec;
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Creating block for validator {} at slot {}", validator.getPublicKey(), slot);
    return forkProvider.getForkInfo(slot).thenCompose(this::produceBlock);
  }

  public SafeFuture<DutyResult> produceBlock(final ForkInfo forkInfo) {
    return createRandaoReveal(forkInfo)
        .thenCompose(this::createUnsignedBlock)
        .thenCompose(unsignedBlock -> signBlock(forkInfo, unsignedBlock))
        .thenCompose(this::sendBlock)
        .exceptionally(error -> DutyResult.forError(validator.getPublicKey(), error));
  }

  private SafeFuture<DutyResult> sendBlock(final SignedBeaconBlock signedBlock) {
    return validatorApiChannel
        .sendSignedBlock(signedBlock)
        .thenApply(
            result -> {
              if (result.isPublished()) {
                return DutyResult.success(
                    signedBlock.getRoot(), getBlockSummary(signedBlock.getMessage().getBody()));
              }
              return DutyResult.forError(
                  validator.getPublicKey(),
                  new IllegalArgumentException(
                      "Block was rejected by the beacon node: "
                          + result.getRejectionReason().orElse("<reason unknown>")));
            });
  }

  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(final BLSSignature randaoReveal) {
    return validatorApiChannel.createUnsignedBlock(
        slot, randaoReveal, validator.getGraffiti(), useBlindedBlock);
  }

  public SafeFuture<BLSSignature> createRandaoReveal(final ForkInfo forkInfo) {
    return validator.getSigner().createRandaoReveal(spec.computeEpochAtSlot(slot), forkInfo);
  }

  public SafeFuture<SignedBeaconBlock> signBlock(
      final ForkInfo forkInfo, final Optional<BeaconBlock> maybeBlock) {
    final BeaconBlock unsignedBlock =
        maybeBlock.orElseThrow(
            () -> new IllegalStateException("Node was not syncing but could not create block"));
    checkArgument(
        unsignedBlock.getSlot().equals(slot),
        "Unsigned block slot (%s) does not match expected slot %s",
        unsignedBlock.getSlot(),
        slot);
    return validator
        .getSigner()
        .signBlock(unsignedBlock, forkInfo)
        .thenApply(signature -> SignedBeaconBlock.create(spec, unsignedBlock, signature));
  }

  @SuppressWarnings("unused")
  public SafeFuture<SignedBlobSidecar> signBlobSidecar(
      final ForkInfo forkInfo, final Optional<BlobSidecar> maybeBlobSidecar) {
    final BlobSidecar unsignedBlobSidecar =
        maybeBlobSidecar.orElseThrow(
            () ->
                new IllegalStateException(
                    "Node was not syncing but could not create blob sidecar"));
    checkArgument(
        unsignedBlobSidecar.getSlot().equals(slot),
        "Unsigned blob sidecar slot (%s) does not match expected slot %s",
        unsignedBlobSidecar.getSlot(),
        slot);

    final SchemaDefinitions schemaDefinitions = spec.getGenesisSchemaDefinitions();
    final SignedBlobSidecarSchema signedBlobSidecarSchema =
        schemaDefinitions.toVersionDeneb().orElseThrow().getSignedBlobSidecarSchema();

    return validator
        .getSigner()
        .signBlobSidecar(unsignedBlobSidecar, forkInfo)
        .thenApply(signature -> signedBlobSidecarSchema.create(unsignedBlobSidecar, signature));
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

  static Optional<String> getBlockSummary(final BeaconBlockBody blockBody) {
    return blockBody
        .getOptionalExecutionPayloadSummary()
        .map(BlockProductionDuty::getSummaryString);
  }

  private static String getSummaryString(final ExecutionPayloadSummary summary) {
    UInt64 gasPercentage;
    try {
      gasPercentage =
          summary.getGasLimit().isGreaterThan(0L)
              ? summary.getGasUsed().times(100).dividedBy(summary.getGasLimit())
              : ZERO;
    } catch (ArithmeticException e) {
      gasPercentage = UInt64.ZERO;
      LOG.debug("Failed to compute percentage", e);
    }
    return String.format(
        "%s (%s%%) gas, EL block:  %s (%s)",
        summary.getGasUsed(),
        gasPercentage,
        summary.getBlockHash().toUnprefixedHexString(),
        summary.getBlockNumber());
  }
}
