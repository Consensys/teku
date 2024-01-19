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

import com.google.common.base.Preconditions;
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
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
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
  private final Bytes32 graffiti;
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
      final Bytes32 graffiti,
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
    this.graffiti = graffiti;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.executionLayerBlockProductionManager = executionLayerBlockProductionManager;
  }

  public Function<BeaconBlockBodyBuilder, SafeFuture<Void>> createSelector(
      final Bytes32 parentRoot,
      final BeaconState blockSlotState,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti,
      final Optional<Boolean> requestedBlinded,
      final BlockProductionPerformance blockProductionPerformance) {

    return bodyBuilder -> {
      final Eth1Data eth1Data = eth1DataCache.getEth1Vote(blockSlotState);

      final SszList<Attestation> attestations =
          attestationPool.getAttestationsForBlock(
              blockSlotState,
              new AttestationForkChecker(spec, blockSlotState),
              spec.createAttestationWorthinessChecker(blockSlotState));

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
          .graffiti(optionalGraffiti.orElse(graffiti))
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
      final SafeFuture<Void> completionFuture;
      if (bodyBuilder.supportsExecutionPayload()) {
        final SchemaDefinitions schemaDefinitions =
            spec.atSlot(blockSlotState.getSlot()).getSchemaDefinitions();

        completionFuture =
            forkChoiceNotifier
                .getPayloadId(parentRoot, blockSlotState.getSlot())
                .thenCompose(
                    executionPayloadContext ->
                        setExecutionData(
                            executionPayloadContext,
                            bodyBuilder,
                            requestedBlinded,
                            schemaDefinitions,
                            blockSlotState,
                            blockProductionPerformance));
      } else {
        completionFuture = SafeFuture.COMPLETE;
      }

      blockProductionPerformance.beaconBlockPrepared();

      return completionFuture;
    };
  }

  private SafeFuture<Void> setExecutionData(
      final Optional<ExecutionPayloadContext> executionPayloadContext,
      final BeaconBlockBodyBuilder bodyBuilder,
      final Optional<Boolean> requestedBlinded,
      final SchemaDefinitions schemaDefinitions,
      final BeaconState blockSlotState,
      final BlockProductionPerformance blockProductionPerformance) {

    // if blinded flow is explicitly requested, we should try to use it
    // otherwise, we should use it only if we have a validator registration
    final boolean shouldTryBuilderFlow =
        requestedBlinded.orElse(true)
            || executionPayloadContext
                .map(ExecutionPayloadContext::isValidatorRegistrationPresent)
                .orElse(false);

    // we should try to return unblinded content only if we try the builder flow with no explicit
    // request
    final boolean setUnblindedContentIfPossible =
        shouldTryBuilderFlow && requestedBlinded.isEmpty();

    // Pre-Deneb: Execution Payload / Execution Payload Header
    if (!bodyBuilder.supportsKzgCommitments()) {
      if (shouldTryBuilderFlow) {
        return builderSetPayloadHeader(
            setUnblindedContentIfPossible,
            bodyBuilder,
            schemaDefinitions,
            blockSlotState,
            executionPayloadContext,
            blockProductionPerformance);
      } else {
        return builderSetPayload(
            bodyBuilder,
            schemaDefinitions,
            blockSlotState,
            executionPayloadContext,
            blockProductionPerformance);
      }
    }

    // Post-Deneb: Execution Payload / Execution Payload Header + KZG Commitments
    final ExecutionPayloadResult executionPayloadResult =
        executionLayerBlockProductionManager.initiateBlockAndBlobsProduction(
            // kzg commitments are supported: we should have already merged by now, so we
            // can safely assume we have an executionPayloadContext
            executionPayloadContext.orElseThrow(
                () -> new IllegalStateException("Cannot provide kzg commitments before The Merge")),
            blockSlotState,
            shouldTryBuilderFlow,
            blockProductionPerformance);

    return SafeFuture.allOf(
        builderSetPayloadPostMerge(
            bodyBuilder, setUnblindedContentIfPossible, executionPayloadResult),
        builderSetKzgCommitments(
            bodyBuilder, setUnblindedContentIfPossible, schemaDefinitions, executionPayloadResult));
  }

  private SafeFuture<Void> builderSetPayloadPostMerge(
      final BeaconBlockBodyBuilder bodyBuilder,
      final boolean setUnblindedPayloadIfPossible,
      final ExecutionPayloadResult executionPayloadResult) {

    if (executionPayloadResult.getExecutionPayloadFuture().isPresent()) {
      // local flow
      return executionPayloadResult
          .getExecutionPayloadFuture()
          .get()
          .thenAccept(bodyBuilder::executionPayload);
    }

    // builder flow
    return executionPayloadResult
        .getHeaderWithFallbackDataFuture()
        .orElseThrow()
        .thenAccept(
            headerWithFallbackData -> {
              if (setUnblindedPayloadIfPossible
                  && headerWithFallbackData.getFallbackData().isPresent()) {
                bodyBuilder.executionPayload(
                    headerWithFallbackData.getFallbackData().get().getExecutionPayload());
              } else {
                bodyBuilder.executionPayloadHeader(
                    headerWithFallbackData.getExecutionPayloadHeader());
              }
            });
  }

  private SafeFuture<Void> builderSetPayload(
      final BeaconBlockBodyBuilder bodyBuilder,
      final SchemaDefinitions schemaDefinitions,
      final BeaconState blockSlotState,
      final Optional<ExecutionPayloadContext> executionPayloadContext,
      final BlockProductionPerformance blockProductionPerformance) {
    if (executionPayloadContext.isEmpty()) {
      // preMergePayload
      bodyBuilder.executionPayload(
          SchemaDefinitionsBellatrix.required(schemaDefinitions)
              .getExecutionPayloadSchema()
              .getDefault());
      return SafeFuture.COMPLETE;
    }
    return executionLayerBlockProductionManager
        .initiateBlockProduction(
            executionPayloadContext.get(), blockSlotState, false, blockProductionPerformance)
        .getExecutionPayloadFuture()
        .orElseThrow()
        .thenAccept(bodyBuilder::executionPayload);
  }

  private SafeFuture<Void> builderSetPayloadHeader(
      final boolean setUnblindedPayloadIfPossible,
      final BeaconBlockBodyBuilder bodyBuilder,
      final SchemaDefinitions schemaDefinitions,
      final BeaconState blockSlotState,
      final Optional<ExecutionPayloadContext> executionPayloadContext,
      final BlockProductionPerformance blockProductionPerformance) {
    if (executionPayloadContext.isEmpty()) {
      // preMergePayloadHeader
      bodyBuilder.executionPayloadHeader(
          SchemaDefinitionsBellatrix.required(schemaDefinitions)
              .getExecutionPayloadHeaderSchema()
              .getHeaderOfDefaultPayload());
      return SafeFuture.COMPLETE;
    }

    return executionLayerBlockProductionManager
        .initiateBlockProduction(
            executionPayloadContext.get(), blockSlotState, true, blockProductionPerformance)
        .getHeaderWithFallbackDataFuture()
        .orElseThrow()
        .thenAccept(
            headerWithFallbackData -> {
              if (setUnblindedPayloadIfPossible
                  && headerWithFallbackData.getFallbackData().isPresent()) {
                bodyBuilder.executionPayload(
                    headerWithFallbackData.getFallbackData().get().getExecutionPayload());
                return;
              }
              bodyBuilder.executionPayloadHeader(
                  headerWithFallbackData.getExecutionPayloadHeader());
            });
  }

  private SafeFuture<Void> builderSetKzgCommitments(
      final BeaconBlockBodyBuilder bodyBuilder,
      final boolean setUnblindedPayloadIfPossible,
      final SchemaDefinitions schemaDefinitions,
      final ExecutionPayloadResult executionPayloadResult) {
    final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema =
        SchemaDefinitionsDeneb.required(schemaDefinitions).getBlobKzgCommitmentsSchema();

    if (executionPayloadResult.getExecutionPayloadFuture().isPresent()) {
      // local flow
      return getExecutionBlobsBundle(executionPayloadResult)
          .thenApply(blobKzgCommitmentsSchema::createFromBlobsBundle)
          .thenAccept(bodyBuilder::blobKzgCommitments);
    }

    // builder flow
    return getBuilderBlobKzgCommitments(
            blobKzgCommitmentsSchema, executionPayloadResult, setUnblindedPayloadIfPossible)
        .thenAccept(bodyBuilder::blobKzgCommitments);
  }

  public Consumer<SignedBeaconBlockUnblinder> createBlockUnblinderSelector() {
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
                    .getUnblindedPayload(signedBlindedBlock)
                    .thenApply(BuilderPayload::getExecutionPayload));
      }
    };
  }

  public Function<BeaconBlock, SafeFuture<BlobsBundle>> createBlobsBundleSelector() {
    return block -> getCachedExecutionBlobsBundle(block.getSlot());
  }

  public Function<SignedBlockContainer, List<BlobSidecar>> createBlobSidecarsSelector() {
    return blockContainer -> {
      final UInt64 slot = blockContainer.getSlot();
      final SignedBeaconBlock block = blockContainer.getSignedBlock();

      final MiscHelpersDeneb miscHelpersDeneb =
          MiscHelpersDeneb.required(spec.atSlot(slot).miscHelpers());

      final SszList<Blob> blobs;
      final SszList<SszKZGProof> proofs;

      if (blockContainer.isBlinded()) {
        // need to use the builder BlobsBundle for the blinded flow, because the
        // blobs and the proofs wouldn't be part of the BlockContainer
        final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle =
            getCachedBuilderBlobsBundle(slot);
        // consistency check because the BlobsBundle comes from an external source (a builder)
        final SszList<SszKZGCommitment> blockCommitments =
            block.getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow();
        Preconditions.checkState(
            blobsBundle.getCommitments().hashTreeRoot().equals(blockCommitments.hashTreeRoot()),
            "Commitments in the builder BlobsBundle don't match the commitments in the block");
        blobs = blobsBundle.getBlobs();
        proofs = blobsBundle.getProofs();
      } else {
        blobs = blockContainer.getBlobs().orElseThrow();
        proofs = blockContainer.getKzgProofs().orElseThrow();
      }

      return IntStream.range(0, blobs.size())
          .mapToObj(
              index ->
                  miscHelpersDeneb.constructBlobSidecar(
                      block, UInt64.valueOf(index), blobs.get(index), proofs.get(index)))
          .toList();
    };
  }

  private SafeFuture<BlobsBundle> getExecutionBlobsBundle(
      final ExecutionPayloadResult executionPayloadResult) {
    return executionPayloadResult
        .getBlobsBundleFuture()
        .orElseThrow(this::executionBlobsBundleIsNotAvailableException)
        .thenApply(
            blobsBundle ->
                blobsBundle.orElseThrow(this::executionBlobsBundleIsNotAvailableException));
  }

  private SafeFuture<BlobsBundle> getCachedExecutionBlobsBundle(final UInt64 slot) {
    return executionLayerBlockProductionManager
        .getCachedPayloadResult(slot)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "ExecutionPayloadResult hasn't been cached for slot " + slot))
        .getBlobsBundleFuture()
        .orElseThrow(() -> executionBlobsBundleIsNotAvailableException(slot))
        .thenApply(
            blobsBundle ->
                blobsBundle.orElseThrow(() -> executionBlobsBundleIsNotAvailableException(slot)));
  }

  private IllegalStateException executionBlobsBundleIsNotAvailableException() {
    return new IllegalStateException("execution BlobsBundle is not available");
  }

  private IllegalStateException executionBlobsBundleIsNotAvailableException(final UInt64 slot) {
    return new IllegalStateException("execution BlobsBundle is not available for slot " + slot);
  }

  private SafeFuture<SszList<SszKZGCommitment>> getBuilderBlobKzgCommitments(
      final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema,
      final ExecutionPayloadResult executionPayloadResult,
      final boolean setUnblindedPayloadIfPossible) {

    return executionPayloadResult
        .getHeaderWithFallbackDataFuture()
        .orElseThrow()
        .thenApply(
            headerWithFallbackData -> {
              if (setUnblindedPayloadIfPossible
                  && headerWithFallbackData.getFallbackData().isPresent()) {
                return blobKzgCommitmentsSchema.createFromBlobsBundle(
                    headerWithFallbackData.getFallbackData().get().getBlobsBundle().orElseThrow());
              }
              return headerWithFallbackData
                  .getBlobKzgCommitments()
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "builder BlobKzgCommitments are not available"));
            });
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
