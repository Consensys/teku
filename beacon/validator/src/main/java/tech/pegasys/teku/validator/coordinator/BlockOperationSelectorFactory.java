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
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
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
  private final ExecutionLayerChannel executionLayerChannel;

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

      if (bodyBuilder.supportsSyncAggregate()) {
        bodyBuilder.syncAggregate(
            contributionPool.createSyncAggregateForBlock(blockSlotState.getSlot(), parentRoot));
      }

      if (bodyBuilder.supportsBlsToExecutionChanges()) {
        bodyBuilder.blsToExecutionChanges(
            blsToExecutionChangePool.getItemsForBlock(blockSlotState));
      }

      if (bodyBuilder.supportsExecutionPayload()) {
        if (bodyBuilder.supportsKzgCommitments()) {
          // FIXME: so we fire it here. Should we lazy wrap it somehow?
          SafeFuture<ExecutionPayloadResult> executionPayloadResultFuture =
              forkChoiceNotifier
                  .getPayloadId(parentRoot, blockSlotState.getSlot())
                  .thenApplyChecked(Optional::orElseThrow)
                  .thenApply(
                      executionPayloadContext ->
                          executionLayerChannel.initiateBlockAndBlobsProduction(
                              executionPayloadContext,
                              blockSlotState.getSlot(),
                              bodyBuilder.isBlinded()));
          if (bodyBuilder.isBlinded()) {
            bodyBuilder.executionPayloadHeader(
                executionPayloadResultFuture.thenCompose(
                    executionPayloadResult ->
                        executionPayloadResult.getExecutionPayloaHeaderdFuture().orElseThrow()));
          } else {
            bodyBuilder.executionPayload(
                executionPayloadResultFuture.thenCompose(
                    executionPayloadResult ->
                        executionPayloadResult.getExecutionPayloadFuture().orElseThrow()));
          }

          final SchemaDefinitionsEip4844 schemaDefinitionsEip4844 =
              spec.getGenesisSchemaDefinitions().toVersionEip4844().orElseThrow();
          bodyBuilder.blobKzgCommitments(
              executionPayloadResultFuture.thenCompose(
                  executionPayloadResult ->
                      executionPayloadResult
                          .getKzgs()
                          .orElseThrow()
                          .thenApply(
                              kzgs ->
                                  schemaDefinitionsEip4844
                                      .getBeaconBlockBodySchema()
                                      .toVersionEip4844()
                                      .orElseThrow()
                                      .getBlobKzgCommitmentsSchema()
                                      .createFromElements(
                                          kzgs.stream()
                                              .map(SszKZGCommitment::new)
                                              .collect(Collectors.toList())))));
        } else {
          if (bodyBuilder.isBlinded()) {
            bodyBuilder.executionPayloadHeader(
                forkChoiceNotifier
                    .getPayloadId(parentRoot, blockSlotState.getSlot())
                    .thenCompose(
                        executionPayloadContext -> {
                          if (executionPayloadContext.isEmpty()) {
                            return SafeFuture.completedFuture(
                                SchemaDefinitionsBellatrix.required(
                                        spec.atSlot(blockSlotState.getSlot())
                                            .getSchemaDefinitions())
                                    .getExecutionPayloadHeaderSchema()
                                    .getHeaderOfDefaultPayload());
                          } else {
                            return executionLayerChannel
                                .initiateBlockProduction(
                                    executionPayloadContext.get(), blockSlotState.getSlot(), true)
                                .getExecutionPayloaHeaderdFuture()
                                .orElseThrow();
                          }
                        }));

          } else {
            bodyBuilder.executionPayload(
                forkChoiceNotifier
                    .getPayloadId(parentRoot, blockSlotState.getSlot())
                    .thenCompose(
                        executionPayloadContext -> {
                          if (executionPayloadContext.isEmpty()) {
                            return SafeFuture.completedFuture(
                                SchemaDefinitionsBellatrix.required(
                                        spec.atSlot(blockSlotState.getSlot())
                                            .getSchemaDefinitions())
                                    .getExecutionPayloadSchema()
                                    .getDefault());
                          } else {
                            return executionLayerChannel
                                .initiateBlockProduction(
                                    executionPayloadContext.get(), blockSlotState.getSlot(), true)
                                .getExecutionPayloadFuture()
                                .orElseThrow();
                          }
                        }));
          }
        }
      }
    };
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

  public Function<SignedBeaconBlock, SafeFuture<SignedBeaconBlockAndBlobsSidecar>>
      createSidecarSupplementSelector() {
    return signedBeaconBlock -> {
      final SchemaDefinitionsEip4844 schemaDefinitionsEip4844 =
          spec.getGenesisSchemaDefinitions().toVersionEip4844().orElseThrow();
      return executionLayerChannel
          .getPayloadResult(signedBeaconBlock.getSlot())
          .orElseThrow()
          .getBlobs()
          .orElseThrow()
          .thenApply(
              blobs ->
                  new SignedBeaconBlockAndBlobsSidecar(
                      schemaDefinitionsEip4844.getSignedBeaconBlockAndBlobsSidecarSchema(),
                      signedBeaconBlock,
                      createBlobsSidecar(
                          signedBeaconBlock.getSlot(), signedBeaconBlock.getRoot(), blobs)));
    };
  }

  private BlobsSidecar createBlobsSidecar(
      final UInt64 slot, final Bytes32 beaconBlockRoot, final List<Blob> blobs) {
    final SchemaDefinitionsEip4844 schemaDefinitionsEip4844 =
        spec.getGenesisSchemaDefinitions().toVersionEip4844().orElseThrow();
    final MiscHelpersEip4844 miscHelpers =
        (MiscHelpersEip4844) spec.forMilestone(SpecMilestone.EIP4844).miscHelpers();
    return new BlobsSidecar(
        schemaDefinitionsEip4844.getBlobsSidecarSchema(),
        beaconBlockRoot,
        slot,
        blobs,
        miscHelpers.computeAggregatedKzgProof(
            blobs.stream().map(Blob::getBytes).collect(Collectors.toList())));
  }
}
