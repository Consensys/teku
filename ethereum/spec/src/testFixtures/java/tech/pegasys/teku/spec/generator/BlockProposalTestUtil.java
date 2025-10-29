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

package tech.pegasys.teku.spec.generator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodySchemaGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.util.BeaconBlockBodyLists;
import tech.pegasys.teku.spec.datastructures.util.BlobsUtil;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockProposalTestUtil {
  private final Spec spec;
  private final DataStructureUtil dataStructureUtil;

  public BlockProposalTestUtil(final Spec spec) {
    this.spec = spec;
    this.dataStructureUtil = new DataStructureUtil(spec);
  }

  public SafeFuture<SignedBlockAndState> createNewBlock(
      final Signer signer,
      final UInt64 newSlot,
      final BeaconState state,
      final Bytes32 parentBlockSigningRoot,
      final Eth1Data eth1Data,
      final SszList<Attestation> attestations,
      final SszList<ProposerSlashing> proposerSlashings,
      final SszList<AttesterSlashing> attesterSlashings,
      final SszList<Deposit> deposits,
      final SszList<SignedVoluntaryExit> exits,
      final Optional<List<Bytes>> transactions,
      final Optional<Bytes32> terminalBlock,
      final Optional<ExecutionPayload> executionPayload,
      final Optional<SyncAggregate> syncAggregate,
      final Optional<SszList<SignedBlsToExecutionChange>> blsToExecutionChange,
      final Optional<SszList<SszKZGCommitment>> kzgCommitments)
      throws EpochProcessingException, SlotProcessingException {

    final UInt64 newEpoch = spec.computeEpochAtSlot(newSlot);

    final BeaconState blockSlotState = spec.processSlots(state, newSlot);
    final BLSSignature randaoReveal =
        signer.createRandaoReveal(newEpoch, blockSlotState.getForkInfo()).join();

    final int proposerIndex = spec.getBeaconProposerIndex(blockSlotState, newSlot);
    return spec.createNewUnsignedBlock(
            newSlot,
            proposerIndex,
            blockSlotState,
            parentBlockSigningRoot,
            builder -> {
              builder
                  .randaoReveal(randaoReveal)
                  .eth1Data(eth1Data)
                  .graffiti(Bytes32.ZERO)
                  .attestations(attestations)
                  .proposerSlashings(proposerSlashings)
                  .attesterSlashings(attesterSlashings)
                  .deposits(deposits)
                  .voluntaryExits(exits);
              if (builder.supportsSyncAggregate()) {
                builder.syncAggregate(
                    syncAggregate.orElse(
                        dataStructureUtil.emptySyncAggregateIfRequiredByState(blockSlotState)));
              }
              if (builder.supportsExecutionPayload()) {
                builder.executionPayload(
                    executionPayload.orElseGet(
                        () ->
                            createExecutionPayload(
                                newSlot, blockSlotState, transactions, terminalBlock)));
              }
              if (builder.supportsBlsToExecutionChanges()) {
                builder.blsToExecutionChanges(
                    blsToExecutionChange.orElseGet(
                        dataStructureUtil::emptySignedBlsToExecutionChangesList));
              }
              if (builder.supportsKzgCommitments()) {
                builder.blobKzgCommitments(
                    kzgCommitments.orElseGet(dataStructureUtil::emptyBlobKzgCommitments));
              }
              if (builder.supportsExecutionRequests()) {
                builder.executionRequests(dataStructureUtil.randomExecutionRequests());
              }
              // TODO-GLOAS: potentially better stubbing of bid and payload attestations
              // https://github.com/Consensys/teku/issues/9959
              if (builder.supportsSignedExecutionPayloadBid()) {
                builder.signedExecutionPayloadBid(
                    createSignedExecutionPayloadBid(
                        newSlot, blockSlotState, proposerIndex, kzgCommitments));
              }
              if (builder.supportsPayloadAttestations()) {
                builder.payloadAttestations(createEmptyPayloadAttestations(newSlot));
              }
              return SafeFuture.COMPLETE;
            },
            BlockProductionPerformance.NOOP)
        .thenApply(
            newBlockAndState -> {
              // Sign block and set block signature
              final BeaconBlock block = newBlockAndState.getBlock();
              BLSSignature blockSignature =
                  signer.signBlock(block, blockSlotState.getForkInfo()).join();

              final SignedBeaconBlock signedBlock =
                  SignedBeaconBlock.create(spec, block, blockSignature);
              return new SignedBlockAndState(signedBlock, newBlockAndState.getState());
            });
  }

  public SafeFuture<SignedBlockAndState> createNewBlockSkippingStateTransition(
      final Signer signer,
      final UInt64 newSlot,
      final BeaconState state,
      final Bytes32 parentBlockSigningRoot,
      final Eth1Data eth1Data,
      final SszList<Attestation> attestations,
      final SszList<ProposerSlashing> proposerSlashings,
      final SszList<AttesterSlashing> attesterSlashings,
      final SszList<Deposit> deposits,
      final SszList<SignedVoluntaryExit> exits,
      final Optional<List<Bytes>> transactions,
      final Optional<Bytes32> terminalBlock,
      final Optional<ExecutionPayload> executionPayload,
      final Optional<SszList<SignedBlsToExecutionChange>> blsToExecutionChange,
      final Optional<SszList<SszKZGCommitment>> kzgCommitments)
      throws EpochProcessingException, SlotProcessingException {

    final UInt64 newEpoch = spec.computeEpochAtSlot(newSlot);
    final BLSSignature randaoReveal =
        signer.createRandaoReveal(newEpoch, state.getForkInfo()).join();

    final BeaconState blockSlotState = spec.processSlots(state, newSlot);

    // Sign block and set block signature
    int proposerIndex = spec.getBeaconProposerIndex(blockSlotState, newSlot);
    return spec.atSlot(newSlot)
        .getSchemaDefinitions()
        .getBeaconBlockBodySchema()
        .createBlockBody(
            builder -> {
              builder
                  .randaoReveal(randaoReveal)
                  .eth1Data(eth1Data)
                  .graffiti(Bytes32.ZERO)
                  .attestations(attestations)
                  .proposerSlashings(proposerSlashings)
                  .attesterSlashings(attesterSlashings)
                  .deposits(deposits)
                  .voluntaryExits(exits);
              if (builder.supportsSyncAggregate()) {
                builder.syncAggregate(
                    dataStructureUtil.emptySyncAggregateIfRequiredByState(blockSlotState));
              }
              if (builder.supportsExecutionPayload()) {
                builder.executionPayload(
                    executionPayload.orElseGet(
                        () -> createExecutionPayload(newSlot, state, transactions, terminalBlock)));
              }
              if (builder.supportsBlsToExecutionChanges()) {
                builder.blsToExecutionChanges(
                    blsToExecutionChange.orElseGet(
                        dataStructureUtil::emptySignedBlsToExecutionChangesList));
              }
              if (builder.supportsKzgCommitments()) {
                builder.blobKzgCommitments(
                    kzgCommitments.orElseGet(dataStructureUtil::emptyBlobKzgCommitments));
              }
              if (builder.supportsExecutionRequests()) {
                builder.executionRequests(dataStructureUtil.randomExecutionRequests());
              }
              // TODO-GLOAS: potentially better stubbing of bid and payload attestations
              // https://github.com/Consensys/teku/issues/10071
              if (builder.supportsSignedExecutionPayloadBid()) {
                builder.signedExecutionPayloadBid(
                    createSignedExecutionPayloadBid(
                        newSlot, blockSlotState, proposerIndex, kzgCommitments));
              }
              if (builder.supportsPayloadAttestations()) {
                builder.payloadAttestations(createEmptyPayloadAttestations(newSlot));
              }
              return SafeFuture.COMPLETE;
            })
        .thenApply(
            blockBody -> {
              final BeaconBlock block =
                  spec.atSlot(newSlot)
                      .getSchemaDefinitions()
                      .getBeaconBlockSchema()
                      .create(
                          newSlot,
                          UInt64.valueOf(proposerIndex),
                          parentBlockSigningRoot,
                          blockSlotState.hashTreeRoot(),
                          blockBody);

              // Sign block and set block signature
              final BLSSignature blockSignature =
                  signer.signBlock(block, state.getForkInfo()).join();

              final SignedBeaconBlock signedBlock =
                  SignedBeaconBlock.create(spec, block, blockSignature);
              final BeaconState updatedState =
                  state.updated(w -> w.setLatestBlockHeader(BeaconBlockHeader.fromBlock(block)));
              return new SignedBlockAndState(signedBlock, updatedState);
            });
  }

  private ExecutionPayload createExecutionPayload(
      final UInt64 newSlot,
      final BeaconState state,
      final Optional<List<Bytes>> transactions,
      final Optional<Bytes32> terminalBlock) {
    final SpecVersion specVersion = spec.atSlot(newSlot);
    final ExecutionPayloadSchema<?> schema =
        SchemaDefinitionsBellatrix.required(specVersion.getSchemaDefinitions())
            .getExecutionPayloadSchema();
    if (terminalBlock.isEmpty() && !isMergeTransitionComplete(state)) {
      return schema.getDefault();
    }

    final Bytes32 currentExecutionPayloadBlockHash =
        BeaconStateBellatrix.required(state)
            .getLatestExecutionPayloadHeaderRequired()
            .getBlockHash();
    if (!currentExecutionPayloadBlockHash.isZero() && terminalBlock.isPresent()) {
      throw new IllegalArgumentException("Merge already happened, cannot set terminal block hash");
    }
    final Bytes32 parentHash = terminalBlock.orElse(currentExecutionPayloadBlockHash);
    final UInt64 currentEpoch = specVersion.beaconStateAccessors().getCurrentEpoch(state);

    return schema.createExecutionPayload(
        builder ->
            builder
                .parentHash(parentHash)
                .feeRecipient(Bytes20.ZERO)
                .stateRoot(dataStructureUtil.randomBytes32())
                .receiptsRoot(dataStructureUtil.randomBytes32())
                .logsBloom(dataStructureUtil.randomBytes256())
                .prevRandao(specVersion.beaconStateAccessors().getRandaoMix(state, currentEpoch))
                .blockNumber(newSlot)
                .gasLimit(UInt64.valueOf(30_000_000L))
                .gasUsed(UInt64.valueOf(30_000_000L))
                .timestamp(
                    specVersion.miscHelpers().computeTimeAtSlot(state.getGenesisTime(), newSlot))
                .extraData(dataStructureUtil.randomBytes32())
                .baseFeePerGas(UInt256.ONE)
                .blockHash(dataStructureUtil.randomBytes32())
                .transactions(transactions.orElse(Collections.emptyList()))
                .withdrawals(List::of)
                .blobGasUsed(() -> UInt64.ZERO)
                .excessBlobGas(() -> UInt64.ZERO));
  }

  private SignedExecutionPayloadBid createSignedExecutionPayloadBid(
      final UInt64 newSlot,
      final BeaconState state,
      final int proposerIndex,
      final Optional<SszList<SszKZGCommitment>> kzgCommitments) {
    final SpecVersion specVersion = spec.atSlot(newSlot);
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(specVersion.getSchemaDefinitions());
    // self-building bid
    final ExecutionPayloadBid bid =
        schemaDefinitions
            .getExecutionPayloadBidSchema()
            .create(
                BeaconStateGloas.required(state).getLatestBlockHash(),
                state.getLatestBlockHeader().getRoot(),
                dataStructureUtil.randomBytes32(),
                Bytes20.ZERO,
                UInt64.valueOf(30_000_000L),
                UInt64.valueOf(proposerIndex),
                newSlot,
                UInt64.ZERO,
                kzgCommitments
                    .orElse(schemaDefinitions.getBlobKzgCommitmentsSchema().of())
                    .hashTreeRoot());
    return schemaDefinitions
        .getSignedExecutionPayloadBidSchema()
        .create(bid, BLSSignature.infinity());
  }

  private SszList<PayloadAttestation> createEmptyPayloadAttestations(final UInt64 newSlot) {
    return BeaconBlockBodySchemaGloas.required(
            spec.atSlot(newSlot).getSchemaDefinitions().getBeaconBlockBodySchema())
        .getPayloadAttestationsSchema()
        .of();
  }

  private Boolean isMergeTransitionComplete(final BeaconState state) {
    return spec.atSlot(state.getSlot()).miscHelpers().isMergeTransitionComplete(state);
  }

  public SafeFuture<SignedBlockAndState> createBlock(
      final Signer signer,
      final UInt64 newSlot,
      final BeaconState previousState,
      final Bytes32 parentBlockSigningRoot,
      final Optional<SszList<Attestation>> attestations,
      final Optional<SszList<Deposit>> deposits,
      final Optional<SszList<AttesterSlashing>> attesterSlashings,
      final Optional<SszList<ProposerSlashing>> proposerSlashings,
      final Optional<SszList<SignedVoluntaryExit>> exits,
      final Optional<Eth1Data> eth1Data,
      final Optional<List<Bytes>> transactions,
      final Optional<Bytes32> terminalBlock,
      final Optional<ExecutionPayload> executionPayload,
      final Optional<SyncAggregate> syncAggregate,
      final Optional<SszList<SignedBlsToExecutionChange>> blsToExecutionChange,
      final Optional<SszList<SszKZGCommitment>> kzgCommitments,
      final boolean skipStateTransition)
      throws EpochProcessingException, SlotProcessingException {
    final UInt64 newEpoch = spec.computeEpochAtSlot(newSlot);
    final BeaconBlockBodyLists blockBodyLists = BeaconBlockBodyLists.ofSpecAtSlot(spec, newSlot);
    if (skipStateTransition) {
      return createNewBlockSkippingStateTransition(
          signer,
          newSlot,
          previousState,
          parentBlockSigningRoot,
          eth1Data.orElse(getEth1DataStub(previousState, newEpoch)),
          attestations.orElse(blockBodyLists.createAttestations()),
          proposerSlashings.orElse(blockBodyLists.createProposerSlashings()),
          attesterSlashings.orElse(blockBodyLists.createAttesterSlashings()),
          deposits.orElse(blockBodyLists.createDeposits()),
          exits.orElse(blockBodyLists.createVoluntaryExits()),
          transactions,
          terminalBlock,
          executionPayload,
          blsToExecutionChange,
          kzgCommitments);
    }
    return createNewBlock(
        signer,
        newSlot,
        previousState,
        parentBlockSigningRoot,
        eth1Data.orElse(getEth1DataStub(previousState, newEpoch)),
        attestations.orElse(blockBodyLists.createAttestations()),
        proposerSlashings.orElse(blockBodyLists.createProposerSlashings()),
        attesterSlashings.orElse(blockBodyLists.createAttesterSlashings()),
        deposits.orElse(blockBodyLists.createDeposits()),
        exits.orElse(blockBodyLists.createVoluntaryExits()),
        transactions,
        terminalBlock,
        executionPayload,
        syncAggregate,
        blsToExecutionChange,
        kzgCommitments);
  }

  public SafeFuture<SignedBlockAndState> createBlockWithBlobs(
      final Signer signer,
      final UInt64 newSlot,
      final BeaconState previousState,
      final Bytes32 parentBlockSigningRoot,
      final Optional<SszList<Attestation>> attestations,
      final Optional<SszList<Deposit>> deposits,
      final Optional<SszList<AttesterSlashing>> attesterSlashings,
      final Optional<SszList<ProposerSlashing>> proposerSlashings,
      final Optional<SszList<SignedVoluntaryExit>> exits,
      final Optional<Eth1Data> eth1Data,
      final Optional<List<Bytes>> transactions,
      final Optional<Bytes32> terminalBlock,
      final Optional<ExecutionPayload> executionPayload,
      final Optional<SyncAggregate> syncAggregate,
      final Optional<SszList<SignedBlsToExecutionChange>> blsToExecutionChange,
      final BlobsUtil blobsUtil,
      final List<Blob> blobs,
      final boolean skipStateTransition)
      throws EpochProcessingException, SlotProcessingException {
    final UInt64 newEpoch = spec.computeEpochAtSlot(newSlot);
    final BeaconBlockBodyLists blockBodyLists = BeaconBlockBodyLists.ofSpecAtSlot(spec, newSlot);
    final List<KZGCommitment> generatedBlobKzgCommitments = blobsUtil.blobsToKzgCommitments(blobs);

    final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema =
        SchemaDefinitionsDeneb.required(spec.atSlot(newSlot).getSchemaDefinitions())
            .getBlobKzgCommitmentsSchema();

    final SszList<SszKZGCommitment> kzgCommitments =
        generatedBlobKzgCommitments.stream()
            .map(SszKZGCommitment::new)
            .collect(blobKzgCommitmentsSchema.collector());

    if (skipStateTransition) {
      return createNewBlockSkippingStateTransition(
          signer,
          newSlot,
          previousState,
          parentBlockSigningRoot,
          eth1Data.orElse(getEth1DataStub(previousState, newEpoch)),
          attestations.orElse(blockBodyLists.createAttestations()),
          proposerSlashings.orElse(blockBodyLists.createProposerSlashings()),
          attesterSlashings.orElse(blockBodyLists.createAttesterSlashings()),
          deposits.orElse(blockBodyLists.createDeposits()),
          exits.orElse(blockBodyLists.createVoluntaryExits()),
          transactions,
          terminalBlock,
          executionPayload,
          blsToExecutionChange,
          Optional.of(kzgCommitments));
    } else {
      return createNewBlock(
          signer,
          newSlot,
          previousState,
          parentBlockSigningRoot,
          eth1Data.orElse(getEth1DataStub(previousState, newEpoch)),
          attestations.orElse(blockBodyLists.createAttestations()),
          proposerSlashings.orElse(blockBodyLists.createProposerSlashings()),
          attesterSlashings.orElse(blockBodyLists.createAttesterSlashings()),
          deposits.orElse(blockBodyLists.createDeposits()),
          exits.orElse(blockBodyLists.createVoluntaryExits()),
          transactions,
          terminalBlock,
          executionPayload,
          syncAggregate,
          blsToExecutionChange,
          Optional.of(kzgCommitments));
    }
  }

  private Eth1Data getEth1DataStub(final BeaconState state, final UInt64 currentEpoch) {
    final SpecConfig specConfig = spec.atSlot(state.getSlot()).getConfig();
    final int epochsPerPeriod = specConfig.getEpochsPerEth1VotingPeriod();
    UInt64 votingPeriod = currentEpoch.dividedBy(epochsPerPeriod);
    return new Eth1Data(
        Hash.sha256(SSZ.encodeUInt64(epochsPerPeriod)),
        state.getEth1DepositIndex(),
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
