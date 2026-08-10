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

package tech.pegasys.teku.validator.coordinator;

import java.util.Optional;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.blocks.versions.gloas.BlockContentsGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

// Gloas is more similar to BlockFactoryPhase0 than BlockFactoryFulu
public class BlockFactoryGloas extends BlockFactoryPhase0 {

  public BlockFactoryGloas(final Spec spec, final BlockOperationSelectorFactory operationSelector) {
    super(spec, operationSelector);
  }

  /**
   * Extends the base block production with Gloas-specific logic: when the proposer is self-building
   * (local EL payload, indicated by a G2-point-at-infinity signature on the bid), the block is
   * wrapped in a {@link BlockContentsGloas} containing the execution payload envelope, KZG proofs,
   * and blobs so they can be returned together in the V4 block-production response.
   *
   * <p>When an external builder's gossip bid is selected the signature is real, and there is no
   * locally-held execution payload to include, so the plain {@link BeaconBlock} is returned as-is.
   */
  @Override
  public SafeFuture<BlockContainerAndMetaData> createUnsignedBlock(
      final BlockProductionContext blockProductionContext) {
    return super.createUnsignedBlock(blockProductionContext)
        .thenCompose(
            blockContainerAndMetaData -> {
              final BeaconBlock block = (BeaconBlock) blockContainerAndMetaData.blockContainer();
              final BeaconBlockBodyGloas blockBodyGloas =
                  block.getBody().toVersionGloas().orElseThrow();

              // G2-point-at-infinity signature means we self-built the payload
              if (!blockBodyGloas.getSignedExecutionPayloadBid().getSignature().isInfinity()) {
                return SafeFuture.completedFuture(blockContainerAndMetaData);
              }

              final UInt64 slot = block.getSlot();
              final UInt64 builderIndex =
                  blockBodyGloas.getSignedExecutionPayloadBid().getMessage().getBuilderIndex();
              final SchemaDefinitionsGloas schemaDefinitions =
                  SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());

              return operationSelector
                  .getCachedPayloadResult(slot)
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "ExecutionPayloadResult not cached for self-built block at slot "
                                  + slot))
                  .getPayloadResponseFutureFromLocalFlowRequired()
                  .thenApply(
                      (GetPayloadResponse getPayloadResponse) -> {
                        final BlobsBundle blobsBundle =
                            getPayloadResponse.getBlobsBundle().orElseThrow();
                        final ExecutionPayloadEnvelope envelope =
                            schemaDefinitions
                                .getExecutionPayloadEnvelopeSchema()
                                .create(
                                    getPayloadResponse.getExecutionPayload(),
                                    getPayloadResponse.getExecutionRequests().orElseThrow(),
                                    builderIndex,
                                    block.hashTreeRoot(),
                                    block.getParentRoot());
                        final BlockContentsGloas blockContentsGloas =
                            schemaDefinitions
                                .getBlockContentsGloasSchema()
                                .create(
                                    block,
                                    envelope,
                                    blobsBundle.getProofs(),
                                    blobsBundle.getBlobs());
                        return blockContainerAndMetaData.withBlockContents(blockContentsGloas);
                      });
            });
  }

  // blocks in ePBS are all unblinded
  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> unblindSignedBlockIfBlinded(
      final SignedBeaconBlock maybeBlindedBlock,
      final BlockPublishingPerformance blockPublishingPerformance) {
    return SafeFuture.completedFuture(Optional.of(maybeBlindedBlock));
  }
}
