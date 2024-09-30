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

package tech.pegasys.teku.spec.logic.versions.eip7732.execution;

import java.util.List;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.config.SpecConfigEip7732;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7732.ExecutionPayloadEip7732;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7732.ExecutionPayloadHeaderEip7732;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.BeaconStateEip7732;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.MutableBeaconStateEip7732;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.execution.AbstractExecutionPayloadProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.ExecutionPayloadProcessingException;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.eip7732.helpers.BeaconStateAccessorsEip7732;
import tech.pegasys.teku.spec.logic.versions.eip7732.helpers.MiscHelpersEip7732;
import tech.pegasys.teku.spec.logic.versions.electra.block.BlockProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;

public class ExecutionPayloadProcessorEip7732 extends AbstractExecutionPayloadProcessor {

  protected final SpecConfigEip7732 specConfig;
  protected final MiscHelpersEip7732 miscHelpers;
  protected final BeaconStateAccessorsEip7732 beaconStateAccessors;
  protected final BeaconStateMutatorsElectra beaconStateMutators;
  protected final BlockProcessorElectra blockProcessorElectra;

  public ExecutionPayloadProcessorEip7732(
      final SpecConfigEip7732 specConfig,
      final MiscHelpersEip7732 miscHelpers,
      final BeaconStateAccessorsEip7732 beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final BlockProcessorElectra blockProcessorElectra) {
    this.specConfig = specConfig;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
    this.beaconStateMutators = beaconStateMutators;
    this.blockProcessorElectra = blockProcessorElectra;
  }

  @Override
  public boolean verifyExecutionPayloadEnvelopeSignature(
      final BeaconState state, final SignedExecutionPayloadEnvelope signedEnvelope) {
    final Validator builder =
        state.getValidators().get(signedEnvelope.getMessage().getBuilderIndex().intValue());
    final Bytes signingRoot =
        miscHelpers.computeSigningRoot(
            signedEnvelope.getMessage(),
            beaconStateAccessors.getDomain(
                state.getForkInfo(),
                Domain.BEACON_BUILDER,
                miscHelpers.computeEpochAtSlot(state.getSlot())));
    return BLS.verify(builder.getPublicKey(), signingRoot, signedEnvelope.getSignature());
  }

  @Override
  public void processExecutionPayload(
      final MutableBeaconState state,
      final ExecutionPayloadEnvelope envelope,
      final OptimisticExecutionPayloadExecutor payloadExecutor)
      throws ExecutionPayloadProcessingException {
    final ExecutionPayloadEip7732 payload = ExecutionPayloadEip7732.required(envelope.getPayload());
    final Bytes32 previousStateRoot = state.hashTreeRoot();

    final BeaconBlockHeader latestBlockHeader = state.getLatestBlockHeader();
    if (latestBlockHeader.getStateRoot().isZero()) {
      state.setLatestBlockHeader(
          new BeaconBlockHeader(
              latestBlockHeader.getSlot(),
              latestBlockHeader.getProposerIndex(),
              latestBlockHeader.getParentRoot(),
              // Cache latest block header state root
              previousStateRoot,
              latestBlockHeader.getBodyRoot()));
    }

    // Verify consistency with the beacon block
    if (!envelope.getBeaconBlockRoot().equals(state.getLatestBlockHeader().hashTreeRoot())) {
      throw new ExecutionPayloadProcessingException(
          "Execution payload is not consistent with the beacon block");
    }

    // Verify consistency with the committed header
    final ExecutionPayloadHeaderEip7732 committedHeader =
        ExecutionPayloadHeaderEip7732.required(
            BeaconStateEip7732.required(state).getLatestExecutionPayloadHeader());

    if (!envelope.getBuilderIndex().equals(committedHeader.getBuilderIndex())
        || !committedHeader
            .getBlobKzgCommitmentsRoot()
            .equals(envelope.getBlobKzgCommitments().hashTreeRoot())) {
      throw new ExecutionPayloadProcessingException(
          "Execution payload is not consistent with the committed header");
    }

    if (envelope.isPayloadWithheld()) {
      return;
    }

    // Verify the withdrawals root
    if (!payload
        .getWithdrawals()
        .hashTreeRoot()
        .equals(BeaconStateEip7732.required(state).getLatestWithdrawalsRoot())) {
      throw new ExecutionPayloadProcessingException(
          "Execution payload withdrawals root is not consistent with the state latest withdrawals root");
    }

    // Verify the gas limit
    if (!committedHeader.getGasLimit().equals(payload.getGasLimit())
        || !committedHeader.getBlockHash().equals(payload.getBlockHash())) {
      throw new ExecutionPayloadProcessingException(
          "Execution payload gas limit is not consistent with the gas limit of the committed header");
    }

    // Verify consistency of the parent hash with respect to the previous execution payload
    if (!payload.getParentHash().equals(BeaconStateEip7732.required(state).getLatestBlockHash())) {
      throw new ExecutionPayloadProcessingException(
          "Execution payload parent hash is not consistent with the latest block hash from state");
    }

    // Verify prev_randao
    if (!payload
        .getPrevRandao()
        .equals(
            beaconStateAccessors.getRandaoMix(
                state, miscHelpers.computeEpochAtSlot(state.getSlot())))) {
      throw new ExecutionPayloadProcessingException(
          "Execution payload prev randao is not as expected");
    }

    // Verify timestamp
    if (!payload
        .getTimestamp()
        .equals(miscHelpers.computeTimeAtSlot(state.getGenesisTime(), state.getSlot()))) {
      throw new ExecutionPayloadProcessingException(
          "Execution payload timestamp is not as expected");
    }

    // Verify commitments are under limit
    if (envelope.getBlobKzgCommitments().size() > specConfig.getMaxBlobCommitmentsPerBlock()) {
      throw new ExecutionPayloadProcessingException(
          "Execution payload blob kzg commitments are over the limit");
    }

    // Verify the execution payload is valid
    final NewPayloadRequest payloadToExecute = computeNewPayloadRequest(state, envelope);
    final boolean optimisticallyAccept =
        payloadExecutor.optimisticallyExecute(committedHeader, payloadToExecute);
    if (!optimisticallyAccept) {
      throw new ExecutionPayloadProcessingException(
          "Execution payload was not optimistically accepted");
    }

    processOperationsNoValidation(state, envelope);

    // Cache the execution payload header and proposer
    MutableBeaconStateEip7732.required(state).setLatestBlockHash(payload.getBlockHash());
    MutableBeaconStateEip7732.required(state).setLatestFullSlot(state.getSlot());
  }

  @Override
  public NewPayloadRequest computeNewPayloadRequest(
      final BeaconState state, final ExecutionPayloadEnvelope envelope) {
    final SszList<SszKZGCommitment> blobKzgCommitments = envelope.getBlobKzgCommitments();
    final List<VersionedHash> versionedHashes =
        blobKzgCommitments.stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(miscHelpers::kzgCommitmentToVersionedHash)
            .toList();
    final Bytes32 parentBeaconBlockRoot = state.getLatestBlockHeader().getParentRoot();
    return new NewPayloadRequest(envelope.getPayload(), versionedHashes, parentBeaconBlockRoot);
  }

  @SuppressWarnings("unused")
  protected void processOperationsNoValidation(
      final MutableBeaconState state, final ExecutionPayloadEnvelope envelope)
      throws ExecutionPayloadProcessingException {
    safelyProcess(
        () -> {
          final Supplier<ValidatorExitContext> validatorExitContextSupplier =
              beaconStateMutators.createValidatorExitContextSupplier(state);

          final ExecutionRequests executionRequests = envelope.getExecutionRequests();

          blockProcessorElectra.processDepositRequests(state, executionRequests.getDeposits());
          blockProcessorElectra.processWithdrawalRequests(
              state, executionRequests.getWithdrawals(), validatorExitContextSupplier);
          blockProcessorElectra.processConsolidationRequests(
              state, executionRequests.getConsolidations());
        });
  }
}
