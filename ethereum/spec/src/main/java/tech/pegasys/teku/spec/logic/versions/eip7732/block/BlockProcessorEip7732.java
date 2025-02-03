/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.logic.versions.eip7732.block;

import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;
import static tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair.PARTICIPATION_FLAG_WEIGHTS;

import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigEip7732;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.constants.PayloadStatus;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732.BeaconBlockBodyEip7732;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7732.ExecutionPayloadHeaderEip7732;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.operations.IndexedPayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.MutableBeaconStateEip7732;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.eip7732.helpers.BeaconStateAccessorsEip7732;
import tech.pegasys.teku.spec.logic.versions.eip7732.helpers.MiscHelpersEip7732;
import tech.pegasys.teku.spec.logic.versions.eip7732.helpers.PredicatesEip7732;
import tech.pegasys.teku.spec.logic.versions.eip7732.util.AttestationUtilEip7732;
import tech.pegasys.teku.spec.logic.versions.electra.block.BlockProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;

public class BlockProcessorEip7732 extends BlockProcessorElectra {

  private final MiscHelpersEip7732 miscHelpersEip7732;
  private final BeaconStateAccessorsEip7732 beaconStateAccessorsEip7732;
  private final PredicatesEip7732 predicatesEip7732;
  private final AttestationUtilEip7732 attestationUtilEip7732;

  public BlockProcessorEip7732(
      final SpecConfigEip7732 specConfig,
      final PredicatesEip7732 predicates,
      final MiscHelpersEip7732 miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsEip7732 beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtilEip7732 attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsEip7732 schemaDefinitions,
      final ExecutionRequestsDataCodec executionRequestsDataCodec) {
    super(
        specConfig,
        predicates,
        miscHelpers,
        syncCommitteeUtil,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        beaconStateUtil,
        attestationUtil,
        validatorsUtil,
        operationValidator,
        schemaDefinitions,
        executionRequestsDataCodec);
    this.miscHelpersEip7732 = miscHelpers;
    this.beaconStateAccessorsEip7732 = beaconStateAccessors;
    this.predicatesEip7732 = predicates;
    this.attestationUtilEip7732 = attestationUtil;
  }

  @Override
  public void executionProcessing(
      final MutableBeaconState genericState,
      final BeaconBlock block,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    super.executionProcessing(genericState, block, payloadExecutor);
    processExecutionPayloadHeader(genericState, block);
  }

  @Override
  protected void processOperationsNoValidation(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final IndexedAttestationCache indexedAttestationCache,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {
    super.processOperationsNoValidation(
        state, body, indexedAttestationCache, validatorExitContextSupplier);

    safelyProcess(
        () ->
            processPayloadAttestations(
                MutableBeaconStateEip7732.required(state),
                body.getOptionalPayloadAttestations()
                    .orElseThrow(
                        () ->
                            new BlockProcessingException(
                                "PayloadAttestations was not found during block processing."))));
  }

  @Override
  protected void processExecutionRequests(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    // Removed in EIP-7732
  }

  // process_execution_payload_header
  @Override
  public void processExecutionPayloadHeader(final MutableBeaconState state, final BeaconBlock block)
      throws BlockProcessingException {
    final SignedExecutionPayloadHeader signedHeader =
        BeaconBlockBodyEip7732.required(block.getBody()).getSignedExecutionPayloadHeader();

    if (!verifyExecutionPayloadHeaderSignature(state, signedHeader)) {
      throw new BlockProcessingException("Header signature is not valid");
    }

    final ExecutionPayloadHeaderEip7732 header =
        ExecutionPayloadHeaderEip7732.required(signedHeader.getMessage());

    final int builderIndex = header.getBuilderIndex().intValue();
    final UInt64 amount = header.getValue();

    if (!state.getBalances().get(builderIndex).get().isGreaterThanOrEqualTo(amount)) {
      throw new BlockProcessingException("Builder does not have the funds to cover the bid");
    }

    if (!header.getSlot().equals(state.getSlot())) {
      throw new BlockProcessingException("Bid is not for the current slot");
    }

    final MutableBeaconStateEip7732 stateEip7732 = MutableBeaconStateEip7732.required(state);

    if (!header.getParentBlockHash().equals(stateEip7732.getLatestBlockHash())) {
      throw new BlockProcessingException(
          String.format(
              "Bid parent block hash %s does not match the latest block hash %s in state",
              header.getParentBlockHash(), stateEip7732.getLatestBlockHash()));
    }

    if (!header.getParentBlockRoot().equals(block.getParentRoot())) {
      throw new BlockProcessingException(
          String.format(
              "Bid parent block root %s does not match the block parent root %s",
              header.getParentBlockRoot(), block.getParentRoot()));
    }

    beaconStateMutators.decreaseBalance(state, builderIndex, amount);
    beaconStateMutators.increaseBalance(state, block.getProposerIndex().intValue(), amount);

    stateEip7732.setLatestExecutionPayloadHeader(header);
  }

  public boolean verifyExecutionPayloadHeaderSignature(
      final MutableBeaconState state, final SignedExecutionPayloadHeader signedHeader) {
    final Validator builder =
        state
            .getValidators()
            .get(
                ExecutionPayloadHeaderEip7732.required(signedHeader.getMessage())
                    .getBuilderIndex()
                    .intValue());
    final Bytes signingRoot =
        miscHelpers.computeSigningRoot(
            signedHeader.getMessage(),
            beaconStateAccessors.getDomain(
                state.getForkInfo(),
                Domain.BEACON_BUILDER,
                miscHelpers.computeEpochAtSlot(state.getSlot())));
    return BLS.verify(builder.getPublicKey(), signingRoot, signedHeader.getSignature());
  }

  public void processPayloadAttestations(
      final MutableBeaconStateEip7732 state, final SszList<PayloadAttestation> payloadAttestations)
      throws BlockProcessingException {
    // process_payload_attestation
    for (PayloadAttestation payloadAttestation : payloadAttestations) {
      final PayloadAttestationData data = payloadAttestation.getData();
      if (!data.getBeaconBlockRoot().equals(state.getLatestBlockHeader().getParentRoot())) {
        throw new BlockProcessingException("Attestation is not for the parent beacon block");
      }
      if (!data.getSlot().increment().equals(state.getSlot())) {
        throw new BlockProcessingException("Attestation is not for the previous slot");
      }
      final IndexedPayloadAttestation indexedPayloadAttestation =
          beaconStateAccessorsEip7732.getIndexedPayloadAttestation(
              state, data.getSlot(), payloadAttestation);
      if (!attestationUtilEip7732.isValidIndexedPayloadAttestation(
          state, indexedPayloadAttestation)) {
        throw new BlockProcessingException("Invalid indexed payload attestation");
      }

      final SszMutableList<SszByte> epochParticipation;

      if (state.getSlot().mod(specConfig.getSlotsPerEpoch()).isZero()) {
        epochParticipation = state.getPreviousEpochParticipation();
      } else {
        epochParticipation = state.getCurrentEpochParticipation();
      }

      final boolean payloadWasPresent = data.getSlot().equals(state.getLatestFullSlot());
      final boolean votedPresent =
          data.getPayloadStatus().equals(PayloadStatus.PAYLOAD_PRESENT.getCode());

      final UInt64 proposerRewardDenominator =
          WEIGHT_DENOMINATOR
              .minusMinZero(PROPOSER_WEIGHT)
              .times(WEIGHT_DENOMINATOR.dividedBy(PROPOSER_WEIGHT));

      final int proposerIndex = beaconStateAccessors.getBeaconProposerIndex(state);

      // Return early if the attestation is for the wrong payload status
      if (votedPresent != payloadWasPresent) {
        // Unset the flags in case they were set by an equivocating ptc attestation
        UInt64 proposerPenaltyNumerator = UInt64.ZERO;

        for (SszUInt64 attestingIndex : indexedPayloadAttestation.getAttestingIndices()) {
          final int index = attestingIndex.get().intValue();
          for (int flagIndex = 0; flagIndex < PARTICIPATION_FLAG_WEIGHTS.size(); flagIndex++) {
            if (miscHelpersEip7732.hasFlag(epochParticipation.get(index).get(), flagIndex)) {
              epochParticipation.set(
                  index,
                  SszByte.of(
                      miscHelpersEip7732.removeFlag(
                          epochParticipation.get(index).get(), flagIndex)));
              proposerPenaltyNumerator =
                  proposerPenaltyNumerator.plus(
                      beaconStateAccessorsEip7732
                          .getBaseReward(state, index)
                          .times(PARTICIPATION_FLAG_WEIGHTS.get(flagIndex)));
            }
          }
        }
        // Penalize the proposer
        final UInt64 proposerPenalty =
            proposerPenaltyNumerator.times(2).dividedBy(proposerRewardDenominator);

        beaconStateMutators.decreaseBalance(state, proposerIndex, proposerPenalty);
        return;
      }

      // Reward the proposer and set all the participation flags in case of correct attestations
      UInt64 proposerRewardNumerator = UInt64.ZERO;

      for (SszUInt64 attestingIndex : indexedPayloadAttestation.getAttestingIndices()) {
        final int index = attestingIndex.get().intValue();
        for (int flagIndex = 0; flagIndex < PARTICIPATION_FLAG_WEIGHTS.size(); flagIndex++) {
          if (!miscHelpersEip7732.hasFlag(epochParticipation.get(index).get(), flagIndex)) {
            epochParticipation.set(
                index,
                SszByte.of(
                    miscHelpersEip7732.addFlag(epochParticipation.get(index).get(), flagIndex)));
            proposerRewardNumerator =
                proposerRewardNumerator.plus(
                    beaconStateAccessorsEip7732
                        .getBaseReward(state, index)
                        .times(PARTICIPATION_FLAG_WEIGHTS.get(flagIndex)));
          }
        }
      }

      // Reward proposer
      final UInt64 proposerReward = proposerRewardNumerator.dividedBy(proposerRewardDenominator);
      beaconStateMutators.increaseBalance(state, proposerIndex, proposerReward);
    }
  }

  @Override
  public void processExecutionPayload(
      final MutableBeaconState genericState,
      final BeaconBlockBody beaconBlockBody,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor) {
    // Removed in EIP-7732
  }

  @Override
  public void processWithdrawals(
      final MutableBeaconState genericState, final ExecutionPayloadSummary payloadSummary)
      throws BlockProcessingException {
    // return early if the parent block was empty
    if (!predicatesEip7732.isParentBlockFull(genericState)) {
      return;
    }
    super.processWithdrawals(genericState, payloadSummary);
  }

  @Override
  public ExecutionPayloadHeader extractExecutionPayloadHeader(final BeaconBlockBody beaconBlockBody)
      throws BlockProcessingException {
    // Removed in EIP-7732 (only required for withdrawals validation against the state)
    return null;
  }
}
