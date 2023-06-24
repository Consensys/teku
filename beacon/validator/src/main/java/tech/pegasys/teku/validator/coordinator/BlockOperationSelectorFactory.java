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

package tech.pegasys.teku.validator.coordinator;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.SignedBlobSidecarsUnblinder;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.builder.BlindedBlobsBundle;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.HeaderWithFallbackData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
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

  public Consumer<BeaconBlockBodyBuilder> createSelector(
      final Bytes32 parentRoot,
      final BeaconState blockSlotState,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti) {
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
      if (bodyBuilder.supportsExecutionPayload()) {
        final SchemaDefinitions schemaDefinitions =
            spec.atSlot(blockSlotState.getSlot()).getSchemaDefinitions();
        setExecutionData(bodyBuilder, schemaDefinitions, parentRoot, blockSlotState);
      }
    };
  }

  private void setExecutionData(
      final BeaconBlockBodyBuilder bodyBuilder,
      final SchemaDefinitions schemaDefinitions,
      final Bytes32 parentRoot,
      final BeaconState blockSlotState) {
    final SafeFuture<Optional<ExecutionPayloadContext>> executionPayloadContextFuture =
        forkChoiceNotifier.getPayloadId(parentRoot, blockSlotState.getSlot());

    // Pre-Deneb: Execution Payload / Execution Payload Header
    if (!bodyBuilder.supportsKzgCommitments()) {
      if (bodyBuilder.isBlinded()) {
        builderSetPayloadHeader(
            bodyBuilder, schemaDefinitions, blockSlotState, executionPayloadContextFuture);
      } else {
        builderSetPayload(
            bodyBuilder, schemaDefinitions, blockSlotState, executionPayloadContextFuture);
      }
      return;
    }

    // Post-Deneb: Execution Payload / Execution Payload Header + KZG Commitments
    SafeFuture<ExecutionPayloadResult> executionPayloadResultFuture =
        executionPayloadContextFuture.thenApply(
            executionPayloadContextOptional ->
                executionLayerBlockProductionManager.initiateBlockAndBlobsProduction(
                    // kzg commitments are supported: we should have already merged by now, so we
                    // can safely assume we have an executionPayloadContext
                    executionPayloadContextOptional.orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Cannot provide kzg commitments before The Merge")),
                    blockSlotState,
                    bodyBuilder.isBlinded()));
    builderSetPayloadPostMerge(bodyBuilder, executionPayloadResultFuture);
    builderSetKzgCommitments(bodyBuilder, schemaDefinitions, executionPayloadResultFuture);
  }

  private void builderSetPayloadPostMerge(
      final BeaconBlockBodyBuilder bodyBuilder,
      final SafeFuture<ExecutionPayloadResult> executionPayloadResultFuture) {
    if (bodyBuilder.isBlinded()) {
      bodyBuilder.executionPayloadHeader(
          executionPayloadResultFuture.thenCompose(
              executionPayloadResult ->
                  executionPayloadResult
                      .getHeaderWithFallbackDataFuture()
                      .orElseThrow()
                      .thenApply(HeaderWithFallbackData::getExecutionPayloadHeader)));
    } else {
      bodyBuilder.executionPayload(
          executionPayloadResultFuture.thenCompose(
              executionPayloadResult ->
                  executionPayloadResult.getExecutionPayloadFuture().orElseThrow()));
    }
  }

  private void builderSetPayload(
      final BeaconBlockBodyBuilder bodyBuilder,
      final SchemaDefinitions schemaDefinitions,
      final BeaconState blockSlotState,
      final SafeFuture<Optional<ExecutionPayloadContext>> executionPayloadContextFuture) {
    final Supplier<SafeFuture<ExecutionPayload>> preMergePayload =
        () ->
            SafeFuture.completedFuture(
                SchemaDefinitionsBellatrix.required(schemaDefinitions)
                    .getExecutionPayloadSchema()
                    .getDefault());

    bodyBuilder.executionPayload(
        executionPayloadContextFuture.thenCompose(
            executionPayloadContext ->
                executionPayloadContext
                    .map(
                        payloadContext ->
                            executionLayerBlockProductionManager
                                .initiateBlockProduction(payloadContext, blockSlotState, false)
                                .getExecutionPayloadFuture()
                                .orElseThrow())
                    .orElseGet(preMergePayload)));
  }

  private void builderSetPayloadHeader(
      final BeaconBlockBodyBuilder bodyBuilder,
      final SchemaDefinitions schemaDefinitions,
      final BeaconState blockSlotState,
      final SafeFuture<Optional<ExecutionPayloadContext>> executionPayloadContextFuture) {
    final Supplier<SafeFuture<ExecutionPayloadHeader>> preMergePayloadHeader =
        () ->
            SafeFuture.completedFuture(
                SchemaDefinitionsBellatrix.required(schemaDefinitions)
                    .getExecutionPayloadHeaderSchema()
                    .getHeaderOfDefaultPayload());

    bodyBuilder.executionPayloadHeader(
        executionPayloadContextFuture.thenCompose(
            executionPayloadContext -> {
              if (executionPayloadContext.isEmpty()) {
                return preMergePayloadHeader.get();
              } else {
                return executionLayerBlockProductionManager
                    .initiateBlockProduction(executionPayloadContext.get(), blockSlotState, true)
                    .getHeaderWithFallbackDataFuture()
                    .orElseThrow()
                    .thenApply(HeaderWithFallbackData::getExecutionPayloadHeader);
              }
            }));
  }

  private void builderSetKzgCommitments(
      final BeaconBlockBodyBuilder bodyBuilder,
      final SchemaDefinitions schemaDefinitions,
      final SafeFuture<ExecutionPayloadResult> executionPayloadResultFuture) {
    final SchemaDefinitionsDeneb schemaDefinitionsDeneb =
        SchemaDefinitionsDeneb.required(schemaDefinitions);
    final SafeFuture<SszList<SszKZGCommitment>> blobKzgCommitments =
        executionPayloadResultFuture.thenCompose(
            executionPayloadResult -> {
              final SszListSchema<SszKZGCommitment, ?> blobKzgCommitmentsSchema =
                  schemaDefinitionsDeneb
                      .getBeaconBlockBodySchema()
                      .toVersionDeneb()
                      .orElseThrow()
                      .getBlobKzgCommitmentsSchema();
              if (bodyBuilder.isBlinded()) {
                return getBlindedBlobsBundle(executionPayloadResult)
                    .thenApply(
                        blindedBlobsBundle ->
                            blobKzgCommitmentsSchema.createFromElements(
                                blindedBlobsBundle.getCommitments().asList()));
              } else {
                return getBlobsBundle(executionPayloadResult)
                    .thenApply(
                        blobsBundle ->
                            blobKzgCommitmentsSchema.createFromElements(
                                blobsBundle.getCommitments().stream()
                                    .map(SszKZGCommitment::new)
                                    .collect(Collectors.toList())));
              }
            });
    bodyBuilder.blobKzgCommitments(blobKzgCommitments);
  }

  public Consumer<SignedBeaconBlockUnblinder> createBlockUnblinderSelector() {
    return bodyUnblinder -> {
      final SignedBlockContainer signedBlindedBlockContainer =
          bodyUnblinder.getSignedBlindedBlockContainer();

      final BeaconBlock block = signedBlindedBlockContainer.getSignedBlock().getMessage();

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
                    .getUnblindedPayload(signedBlindedBlockContainer)
                    .thenApply(BuilderPayload::getExecutionPayload));
      }
    };
  }

  public Consumer<SignedBlobSidecarsUnblinder> createBlobSidecarsUnblinderSelector(
      final UInt64 slot) {
    return blobSidecarsUnblinder ->
        blobSidecarsUnblinder.setBlobsBundleSupplier(
            () -> {
              final BuilderPayload cachedBuilderPayload =
                  executionLayerBlockProductionManager
                      .getCachedUnblindedPayload(slot)
                      .orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "BuilderPayload hasn't been cached for slot " + slot));
              final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle =
                  cachedBuilderPayload
                      .getOptionalBlobsBundle()
                      .orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "BlobsBundle is not available in BuilderPayload: "
                                      + cachedBuilderPayload));
              return SafeFuture.completedFuture(blobsBundle);
            });
  }

  public Function<BeaconBlock, SafeFuture<List<BlobSidecar>>> createBlobSidecarsSelector() {
    return block -> {
      final BlobSidecarSchema blobSidecarSchema =
          SchemaDefinitionsDeneb.required(spec.atSlot(block.getSlot()).getSchemaDefinitions())
              .getBlobSidecarSchema();
      return getCachedBlobsBundle(block.getSlot())
          .thenApply(
              blobsBundle ->
                  IntStream.range(0, blobsBundle.getNumberOfBlobs())
                      .mapToObj(
                          index ->
                              blobSidecarSchema.create(
                                  block.getRoot(),
                                  UInt64.valueOf(index),
                                  block.getSlot(),
                                  block.getParentRoot(),
                                  block.getProposerIndex(),
                                  blobsBundle.getBlobs().get(index),
                                  blobsBundle.getCommitments().get(index),
                                  blobsBundle.getProofs().get(index)))
                      .collect(Collectors.toUnmodifiableList()));
    };
  }

  public Function<BeaconBlock, SafeFuture<List<BlindedBlobSidecar>>>
      createBlindedBlobSidecarsSelector() {
    return block -> {
      final BlindedBlobSidecarSchema blindedBlobSidecarSchema =
          SchemaDefinitionsDeneb.required(spec.atSlot(block.getSlot()).getSchemaDefinitions())
              .getBlindedBlobSidecarSchema();
      return getCachedBlindedBlobsBundle(block.getSlot())
          .thenApply(
              blindedBlobsBundle ->
                  IntStream.range(0, blindedBlobsBundle.getNumberOfBlobs())
                      .mapToObj(
                          index ->
                              blindedBlobSidecarSchema.create(
                                  block.getRoot(),
                                  UInt64.valueOf(index),
                                  block.getSlot(),
                                  block.getParentRoot(),
                                  block.getProposerIndex(),
                                  blindedBlobsBundle.getBlobRoots().get(index).get(),
                                  blindedBlobsBundle.getCommitments().get(index).getKZGCommitment(),
                                  blindedBlobsBundle.getProofs().get(index).getKZGProof()))
                      .collect(Collectors.toUnmodifiableList()));
    };
  }

  private SafeFuture<BlobsBundle> getBlobsBundle(
      final ExecutionPayloadResult executionPayloadResult) {
    return executionPayloadResult
        .getBlobsBundleFuture()
        .orElseThrow(() -> blobsBundleIsNotAvailableException(false))
        .thenApply(
            blobsBundle ->
                blobsBundle.orElseThrow(() -> blobsBundleIsNotAvailableException(false)));
  }

  private SafeFuture<BlindedBlobsBundle> getBlindedBlobsBundle(
      final ExecutionPayloadResult executionPayloadResult) {
    return executionPayloadResult
        .getHeaderWithFallbackDataFuture()
        .orElseThrow(() -> new IllegalStateException("HeaderWithFallbackData is not available"))
        .thenApply(
            headerWithFallbackData ->
                headerWithFallbackData
                    .getBlindedBlobsBundle()
                    .orElseThrow(() -> blobsBundleIsNotAvailableException(true)));
  }

  private SafeFuture<BlobsBundle> getCachedBlobsBundle(final UInt64 slot) {
    final ExecutionPayloadResult executionPayloadResult = getCachedPayloadResult(slot);
    return getBlobsBundle(executionPayloadResult);
  }

  private SafeFuture<BlindedBlobsBundle> getCachedBlindedBlobsBundle(final UInt64 slot) {
    final ExecutionPayloadResult executionPayloadResult = getCachedPayloadResult(slot);
    return getBlindedBlobsBundle(executionPayloadResult);
  }

  private ExecutionPayloadResult getCachedPayloadResult(final UInt64 slot) {
    return executionLayerBlockProductionManager
        .getCachedPayloadResult(slot)
        .orElseThrow(() -> new IllegalStateException("ExecutionPayloadResult is not available"));
  }

  private IllegalStateException blobsBundleIsNotAvailableException(final boolean blinded) {
    return new IllegalStateException((blinded ? "Blinded" : "") + "BlobsBundle is not available");
  }
}
