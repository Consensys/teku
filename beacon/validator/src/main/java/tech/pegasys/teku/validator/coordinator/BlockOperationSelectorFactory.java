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

package tech.pegasys.teku.validator.coordinator;

import static com.google.common.base.Preconditions.checkState;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidWithFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationForkChecker;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;

public class BlockOperationSelectorFactory {
  private final Spec spec;
  private final AggregatingAttestationPool attestationPool;
  private final OperationPool<AttesterSlashing> attesterSlashingPool;
  private final OperationPool<ProposerSlashing> proposerSlashingPool;
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool;
  private final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool;
  private final SyncCommitteeContributionPool contributionPool;
  private final DepositProvider depositProvider;
  private final Eth1DataCache eth1DataCache;
  private final GraffitiBuilder graffitiBuilder;
  private final ForkChoiceNotifier forkChoiceNotifier;
  private final ExecutionLayerBlockProductionManager executionLayerBlockProductionManager;

  public BlockOperationSelectorFactory(
      final Spec spec,
      final AggregatingAttestationPool attestationPool,
      final OperationPool<AttesterSlashing> attesterSlashingPool,
      final OperationPool<ProposerSlashing> proposerSlashingPool,
      final OperationPool<SignedVoluntaryExit> voluntaryExitPool,
      final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool,
      final SyncCommitteeContributionPool contributionPool,
      final DepositProvider depositProvider,
      final Eth1DataCache eth1DataCache,
      final GraffitiBuilder graffitiBuilder,
      final ForkChoiceNotifier forkChoiceNotifier,
      final ExecutionLayerBlockProductionManager executionLayerBlockProductionManager) {
    this.spec = spec;
    this.attestationPool = attestationPool;
    this.attesterSlashingPool = attesterSlashingPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.voluntaryExitPool = voluntaryExitPool;
    this.blsToExecutionChangePool = blsToExecutionChangePool;
    this.contributionPool = contributionPool;
    this.depositProvider = depositProvider;
    this.eth1DataCache = eth1DataCache;
    this.graffitiBuilder = graffitiBuilder;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.executionLayerBlockProductionManager = executionLayerBlockProductionManager;
  }

  public Function<BeaconBlockBodyBuilder, SafeFuture<Void>> createSelector(
      final Bytes32 parentRoot,
      final BeaconState blockSlotState,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti,
      final Optional<Boolean> requestedBlinded,
      final Optional<UInt64> requestedBuilderBoostFactor,
      final BlockProductionPerformance blockProductionPerformance) {

    return bodyBuilder -> {
      final Eth1Data eth1Data = eth1DataCache.getEth1Vote(blockSlotState);

      final SszList<Attestation> attestations =
          attestationPool.getAttestationsForBlock(
              blockSlotState, new AttestationForkChecker(spec, blockSlotState));

      // Collect slashings to include
      final Set<UInt64> exitedValidators = new HashSet<>();
      final SszList<AttesterSlashing> attesterSlashings =
          attesterSlashingPool.getItemsForBlock(
              blockSlotState,
              slashing -> !exitedValidators.containsAll(slashing.getIntersectingValidatorIndices()),
              slashing -> exitedValidators.addAll(slashing.getIntersectingValidatorIndices()));

      final SszList<ProposerSlashing> proposerSlashings =
          proposerSlashingPool.getItemsForBlock(
              blockSlotState,
              slashing ->
                  !exitedValidators.contains(slashing.getHeader1().getMessage().getProposerIndex()),
              slashing ->
                  exitedValidators.add(slashing.getHeader1().getMessage().getProposerIndex()));

      // Collect exits to include
      final SszList<SignedVoluntaryExit> voluntaryExits =
          voluntaryExitPool.getItemsForBlock(
              blockSlotState,
              exit -> !exitedValidators.contains(exit.getMessage().getValidatorIndex()),
              exit -> exitedValidators.add(exit.getMessage().getValidatorIndex()));

      bodyBuilder
          .randaoReveal(randaoReveal)
          .eth1Data(eth1Data)
          .graffiti(graffitiBuilder.buildGraffiti(optionalGraffiti))
          .attestations(attestations)
          .proposerSlashings(proposerSlashings)
          .attesterSlashings(attesterSlashings)
          .deposits(depositProvider.getDeposits(blockSlotState, eth1Data))
          .voluntaryExits(voluntaryExits);

      // Optional fields introduced in later forks

      // Sync aggregate
      if (bodyBuilder.supportsSyncAggregate()) {
        bodyBuilder.syncAggregate(
            contributionPool.createSyncAggregateForBlock(blockSlotState.getSlot(), parentRoot));
      }

      // BLS to Execution changes
      if (bodyBuilder.supportsBlsToExecutionChanges()) {
        bodyBuilder.blsToExecutionChanges(
            blsToExecutionChangePool.getItemsForBlock(blockSlotState));
      }

      // Execution Payload / Execution Payload Header / KZG Commitments
      final SafeFuture<Void> blockProductionComplete;
      if (bodyBuilder.supportsExecutionPayload()) {
        final SchemaDefinitionsBellatrix schemaDefinitions =
            SchemaDefinitionsBellatrix.required(
                spec.atSlot(blockSlotState.getSlot()).getSchemaDefinitions());
        blockProductionComplete =
            forkChoiceNotifier
                .getPayloadId(parentRoot, blockSlotState.getSlot())
                .thenCompose(
                    executionPayloadContext ->
                        setExecutionData(
                            executionPayloadContext,
                            bodyBuilder,
                            requestedBlinded,
                            requestedBuilderBoostFactor,
                            schemaDefinitions,
                            blockSlotState,
                            blockProductionPerformance));
      } else {
        blockProductionComplete = SafeFuture.COMPLETE;
      }

      blockProductionPerformance.beaconBlockPrepared();

      return blockProductionComplete;
    };
  }

  private SafeFuture<Void> setExecutionData(
      final Optional<ExecutionPayloadContext> executionPayloadContext,
      final BeaconBlockBodyBuilder bodyBuilder,
      final Optional<Boolean> requestedBlinded,
      final Optional<UInt64> requestedBuilderBoostFactor,
      final SchemaDefinitionsBellatrix schemaDefinitions,
      final BeaconState blockSlotState,
      final BlockProductionPerformance blockProductionPerformance) {

    if (spec.isMergeTransitionComplete(blockSlotState) && executionPayloadContext.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "ExecutionPayloadContext is not provided for production of post-merge block at slot %s",
              blockSlotState.getSlot()));
    }

    // if requestedBlinded has been specified, we strictly follow it otherwise, we should run
    // Builder flow (blinded) only if we have a validator registration
    final boolean shouldTryBuilderFlow =
        requestedBlinded.orElseGet(
            () ->
                executionPayloadContext
                    .map(ExecutionPayloadContext::isValidatorRegistrationPresent)
                    .orElse(false));

    // pre-Merge Execution Payload / Execution Payload Header
    if (executionPayloadContext.isEmpty()) {
      if (shouldTryBuilderFlow) {
        bodyBuilder.executionPayloadHeader(
            schemaDefinitions.getExecutionPayloadHeaderSchema().getHeaderOfDefaultPayload());
      } else {
        bodyBuilder.executionPayload(schemaDefinitions.getExecutionPayloadSchema().getDefault());
      }
      return SafeFuture.COMPLETE;
    }

    final ExecutionPayloadResult executionPayloadResult =
        executionLayerBlockProductionManager.initiateBlockProduction(
            executionPayloadContext.orElseThrow(),
            blockSlotState,
            shouldTryBuilderFlow,
            requestedBuilderBoostFactor,
            blockProductionPerformance);

    // we should try to return unblinded content only if no explicit "blindness" has been requested
    // and builder flow fallbacks to local
    final Function<BuilderBidWithFallbackData, Boolean> setUnblindedContentIfBuilderFallbacks =
        builderBidWithFallbackData ->
            builderBidWithFallbackData.getFallbackData().isPresent() && requestedBlinded.isEmpty();

    return SafeFuture.allOf(
        cacheExecutionPayloadValue(executionPayloadResult, blockSlotState),
        // Execution Payload / Execution Payload Header
        setPayloadPostMerge(
            bodyBuilder, setUnblindedContentIfBuilderFallbacks, executionPayloadResult),
        // KZG Commitments
        setKzgCommitments(bodyBuilder, schemaDefinitions, executionPayloadResult));
  }

  private SafeFuture<Void> cacheExecutionPayloadValue(
      final ExecutionPayloadResult executionPayloadResult, final BeaconState blockSlotState) {
    return executionPayloadResult
        .getExecutionPayloadValueFuture()
        .thenAccept(
            blockExecutionValue ->
                BeaconStateCache.getSlotCaches(blockSlotState)
                    .setBlockExecutionValue(blockExecutionValue));
  }

  private SafeFuture<Void> setPayloadPostMerge(
      final BeaconBlockBodyBuilder bodyBuilder,
      final Function<BuilderBidWithFallbackData, Boolean> setUnblindedContentIfBuilderFallbacks,
      final ExecutionPayloadResult executionPayloadResult) {

    if (executionPayloadResult.isFromNonBlindedFlow()) {
      // non-blinded flow
      return executionPayloadResult
          .getExecutionPayloadFutureFromNonBlindedFlow()
          .orElseThrow()
          .thenAccept(bodyBuilder::executionPayload);
    }

    // blinded flow
    return executionPayloadResult
        .getBuilderBidWithFallbackDataFuture()
        .orElseThrow()
        .thenAccept(
            builderBidWithFallbackData -> {
              if (setUnblindedContentIfBuilderFallbacks.apply(builderBidWithFallbackData)) {
                bodyBuilder.executionPayload(
                    builderBidWithFallbackData
                        .getFallbackData()
                        .orElseThrow()
                        .getExecutionPayload());
              } else {
                bodyBuilder.executionPayloadHeader(
                    builderBidWithFallbackData.getBuilderBid().getHeader());
              }
            });
  }

  private SafeFuture<Void> setKzgCommitments(
      final BeaconBlockBodyBuilder bodyBuilder,
      final SchemaDefinitions schemaDefinitions,
      final ExecutionPayloadResult executionPayloadResult) {
    if (!bodyBuilder.supportsKzgCommitments()) {
      return SafeFuture.COMPLETE;
    }
    final SafeFuture<SszList<SszKZGCommitment>> blobKzgCommitments;
    if (executionPayloadResult.isFromNonBlindedFlow()) {
      // non-blinded flow
      final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema =
          SchemaDefinitionsDeneb.required(schemaDefinitions).getBlobKzgCommitmentsSchema();
      blobKzgCommitments =
          getExecutionBlobsBundleFromNonBlindedFlow(executionPayloadResult)
              .thenApply(blobKzgCommitmentsSchema::createFromBlobsBundle);
    } else {
      // blinded flow
      blobKzgCommitments =
          executionPayloadResult
              .getBuilderBidWithFallbackDataFuture()
              .orElseThrow()
              .thenApply(
                  builderBidWithFallbackData ->
                      builderBidWithFallbackData
                          .getBuilderBid()
                          .getOptionalBlobKzgCommitments()
                          .orElseThrow(
                              () ->
                                  new IllegalStateException(
                                      "builder BlobKzgCommitments are not available")));
    }

    return blobKzgCommitments.thenAccept(bodyBuilder::blobKzgCommitments);
  }

  public Consumer<SignedBeaconBlockUnblinder> createBlockUnblinderSelector(
      final BlockPublishingPerformance blockPublishingPerformance) {
    return bodyUnblinder -> {
      final SignedBeaconBlock signedBlindedBlock = bodyUnblinder.getSignedBlindedBeaconBlock();

      final BeaconBlock block = signedBlindedBlock.getMessage();

      if (block
          .getBody()
          .getOptionalExecutionPayloadHeader()
          .orElseThrow()
          .isHeaderOfDefaultPayload()) {
        // Terminal block not reached, provide default payload
        bodyUnblinder.setExecutionPayloadSupplier(
            () ->
                SafeFuture.completedFuture(
                    SchemaDefinitionsBellatrix.required(
                            spec.atSlot(block.getSlot()).getSchemaDefinitions())
                        .getExecutionPayloadSchema()
                        .getDefault()));
      } else {
        bodyUnblinder.setExecutionPayloadSupplier(
            () ->
                executionLayerBlockProductionManager
                    .getUnblindedPayload(signedBlindedBlock, blockPublishingPerformance)
                    .thenApply(BuilderPayload::getExecutionPayload));
      }
    };
  }

  public Function<BeaconBlock, SafeFuture<BlobsBundle>> createBlobsBundleSelector() {
    return block -> getCachedExecutionBlobsBundle(block.getSlot());
  }

  public Function<SignedBlockContainer, List<BlobSidecar>> createBlobSidecarsSelector(
      final BlockPublishingPerformance blockPublishingPerformance) {
    return blockContainer -> {
      final UInt64 slot = blockContainer.getSlot();
      final SignedBeaconBlock block = blockContainer.getSignedBlock();

      final MiscHelpersDeneb miscHelpersDeneb =
          MiscHelpersDeneb.required(spec.atSlot(slot).miscHelpers());

      final SszList<Blob> blobs;
      final SszList<SszKZGProof> proofs;
      final SszList<SszKZGCommitment> blockCommitments =
          block.getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow();

      if (blockContainer.isBlinded()) {
        // need to use the builder BlobsBundle for the blinded flow, because the
        // blobs and the proofs wouldn't be part of the BlockContainer
        final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle =
            getCachedBuilderBlobsBundle(slot);

        blobs = blobsBundle.getBlobs();
        proofs = blobsBundle.getProofs();

        // consistency check because the BlobsBundle comes from an external source (a builder)
        checkState(
            blobsBundle.getCommitments().hashTreeRoot().equals(blockCommitments.hashTreeRoot()),
            "Commitments in the builder BlobsBundle don't match the commitments in the block");
        checkState(
            blockCommitments.size() == proofs.size(),
            "The number of proofs in the builder BlobsBundle doesn't match the number of commitments in the block");
        checkState(
            blockCommitments.size() == blobs.size(),
            "The number of blobs in the builder BlobsBundle doesn't match the number of commitments in the block");
      } else {
        blobs = blockContainer.getBlobs().orElseThrow();
        proofs = blockContainer.getKzgProofs().orElseThrow();
      }

      final List<BlobSidecar> blobSidecars =
          IntStream.range(0, blobs.size())
              .mapToObj(
                  index ->
                      miscHelpersDeneb.constructBlobSidecar(
                          block, UInt64.valueOf(index), blobs.get(index), proofs.get(index)))
              .toList();

      blockPublishingPerformance.blobSidecarsPrepared();

      return blobSidecars;
    };
  }

  private SafeFuture<BlobsBundle> getExecutionBlobsBundleFromNonBlindedFlow(
      final ExecutionPayloadResult executionPayloadResult) {
    return executionPayloadResult
        .getBlobsBundleFutureFromNonBlindedFlow()
        .orElseThrow(this::executionBlobsBundleIsNotAvailableException)
        .thenApply(
            blobsBundle ->
                blobsBundle.orElseThrow(this::executionBlobsBundleIsNotAvailableException));
  }

  private SafeFuture<BlobsBundle> getCachedExecutionBlobsBundle(final UInt64 slot) {
    final ExecutionPayloadResult executionPayloadResult =
        executionLayerBlockProductionManager
            .getCachedPayloadResult(slot)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "ExecutionPayloadResult hasn't been cached for slot " + slot));

    if (executionPayloadResult.isFromNonBlindedFlow()) {
      // we performed a non-blinded flow, so the bundle must be in getPayloadResponseFuture
      return getExecutionBlobsBundleFromNonBlindedFlow(executionPayloadResult);
    } else {
      // we performed a blinded flow, so the bundle must be in getBuilderBidWithFallbackDataFuture
      return executionPayloadResult
          .getBuilderBidWithFallbackDataFuture()
          .orElseThrow(() -> executionBlobsBundleIsNotAvailableException(slot))
          .thenApply(
              builderBidWithFallbackData ->
                  builderBidWithFallbackData
                      .getFallbackData()
                      .orElseThrow(() -> executionBlobsBundleIsNotAvailableException(slot))
                      .getBlobsBundle())
          .thenApply(
              blobsBundle ->
                  blobsBundle.orElseThrow(() -> executionBlobsBundleIsNotAvailableException(slot)));
    }
  }

  private IllegalStateException executionBlobsBundleIsNotAvailableException() {
    return new IllegalStateException("execution BlobsBundle is not available");
  }

  private IllegalStateException executionBlobsBundleIsNotAvailableException(final UInt64 slot) {
    return new IllegalStateException("execution BlobsBundle is not available for slot " + slot);
  }

  private tech.pegasys.teku.spec.datastructures.builder.BlobsBundle getCachedBuilderBlobsBundle(
      final UInt64 slot) {
    return executionLayerBlockProductionManager
        .getCachedUnblindedPayload(slot)
        .orElseThrow(
            () -> new IllegalStateException("BuilderPayload hasn't been cached for slot " + slot))
        .getOptionalBlobsBundle()
        .orElseThrow(
            () ->
                new IllegalStateException("builder BlobsBundle is not available for slot " + slot));
  }
}
