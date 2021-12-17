/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.core;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge;
import tech.pegasys.teku.spec.datastructures.util.BeaconBlockBodyLists;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsMerge;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockProposalTestUtil {
  private final Spec spec;
  private final DataStructureUtil dataStructureUtil;
  private final BeaconBlockBodyLists blockBodyLists;

  public BlockProposalTestUtil(final Spec spec) {
    this.spec = spec;
    this.dataStructureUtil = new DataStructureUtil(spec);
    blockBodyLists = BeaconBlockBodyLists.ofSpec(spec);
  }

  public SignedBlockAndState createNewBlock(
      final Signer signer,
      final UInt64 newSlot,
      final BeaconState state,
      final Bytes32 parentBlockSigningRoot,
      final Eth1Data eth1Data,
      final SszList<Attestation> attestations,
      final SszList<ProposerSlashing> slashings,
      final SszList<Deposit> deposits,
      final SszList<SignedVoluntaryExit> exits,
      final Optional<List<Bytes>> transactions,
      final Optional<Bytes32> terminalBlock,
      final Optional<ExecutionPayload> executionPayload)
      throws StateTransitionException, EpochProcessingException, SlotProcessingException {

    final UInt64 newEpoch = spec.computeEpochAtSlot(newSlot);
    final BLSSignature randaoReveal =
        signer.createRandaoReveal(newEpoch, state.getForkInfo()).join();

    final BeaconState blockSlotState = spec.processSlots(state, newSlot);
    final BeaconBlockAndState newBlockAndState =
        spec.createNewUnsignedBlock(
            newSlot,
            spec.getBeaconProposerIndex(blockSlotState, newSlot),
            blockSlotState,
            parentBlockSigningRoot,
            builder ->
                builder
                    .randaoReveal(randaoReveal)
                    .eth1Data(eth1Data)
                    .graffiti(Bytes32.ZERO)
                    .attestations(attestations)
                    .proposerSlashings(slashings)
                    .attesterSlashings(blockBodyLists.createAttesterSlashings())
                    .deposits(deposits)
                    .voluntaryExits(exits)
                    .syncAggregate(
                        () -> dataStructureUtil.emptySyncAggregateIfRequiredByState(blockSlotState))
                    .executionPayload(
                        () ->
                            executionPayload.orElseGet(
                                () ->
                                    createExecutionPayload(
                                        newSlot, state, transactions, terminalBlock))));

    // Sign block and set block signature
    final BeaconBlock block = newBlockAndState.getBlock();
    BLSSignature blockSignature = signer.signBlock(block, state.getForkInfo()).join();

    final SignedBeaconBlock signedBlock = SignedBeaconBlock.create(spec, block, blockSignature);
    return new SignedBlockAndState(signedBlock, newBlockAndState.getState());
  }

  public SignedBlockAndState createNewBlockSkippingStateTransition(
      final Signer signer,
      final UInt64 newSlot,
      final BeaconState state,
      final Bytes32 parentBlockSigningRoot,
      final Eth1Data eth1Data,
      final SszList<Attestation> attestations,
      final SszList<ProposerSlashing> slashings,
      final SszList<Deposit> deposits,
      final SszList<SignedVoluntaryExit> exits,
      final Optional<List<Bytes>> transactions,
      final Optional<Bytes32> terminalBlock,
      final Optional<ExecutionPayload> executionPayload)
      throws EpochProcessingException, SlotProcessingException {

    final UInt64 newEpoch = spec.computeEpochAtSlot(newSlot);
    final BLSSignature randaoReveal =
        signer.createRandaoReveal(newEpoch, state.getForkInfo()).join();

    final BeaconState blockSlotState = spec.processSlots(state, newSlot);

    // Sign block and set block signature

    final BeaconBlockBody blockBody =
        spec.atSlot(newSlot)
            .getSchemaDefinitions()
            .getBeaconBlockBodySchema()
            .createBlockBody(
                builder ->
                    builder
                        .randaoReveal(randaoReveal)
                        .eth1Data(eth1Data)
                        .graffiti(Bytes32.ZERO)
                        .attestations(attestations)
                        .proposerSlashings(slashings)
                        .attesterSlashings(blockBodyLists.createAttesterSlashings())
                        .deposits(deposits)
                        .voluntaryExits(exits)
                        .syncAggregate(
                            () ->
                                dataStructureUtil.emptySyncAggregateIfRequiredByState(
                                    blockSlotState))
                        .executionPayload(
                            () ->
                                executionPayload.orElseGet(
                                    () ->
                                        createExecutionPayload(
                                            newSlot, state, transactions, terminalBlock))));

    final BeaconBlock block =
        spec.atSlot(newSlot)
            .getSchemaDefinitions()
            .getBeaconBlockSchema()
            .create(
                newSlot,
                UInt64.valueOf(spec.getBeaconProposerIndex(blockSlotState, newSlot)),
                parentBlockSigningRoot,
                blockSlotState.hashTreeRoot(),
                blockBody);

    // Sign block and set block signature
    BLSSignature blockSignature = signer.signBlock(block, state.getForkInfo()).join();

    final SignedBeaconBlock signedBlock = SignedBeaconBlock.create(spec, block, blockSignature);
    return new SignedBlockAndState(signedBlock, blockSlotState);
  }

  private ExecutionPayload createExecutionPayload(
      final UInt64 newSlot,
      final BeaconState state,
      final Optional<List<Bytes>> transactions,
      final Optional<Bytes32> terminalBlock) {
    final SpecVersion specVersion = spec.atSlot(newSlot);
    final ExecutionPayloadSchema schema =
        SchemaDefinitionsMerge.required(specVersion.getSchemaDefinitions())
            .getExecutionPayloadSchema();
    if (terminalBlock.isEmpty() && !isMergeTransitionComplete(state)) {
      return schema.getDefault();
    }

    Bytes32 currentExecutionPayloadBlockHash =
        BeaconStateMerge.required(state).getLatestExecutionPayloadHeader().getBlockHash();
    if (!currentExecutionPayloadBlockHash.isZero() && terminalBlock.isPresent()) {
      throw new IllegalArgumentException("Merge already happened, cannot set terminal block hash");
    }
    Bytes32 parentHash = terminalBlock.orElse(currentExecutionPayloadBlockHash);
    UInt64 currentEpoch = specVersion.beaconStateAccessors().getCurrentEpoch(state);

    return schema.create(
        parentHash,
        Bytes20.ZERO,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes256(),
        specVersion.beaconStateAccessors().getRandaoMix(state, currentEpoch),
        newSlot,
        UInt64.valueOf(30_000_000L),
        UInt64.valueOf(30_000_000L),
        specVersion.miscHelpers().computeTimeAtSlot(state, newSlot),
        dataStructureUtil.randomBytes32(),
        UInt256.ONE,
        dataStructureUtil.randomBytes32(),
        transactions.orElse(Collections.emptyList()));
  }

  private Boolean isMergeTransitionComplete(final BeaconState state) {
    return spec.atSlot(state.getSlot()).miscHelpers().isMergeTransitionComplete(state);
  }

  public SignedBlockAndState createBlock(
      final Signer signer,
      final UInt64 newSlot,
      final BeaconState previousState,
      final Bytes32 parentBlockSigningRoot,
      final Optional<SszList<Attestation>> attestations,
      final Optional<SszList<Deposit>> deposits,
      final Optional<SszList<SignedVoluntaryExit>> exits,
      final Optional<Eth1Data> eth1Data,
      final Optional<List<Bytes>> transactions,
      final Optional<Bytes32> terminalBlock,
      final Optional<ExecutionPayload> executionPayload,
      final boolean skipStateTransition)
      throws StateTransitionException, EpochProcessingException, SlotProcessingException {
    final UInt64 newEpoch = spec.computeEpochAtSlot(newSlot);
    if (skipStateTransition) {
      return createNewBlockSkippingStateTransition(
          signer,
          newSlot,
          previousState,
          parentBlockSigningRoot,
          eth1Data.orElse(get_eth1_data_stub(previousState, newEpoch)),
          attestations.orElse(blockBodyLists.createAttestations()),
          blockBodyLists.createProposerSlashings(),
          deposits.orElse(blockBodyLists.createDeposits()),
          exits.orElse(blockBodyLists.createVoluntaryExits()),
          transactions,
          terminalBlock,
          executionPayload);
    }
    return createNewBlock(
        signer,
        newSlot,
        previousState,
        parentBlockSigningRoot,
        eth1Data.orElse(get_eth1_data_stub(previousState, newEpoch)),
        attestations.orElse(blockBodyLists.createAttestations()),
        blockBodyLists.createProposerSlashings(),
        deposits.orElse(blockBodyLists.createDeposits()),
        exits.orElse(blockBodyLists.createVoluntaryExits()),
        transactions,
        terminalBlock,
        executionPayload);
  }

  private Eth1Data get_eth1_data_stub(BeaconState state, UInt64 current_epoch) {
    final SpecConfig specConfig = spec.atSlot(state.getSlot()).getConfig();
    final int epochsPerPeriod = specConfig.getEpochsPerEth1VotingPeriod();
    UInt64 votingPeriod = current_epoch.dividedBy(epochsPerPeriod);
    return new Eth1Data(
        Hash.sha256(SSZ.encodeUInt64(epochsPerPeriod)),
        state.getEth1_deposit_index(),
        Hash.sha256(Hash.sha256(SSZ.encodeUInt64(votingPeriod.longValue()))));
  }

  public int getProposerIndexForSlot(final BeaconState preState, final UInt64 slot) {
    BeaconState state;
    try {
      state = spec.processSlots(preState, slot);
    } catch (SlotProcessingException | EpochProcessingException e) {
      throw new RuntimeException(e);
    }
    return spec.getBeaconProposerIndex(state, state.getSlot());
  }
}
