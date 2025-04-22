/*
 * Copyright Consensys Software Inc., 2025
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
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContentsWithBlobsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.builder.versions.deneb.BlobsBundleDeneb;
import tech.pegasys.teku.spec.datastructures.builder.versions.fulu.BlobsBundleFulu;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.BlobsCellBundle;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.BuilderPayloadOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
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
      final Optional<UInt64> requestedBuilderBoostFactor,
      final BlockProductionPerformance blockProductionPerformance) {

    return bodyBuilder -> {
      blockProductionPerformance.beaconBlockPreparationStarted();
      final Eth1Data eth1Data = eth1DataCache.getEth1Vote(blockSlotState);

      final SszList<Attestation> attestations =
          attestationPool.getAttestationsForBlock(
              blockSlotState, new AttestationForkChecker(spec, blockSlotState));
      blockProductionPerformance.getAttestationsForBlock();

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
              exit -> voluntaryExitPredicate(blockSlotState, exitedValidators, exit),
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

      // Post-Altair: Sync aggregate
      if (bodyBuilder.supportsSyncAggregate()) {
        bodyBuilder.syncAggregate(
            contributionPool.createSyncAggregateForBlock(blockSlotState.getSlot(), parentRoot));
      }

      // Post-Capella: BLS to Execution changes
      if (bodyBuilder.supportsBlsToExecutionChanges()) {
        bodyBuilder.blsToExecutionChanges(
            blsToExecutionChangePool.getItemsForBlock(blockSlotState));
      }

      final SchemaDefinitions schemaDefinitions =
          spec.atSlot(blockSlotState.getSlot()).getSchemaDefinitions();

      final SafeFuture<Void> blockProductionComplete;

      // In `setExecutionData` the following fields are set:
      // Post-Bellatrix: Execution Payload / Execution Payload Header
      // Post-Deneb: KZG Commitments
      if (bodyBuilder.supportsExecutionPayload()) {
        blockProductionComplete =
            forkChoiceNotifier
                .getPayloadId(parentRoot, blockSlotState.getSlot())
                .thenCompose(
                    executionPayloadContext ->
                        setExecutionData(
                            executionPayloadContext,
                            bodyBuilder,
                            requestedBuilderBoostFactor,
                            SchemaDefinitionsBellatrix.required(schemaDefinitions),
                            blockSlotState,
                            blockProductionPerformance));
      } else {
        blockProductionComplete = SafeFuture.COMPLETE;
      }

      blockProductionPerformance.beaconBlockPrepared();

      return blockProductionComplete;
    };
  }

  private boolean voluntaryExitPredicate(
      final BeaconState blockSlotState,
      final Set<UInt64> exitedValidators,
      final SignedVoluntaryExit exit) {
    final UInt64 validatorIndex = exit.getMessage().getValidatorIndex();
    if (exitedValidators.contains(validatorIndex)) {
      return false;
    }
    // if there is  a pending withdrawal, the exit is not valid for inclusion in a block.
    return blockSlotState
        .toVersionElectra()
        .map(
            beaconStateElectra ->
                beaconStateElectra.getPendingPartialWithdrawals().stream()
                    .map(PendingPartialWithdrawal::getValidatorIndex)
                    .noneMatch(index -> index == validatorIndex.intValue()))
        .orElse(true);
  }

  private SafeFuture<Void> setExecutionData(
      final Optional<ExecutionPayloadContext> executionPayloadContext,
      final BeaconBlockBodyBuilder bodyBuilder,
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

    // pre-Merge Execution Payload / Execution Payload Header
    if (executionPayloadContext.isEmpty()) {
      bodyBuilder.executionPayload(schemaDefinitions.getExecutionPayloadSchema().getDefault());
      return SafeFuture.COMPLETE;
    }

    // We should run Builder flow (blinded) only if we have a validator registration
    final boolean shouldTryBuilderFlow =
        executionPayloadContext
            .map(ExecutionPayloadContext::isValidatorRegistrationPresent)
            .orElse(false);

    final ExecutionPayloadResult executionPayloadResult =
        executionLayerBlockProductionManager.initiateBlockProduction(
            executionPayloadContext.orElseThrow(),
            blockSlotState,
            shouldTryBuilderFlow,
            requestedBuilderBoostFactor,
            blockProductionPerformance);

    return SafeFuture.allOf(
        cacheExecutionPayloadValue(executionPayloadResult, blockSlotState),
        setPayloadOrPayloadHeader(bodyBuilder, executionPayloadResult),
        setKzgCommitments(bodyBuilder, schemaDefinitions, executionPayloadResult),
        setExecutionRequests(bodyBuilder, executionPayloadResult));
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

  private SafeFuture<Void> setPayloadOrPayloadHeader(
      final BeaconBlockBodyBuilder bodyBuilder,
      final ExecutionPayloadResult executionPayloadResult) {

    if (executionPayloadResult.isFromLocalFlow()) {
      // local, non-blinded flow
      return executionPayloadResult
          .getExecutionPayloadFutureFromLocalFlow()
          .orElseThrow()
          .thenAccept(bodyBuilder::executionPayload);
    }
    // builder, blinded flow
    return executionPayloadResult
        .getBuilderBidOrFallbackDataFuture()
        .orElseThrow()
        .thenAccept(
            builderBidOrFallbackData -> {
              // we should try to return unblinded content only if no explicit "blindness" has been
              // requested and builder flow fallbacks to local
              if (builderBidOrFallbackData.getFallbackData().isPresent()) {
                bodyBuilder.executionPayload(
                    builderBidOrFallbackData.getFallbackDataRequired().getExecutionPayload());
              } else {
                final ExecutionPayloadHeader executionPayloadHeader =
                    builderBidOrFallbackData
                        .getBuilderBid()
                        // from the builder bid
                        .map(BuilderBid::getHeader)
                        // from the local fallback
                        .orElseThrow();
                bodyBuilder.executionPayloadHeader(executionPayloadHeader);
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
    final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema =
        SchemaDefinitionsDeneb.required(schemaDefinitions).getBlobKzgCommitmentsSchema();
    final SafeFuture<SszList<SszKZGCommitment>> blobKzgCommitments;
    if (bodyBuilder.supportsCellProofs()) {
      if (executionPayloadResult.isFromLocalFlow()) {
        // local, non-blinded flow
        blobKzgCommitments =
            executionPayloadResult
                .getBlobsCellBundleFutureFromLocalFlow()
                .orElseThrow()
                .thenApply(Optional::orElseThrow)
                .thenApply(blobKzgCommitmentsSchema::createFromBlobsCellBundle);
      } else {
        // builder, blinded flow
        blobKzgCommitments =
            executionPayloadResult
                .getBuilderBidOrFallbackDataFuture()
                .orElseThrow()
                .thenApply(
                    builderBidOrFallbackData ->
                        getBlobKzgCommitmentsFromBuilderFlowFulu(
                            builderBidOrFallbackData, blobKzgCommitmentsSchema));
      }
    } else {
      if (executionPayloadResult.isFromLocalFlow()) {
        // local, non-blinded flow
        blobKzgCommitments =
            executionPayloadResult
                .getBlobsBundleFutureFromLocalFlow()
                .orElseThrow()
                .thenApply(Optional::orElseThrow)
                .thenApply(blobKzgCommitmentsSchema::createFromBlobsBundle);
      } else {
        // builder, blinded flow
        blobKzgCommitments =
            executionPayloadResult
                .getBuilderBidOrFallbackDataFuture()
                .orElseThrow()
                .thenApply(
                    builderBidOrFallbackData ->
                        getBlobKzgCommitmentsFromBuilderFlow(
                            builderBidOrFallbackData, blobKzgCommitmentsSchema));
      }
    }

    return blobKzgCommitments.thenAccept(bodyBuilder::blobKzgCommitments);
  }

  private SszList<SszKZGCommitment> getBlobKzgCommitmentsFromBuilderFlow(
      final BuilderBidOrFallbackData builderBidOrFallbackData,
      final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema) {
    return builderBidOrFallbackData
        .getBuilderBid()
        // from the builder bid
        .map(BuilderBid::getOptionalBlobKzgCommitments)
        // from the local fallback
        .orElseGet(
            () ->
                builderBidOrFallbackData
                    .getFallbackDataRequired()
                    .getBlobsBundle()
                    .map(blobKzgCommitmentsSchema::createFromBlobsBundle))
        .orElseThrow();
  }

  private SszList<SszKZGCommitment> getBlobKzgCommitmentsFromBuilderFlowFulu(
      final BuilderBidOrFallbackData builderBidOrFallbackData,
      final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema) {
    return builderBidOrFallbackData
        .getBuilderBid()
        // from the builder bid
        .map(BuilderBid::getOptionalBlobKzgCommitments)
        // from the local fallback
        .orElseGet(
            () ->
                builderBidOrFallbackData
                    .getFallbackDataRequired()
                    .getBlobsCellBundle()
                    .map(blobKzgCommitmentsSchema::createFromBlobsCellBundle))
        .orElseThrow();
  }

  private SafeFuture<Void> setExecutionRequests(
      final BeaconBlockBodyBuilder bodyBuilder,
      final ExecutionPayloadResult executionPayloadResult) {
    if (!bodyBuilder.supportsExecutionRequests()) {
      return SafeFuture.COMPLETE;
    }
    final SafeFuture<ExecutionRequests> executionRequests;
    if (executionPayloadResult.isFromLocalFlow()) {
      // local, non-blinded flow
      executionRequests =
          executionPayloadResult
              .getExecutionRequestsFutureFromLocalFlow()
              .orElseThrow()
              .thenApply(Optional::orElseThrow);
    } else {
      // builder, blinded flow
      executionRequests =
          executionPayloadResult
              .getBuilderBidOrFallbackDataFuture()
              .orElseThrow()
              .thenApply(this::getExecutionRequestsFromBuilderFlow);
    }

    return executionRequests.thenAccept(bodyBuilder::executionRequests);
  }

  private ExecutionRequests getExecutionRequestsFromBuilderFlow(
      final BuilderBidOrFallbackData builderBidOrFallbackData) {
    return builderBidOrFallbackData
        .getBuilderBid()
        // from the builder bid
        .flatMap(BuilderBid::getOptionalExecutionRequests)
        // from the local fallback
        .or(() -> builderBidOrFallbackData.getFallbackDataRequired().getExecutionRequests())
        .orElseThrow();
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
                    .thenApply(this::getExecutionPayloadFromBuilderFlow));
      }
    };
  }

  private ExecutionPayload getExecutionPayloadFromBuilderFlow(
      final BuilderPayloadOrFallbackData builderPayloadOrFallbackData) {
    return builderPayloadOrFallbackData
        .getBuilderPayload()
        // from the builder payload
        .map(BuilderPayload::getExecutionPayload)
        // from the local fallback
        .orElseGet(
            () -> builderPayloadOrFallbackData.getFallbackDataRequired().getExecutionPayload());
  }

  public Function<BeaconBlock, SafeFuture<BlobsBundle>> createBlobsBundleSelector() {
    return block -> {
      final UInt64 slot = block.getSlot();
      final ExecutionPayloadResult executionPayloadResult =
          executionLayerBlockProductionManager
              .getCachedPayloadResult(slot)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "ExecutionPayloadResult hasn't been cached for slot " + slot));

      if (executionPayloadResult.isFromLocalFlow()) {
        // we performed a non-blinded flow, so the bundle must be in
        // getBlobsBundleFutureFromNonBlindedFlow
        return executionPayloadResult
            .getBlobsBundleFutureFromLocalFlow()
            .orElseThrow()
            .thenApply(Optional::orElseThrow);
      } else {
        // we performed a blinded flow, so the bundle must be in the FallbackData in
        // getBuilderBidOrFallbackDataFuture
        return executionPayloadResult
            .getBuilderBidOrFallbackDataFuture()
            .orElseThrow()
            .thenApply(
                builderBidOrFallbackData ->
                    builderBidOrFallbackData.getFallbackDataRequired().getBlobsBundle())
            .thenApply(Optional::orElseThrow);
      }
    };
  }

  public Function<BeaconBlock, SafeFuture<BlobsCellBundle>> createBlobsCellBundleSelector() {
    return block -> {
      final UInt64 slot = block.getSlot();
      final ExecutionPayloadResult executionPayloadResult =
          executionLayerBlockProductionManager
              .getCachedPayloadResult(slot)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "ExecutionPayloadResult hasn't been cached for slot " + slot));

      if (executionPayloadResult.isFromLocalFlow()) {
        // we performed a non-blinded flow, so the bundle must be in
        // getBlobsBundleFutureFromNonBlindedFlow
        return executionPayloadResult
            .getBlobsCellBundleFutureFromLocalFlow()
            .orElseThrow()
            .thenApply(Optional::orElseThrow);
      } else {
        // we performed a blinded flow, so the bundle must be in the FallbackData in
        return executionPayloadResult
            .getBuilderBidOrFallbackDataFuture()
            .orElseThrow()
            .thenApply(
                builderBidOrFallbackData ->
                    builderBidOrFallbackData.getFallbackDataRequired().getBlobsCellBundle())
            .thenApply(Optional::orElseThrow);
      }
    };
  }

  public Function<SignedBlockContainer, List<BlobSidecar>> createBlobSidecarsSelector() {
    return blockContainer -> {
      final UInt64 slot = blockContainer.getSlot();
      final SignedBeaconBlock block = blockContainer.getSignedBlock();

      final SszList<Blob> blobs;
      final SszList<SszKZGProof> proofs;

      if (blockContainer.isBlinded()) {
        // need to use the builder BlobsBundle or the local fallback for the blinded flow, because
        // the blobs and the proofs wouldn't be part of the BlockContainer.
        final BuilderPayloadOrFallbackData builderPayloadOrFallbackData =
            executionLayerBlockProductionManager
                .getCachedUnblindedPayload(slot)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "BuilderPayloadOrFallbackData hasn't been cached for slot " + slot));

        final Optional<BuilderPayload> maybeBuilderPayload =
            builderPayloadOrFallbackData.getBuilderPayload();

        if (maybeBuilderPayload.isPresent()) {
          // from the builder payload
          final BlobsBundleDeneb blobsBundle =
              maybeBuilderPayload.get().getOptionalBlobsBundle().orElseThrow();
          // consistency checks because the BlobsBundle comes from an external source (a builder)
          verifyBuilderBlobsBundle(blobsBundle, block);
          blobs = blobsBundle.getBlobs();
          proofs = blobsBundle.getProofs();
        } else {
          // from the local fallback
          final BlobsBundle blobsBundle =
              builderPayloadOrFallbackData.getFallbackDataRequired().getBlobsBundle().orElseThrow();
          final BlockContentsWithBlobsSchema<?> blockContentsSchema =
              SchemaDefinitionsDeneb.required(spec.atSlot(slot).getSchemaDefinitions())
                  .getBlockContentsSchema();
          blobs = blockContentsSchema.getBlobsSchema().createFromElements(blobsBundle.getBlobs());
          proofs =
              blockContentsSchema
                  .getKzgProofsSchema()
                  .createFromElements(
                      blobsBundle.getProofs().stream().map(SszKZGProof::new).toList());
        }

      } else {
        blobs = blockContainer.getBlobs().orElseThrow();
        proofs = blockContainer.getKzgProofs().orElseThrow();
      }

      final MiscHelpersDeneb miscHelpersDeneb =
          MiscHelpersDeneb.required(spec.atSlot(slot).miscHelpers());

      return IntStream.range(0, blobs.size())
          .mapToObj(
              index ->
                  miscHelpersDeneb.constructBlobSidecar(
                      block, UInt64.valueOf(index), blobs.get(index), proofs.get(index)))
          .toList();
    };
  }

  public Function<SignedBlockContainer, List<DataColumnSidecar>> createDataColumnSidecarsSelector(
      final KZG kzg) {
    return blockContainer -> {
      final UInt64 slot = blockContainer.getSlot();
      final SignedBeaconBlock block = blockContainer.getSignedBlock();

      final SszList<Blob> blobs;
      final SszList<SszKZGProof> proofs;

      if (blockContainer.isBlinded()) {
        // need to use the builder BlobsBundle or the local fallback for the blinded flow, because
        // the blobs and the proofs wouldn't be part of the BlockContainer.
        final BuilderPayloadOrFallbackData builderPayloadOrFallbackData =
            executionLayerBlockProductionManager
                .getCachedUnblindedPayload(slot)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "BuilderPayloadOrFallbackData hasn't been cached for slot " + slot));

        final Optional<BuilderPayload> maybeBuilderPayload =
            builderPayloadOrFallbackData.getBuilderPayload();

        if (maybeBuilderPayload.isPresent()) {
          // from the builder payload
          final BlobsBundleFulu blobsCellBundle =
              maybeBuilderPayload.get().getOptionalBlobsCellBundle().orElseThrow();
          // consistency checks because the BlobsBundle comes from an external source (a builder)
          verifyBuilderBlobsCellBundle(blobsCellBundle, block);
          blobs = blobsCellBundle.getBlobs();
          proofs = blobsCellBundle.getProofs();
        } else {
          // from the local fallback
          final BlobsCellBundle blobsCellBundle =
              builderPayloadOrFallbackData
                  .getFallbackDataRequired()
                  .getBlobsCellBundle()
                  .orElseThrow();
          final BlockContentsWithBlobsSchema<?> blockContentsSchema =
              SchemaDefinitionsFulu.required(spec.atSlot(slot).getSchemaDefinitions())
                  .getBlockContentsSchema();
          blobs =
              blockContentsSchema.getBlobsSchema().createFromElements(blobsCellBundle.getBlobs());
          proofs =
              blockContentsSchema
                  .getKzgProofsSchema()
                  .createFromElements(
                      blobsCellBundle.getProofs().stream().map(SszKZGProof::new).toList());
        }

      } else {
        blobs = blockContainer.getBlobs().orElseThrow();
        proofs = blockContainer.getKzgProofs().orElseThrow();
      }

      final MiscHelpersFulu miscHelpersFulu =
          MiscHelpersFulu.required(spec.atSlot(blockContainer.getSlot()).miscHelpers());
      final SpecConfigFulu specConfigFulu =
          SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());

      final List<BlobAndCellProofs> blobAndCellProofsList =
          IntStream.range(0, blobs.size())
              .mapToObj(
                  index ->
                      new BlobAndCellProofs(
                          blobs.get(index),
                          proofs.stream()
                              .skip((long) index * specConfigFulu.getNumberOfColumns())
                              .limit(specConfigFulu.getNumberOfColumns())
                              .map(SszKZGProof::getKZGProof)
                              .toList()))
              .toList();

      return miscHelpersFulu.constructDataColumnSidecars(
          blockContainer.getSignedBlock(), blobAndCellProofsList, kzg);
    };
  }

  private void verifyBuilderBlobsBundle(
      final BlobsBundleDeneb blobsBundle, final SignedBeaconBlock block) {
    final SszList<SszKZGCommitment> blockCommitments =
        block.getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow();
    checkState(
        blobsBundle.getCommitments().hashTreeRoot().equals(blockCommitments.hashTreeRoot()),
        "Commitments in the builder BlobsBundle don't match the commitments in the block");
    checkState(
        blockCommitments.size() == blobsBundle.getProofs().size(),
        "The number of proofs in the builder BlobsBundle doesn't match the number of commitments in the block");
    checkState(
        blockCommitments.size() == blobsBundle.getBlobs().size(),
        "The number of blobs in the builder BlobsBundle doesn't match the number of commitments in the block");
  }

  private void verifyBuilderBlobsCellBundle(
      final BlobsBundleFulu blobsCellBundle, final SignedBeaconBlock block) {
    final SszList<SszKZGCommitment> blockCommitments =
        block.getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow();
    checkState(
        blobsCellBundle.getCommitments().hashTreeRoot().equals(blockCommitments.hashTreeRoot()),
        "Commitments in the builder BlobsBundle don't match the commitments in the block");
    checkState(
        blockCommitments.size() == blobsCellBundle.getBlobs().size(),
        "The number of blobs in the builder BlobsBundle doesn't match the number of commitments in the block");
  }
}
