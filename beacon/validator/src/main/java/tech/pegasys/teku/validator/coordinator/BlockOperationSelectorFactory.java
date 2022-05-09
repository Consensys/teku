/*
 * Copyright 2021 ConsenSys AG.
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationForkChecker;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;

public class BlockOperationSelectorFactory {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final AggregatingAttestationPool attestationPool;
  private final OperationPool<AttesterSlashing> attesterSlashingPool;
  private final OperationPool<ProposerSlashing> proposerSlashingPool;
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool;
  private final SyncCommitteeContributionPool contributionPool;
  private final DepositProvider depositProvider;
  private final Eth1DataCache eth1DataCache;
  private final Bytes32 graffiti;
  private final ForkChoiceNotifier forkChoiceNotifier;
  private final ExecutionLayerChannel executionLayerChannel;
  private final boolean isMevBoostEnabled;

  public BlockOperationSelectorFactory(
      final Spec spec,
      final AggregatingAttestationPool attestationPool,
      final OperationPool<AttesterSlashing> attesterSlashingPool,
      final OperationPool<ProposerSlashing> proposerSlashingPool,
      final OperationPool<SignedVoluntaryExit> voluntaryExitPool,
      final SyncCommitteeContributionPool contributionPool,
      final DepositProvider depositProvider,
      final Eth1DataCache eth1DataCache,
      final Bytes32 graffiti,
      final ForkChoiceNotifier forkChoiceNotifier,
      final ExecutionLayerChannel executionLayerChannel,
      final boolean isMevBoostEnabled) {
    this.spec = spec;
    this.attestationPool = attestationPool;
    this.attesterSlashingPool = attesterSlashingPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.voluntaryExitPool = voluntaryExitPool;
    this.contributionPool = contributionPool;
    this.depositProvider = depositProvider;
    this.eth1DataCache = eth1DataCache;
    this.graffiti = graffiti;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.executionLayerChannel = executionLayerChannel;
    this.isMevBoostEnabled = isMevBoostEnabled;
  }

  public Consumer<BeaconBlockBodyBuilder> createSelector(
      final Bytes32 parentRoot,
      final BeaconState blockSlotState,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti) {
    return bodyBuilder -> {
      final Eth1Data eth1Data = eth1DataCache.getEth1Vote(blockSlotState);

      SszList<Attestation> attestations =
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
          .voluntaryExits(voluntaryExits)
          .syncAggregate(
              () ->
                  contributionPool.createSyncAggregateForBlock(
                      blockSlotState.getSlot(), parentRoot));

      // execution Payload handling
      if (bodyBuilder.isBlinded()) {
        if (isMevBoostEnabled) {

          // mev-boost is enabled and a blinded block has been requested
          // we can call builder api to get a payload header
          bodyBuilder.executionPayloadHeader(
              payloadProvider(
                  parentRoot,
                  blockSlotState,
                  () ->
                      SchemaDefinitionsBellatrix.required(
                              spec.atSlot(blockSlotState.getSlot()).getSchemaDefinitions())
                          .getExecutionPayloadHeaderSchema()
                          .getHeaderOfDefaultPayload(),
                  (executionPayloadContext) ->
                      executionLayerChannel
                          .builderGetHeader(executionPayloadContext, blockSlotState.getSlot())
                          .join()));
          return;
        }

        // blinded block has been requested but mev-boost is not enabled
        throw new UnsupportedOperationException(
            "Blinded flow for non-mev_boost execution engine is not yet supported. See issue #5103");
      }

      // non-blinded block requested
      if (isMevBoostEnabled
          && spec.atSlot(blockSlotState.getSlot())
              .getMilestone()
              .isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX)) {
        LOG.warn("Mev-boost is enabled but a non-blinded block has been requested");
      }

      bodyBuilder.executionPayload(
          payloadProvider(
              parentRoot,
              blockSlotState,
              () ->
                  SchemaDefinitionsBellatrix.required(
                          spec.atSlot(blockSlotState.getSlot()).getSchemaDefinitions())
                      .getExecutionPayloadSchema()
                      .getDefault(),
              (executionPayloadContext) ->
                  executionLayerChannel
                      .engineGetPayload(executionPayloadContext, blockSlotState.getSlot())
                      .join()));
    };
  }

  private <T> Supplier<T> payloadProvider(
      final Bytes32 parentRoot,
      final BeaconState blockSlotState,
      Supplier<T> defaultSupplier,
      Function<ExecutionPayloadContext, T> supplier) {
    return () ->
        forkChoiceNotifier
            .getPayloadId(parentRoot, blockSlotState.getSlot())
            .thenApply(
                maybePayloadId -> {
                  if (maybePayloadId.isEmpty()) {
                    // Terminal block not reached, provide default payload
                    return defaultSupplier.get();
                  } else {
                    return supplier.apply(maybePayloadId.get());
                  }
                })
            .join();
  }

  public Consumer<SignedBeaconBlockUnblinder> createUnblinderSelector() {
    return bodyUnblinder -> {
      if (!isMevBoostEnabled) {
        // blinded block has been requested but mev-boost is not enabled
        throw new UnsupportedOperationException(
            "Blinded flow for non-mev_boost execution engine is not yet supported. See issue #5103");
      }

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
}
