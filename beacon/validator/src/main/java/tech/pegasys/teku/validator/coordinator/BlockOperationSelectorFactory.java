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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.Blob;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.versions.eip4844.helpers.MiscHelpersEip4844;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip4844;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationForkChecker;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.validation.BlobsBundleValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

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
  private final Optional<BlobsBundleValidator> blobsBundleValidator;
  private final ForkChoiceNotifier forkChoiceNotifier;
  private final ExecutionLayerChannel executionLayerChannel;
  private volatile Optional<Pair<BlobsSidecar, ExecutionPayloadSummary>> blobsSidecarCache;

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
      final Optional<BlobsBundleValidator> blobsBundleValidator,
      final ForkChoiceNotifier forkChoiceNotifier,
      final ExecutionLayerChannel executionLayerChannel) {
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
    this.blobsBundleValidator = blobsBundleValidator;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.executionLayerChannel = executionLayerChannel;
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

      final SpecVersion specVersion = spec.atSlot(blockSlotState.getSlot());
      bodyBuilder
          .randaoReveal(randaoReveal)
          .eth1Data(eth1Data)
          .graffiti(optionalGraffiti.orElse(graffiti))
          .attestations(attestations)
          .proposerSlashings(proposerSlashings)
          .attesterSlashings(attesterSlashings)
          .deposits(depositProvider.getDeposits(blockSlotState, eth1Data))
          .voluntaryExits(voluntaryExits)
          .syncAggregate(
              () ->
                  contributionPool.createSyncAggregateForBlock(
                      blockSlotState.getSlot(), parentRoot))
          .blsToExecutionChanges(() -> blsToExecutionChangePool.getItemsForBlock(blockSlotState));

      final PayloadCache payloadCache = new PayloadCache();
      // execution payload handling
      if (bodyBuilder.isBlinded()) {
        // an execution payload header is required
        bodyBuilder.executionPayloadHeader(
            payloadProvider(
                parentRoot,
                blockSlotState,
                () ->
                    SchemaDefinitionsBellatrix.required(
                            spec.atSlot(blockSlotState.getSlot()).getSchemaDefinitions())
                        .getExecutionPayloadHeaderSchema()
                        .getHeaderOfDefaultPayload(),
                (executionPayloadContext) -> {
                  final SafeFuture<ExecutionPayloadHeader> executionPayloadHeaderSafeFuture =
                      executionLayerChannel
                          .builderGetHeader(executionPayloadContext, blockSlotState)
                          .thenApply(
                              executionPayloadHeader -> {
                                payloadCache.payloadId = executionPayloadContext.getPayloadId();
                                return executionPayloadHeader;
                              });
                  payloadCache.initializer = Optional.of(executionPayloadHeaderSafeFuture);
                  return executionPayloadHeaderSafeFuture;
                }));
      } else {
        // non-blinded body requested: a full execution payload is required
        bodyBuilder.executionPayload(
            payloadProvider(
                parentRoot,
                blockSlotState,
                () ->
                    SchemaDefinitionsBellatrix.required(specVersion.getSchemaDefinitions())
                        .getExecutionPayloadSchema()
                        .getDefault(),
                (executionPayloadContext) -> {
                  final SafeFuture<ExecutionPayload> executionPayloadSafeFuture =
                      executionLayerChannel
                          .engineGetPayload(executionPayloadContext, blockSlotState.getSlot())
                          .thenApply(
                              executionPayload -> {
                                payloadCache.payloadId = executionPayloadContext.getPayloadId();
                                payloadCache.executionPayload = Optional.of(executionPayload);
                                return executionPayload;
                              });
                  payloadCache.initializer = Optional.of(executionPayloadSafeFuture);
                  return executionPayloadSafeFuture;
                }));
      }

      bodyBuilder.blobKzgCommitments(
          () -> {
            final SchemaDefinitionsEip4844 schemaDefinitionsEip4844 =
                spec.getGenesisSchemaDefinitions().toVersionEip4844().orElseThrow();
            final MiscHelpersEip4844 miscHelpers =
                (MiscHelpersEip4844) spec.forMilestone(SpecMilestone.EIP4844).miscHelpers();
            return payloadCache
                .initializer
                .orElseThrow()
                .thenCompose(
                    __ ->
                        executionLayerChannel
                            .engineGetBlobsBundle(payloadCache.payloadId, blockSlotState.getSlot())
                            .thenApply(
                                blobsBundle -> {
                                  final InternalValidationResult validationResult =
                                      blobsBundleValidator
                                          .orElseThrow()
                                          .validate(blobsBundle, payloadCache.executionPayload);
                                  if (validationResult.isAccept()) {
                                    // FIXME:how is it called? one thread or many, check
                                    final ExecutionPayloadSummary executionPayloadSummary =
                                        payloadCache.executionPayload.isPresent()
                                            ? payloadCache.executionPayload.orElseThrow()
                                            : payloadCache.executionPayloadHeader.orElseThrow();
                                    this.blobsSidecarCache =
                                        Optional.of(
                                            Pair.of(
                                                new BlobsSidecar(
                                                    schemaDefinitionsEip4844
                                                        .getBlobsSidecarSchema(),
                                                    Bytes32.ZERO,
                                                    blockSlotState.getSlot(),
                                                    blobsBundle.getBlobs(),
                                                    miscHelpers.computeAggregatedKzgProof(
                                                        blobsBundle.getBlobs().stream()
                                                            .map(Blob::getBytes)
                                                            .collect(Collectors.toList()))),
                                                executionPayloadSummary));
                                    return schemaDefinitionsEip4844
                                        .getBeaconBlockBodySchema()
                                        .toVersionEip4844()
                                        .orElseThrow()
                                        .getBlobKzgCommitmentsSchema()
                                        .createFromElements(
                                            blobsBundle.getKzgs().stream()
                                                .map(SszKZGCommitment::new)
                                                .collect(Collectors.toList()));
                                  } else {
                                    throw new IllegalArgumentException(
                                        "Blobs bundle validation failed");
                                  }
                                }));
          });
    };
  }

  private <T> Supplier<SafeFuture<T>> payloadProvider(
      final Bytes32 parentRoot,
      final BeaconState blockSlotState,
      Supplier<T> defaultSupplier,
      Function<ExecutionPayloadContext, SafeFuture<T>> supplier) {
    return () ->
        forkChoiceNotifier
            .getPayloadId(parentRoot, blockSlotState.getSlot())
            .thenCompose(
                maybePayloadId -> {
                  if (maybePayloadId.isEmpty()) {
                    // Terminal block not reached, provide default payload
                    return SafeFuture.completedFuture(defaultSupplier.get());
                  } else {
                    return supplier.apply(maybePayloadId.get());
                  }
                });
  }

  public Consumer<SignedBeaconBlockUnblinder> createUnblinderSelector() {
    return bodyUnblinder -> {
      final BeaconBlock block = bodyUnblinder.getSignedBlindedBeaconBlock().getMessage();

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
                executionLayerChannel.builderGetPayload(
                    bodyUnblinder.getSignedBlindedBeaconBlock()));
      }
    };
  }

  // FIXME: I don't like cache being there in Factory class
  public Function<SignedBeaconBlock, SignedBeaconBlockAndBlobsSidecar>
      createSidecarSupplementSelector() {
    return signedBeaconBlock -> {
      final SchemaDefinitionsEip4844 schemaDefinitionsEip4844 =
          spec.getGenesisSchemaDefinitions().toVersionEip4844().orElseThrow();
      if (!blobsSidecarCache
              .orElseThrow()
              .getLeft()
              .getBeaconBlockSlot()
              .equals(signedBeaconBlock.getSlot())
          || !blobsSidecarCache
              .orElseThrow()
              .getRight()
              .getBlockHash()
              .equals(
                  signedBeaconBlock
                      .getMessage()
                      .getBody()
                      .getOptionalExecutionPayloadSummary()
                      .orElseThrow()
                      .getBlockHash())) {
        // TODO: actually it means that we should try to retrieve it from Eth1 again
        throw new IllegalArgumentException("There is no matching BlobsSidecar");
      }
      final SignedBeaconBlockAndBlobsSidecar blockAndBlobsSidecar =
          new SignedBeaconBlockAndBlobsSidecar(
              schemaDefinitionsEip4844.getSignedBeaconBlockAndBlobsSidecarSchema(),
              signedBeaconBlock,
              new BlobsSidecar(
                  schemaDefinitionsEip4844.getBlobsSidecarSchema(),
                  signedBeaconBlock.getRoot(),
                  signedBeaconBlock.getSlot(),
                  blobsSidecarCache.orElseThrow().getLeft().getBlobs(),
                  blobsSidecarCache.orElseThrow().getLeft().getKZGAggregatedProof()));
      this.blobsSidecarCache = Optional.empty();
      return blockAndBlobsSidecar;
    };
  }

  private static class PayloadCache {
    private Optional<SafeFuture<?>> initializer;
    private Bytes8 payloadId;
    private Optional<ExecutionPayload> executionPayload;
    private Optional<ExecutionPayloadHeader> executionPayloadHeader;
  }
}
