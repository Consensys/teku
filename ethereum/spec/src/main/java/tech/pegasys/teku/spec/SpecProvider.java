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

package tech.pegasys.teku.spec;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.spec.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.BeaconStateUtil;
import tech.pegasys.teku.spec.util.BlockProcessorUtil;
import tech.pegasys.teku.spec.util.operationvalidators.OperationInvalidReason;
import tech.pegasys.teku.spec.util.results.AttestationProcessingResult;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.backing.collections.SszBitlist;

public class SpecProvider {
  // Eventually we will have multiple versioned specs, where each version is active for a specific
  // range of epochs
  private final Spec genesisSpec;
  private final ForkManifest forkManifest;

  private SpecProvider(final Spec genesisSpec, final ForkManifest forkManifest) {
    Preconditions.checkArgument(forkManifest != null);
    Preconditions.checkArgument(genesisSpec != null);
    this.genesisSpec = genesisSpec;
    this.forkManifest = forkManifest;
  }

  private SpecProvider(final Spec genesisSpec) {
    this(genesisSpec, ForkManifest.create(genesisSpec.getConstants()));
  }

  public static SpecProvider create(final SpecConfiguration config) {
    final Spec initialSpec = new Spec(config.constants());
    return new SpecProvider(initialSpec);
  }

  public static SpecProvider create(
      final SpecConfiguration config, final ForkManifest forkManifest) {
    final Spec initialSpec = new Spec(config.constants());
    return new SpecProvider(initialSpec, forkManifest);
  }

  public Spec atEpoch(final UInt64 epoch) {
    return genesisSpec;
  }

  public Spec atSlot(final UInt64 slot) {
    // Calculate using the latest spec
    final UInt64 epoch = getLatestSpec().getBeaconStateUtil().computeEpochAtSlot(slot);
    return atEpoch(epoch);
  }

  public SpecConstants getSpecConstants(final UInt64 epoch) {
    return atEpoch(epoch).getConstants();
  }

  public BeaconStateUtil getBeaconStateUtil(final UInt64 slot) {
    return atSlot(slot).getBeaconStateUtil();
  }

  public Spec getGenesisSpec() {
    return atEpoch(UInt64.ZERO);
  }

  public SpecConstants getGenesisSpecConstants() {
    return getGenesisSpec().getConstants();
  }

  public BeaconStateUtil getGenesisBeaconStateUtil() {
    return getGenesisSpec().getBeaconStateUtil();
  }

  public ForkManifest getForkManifest() {
    return forkManifest;
  }

  public Fork fork(final UInt64 epoch) {
    return forkManifest.get(epoch);
  }

  // Constants helpers
  public int slotsPerEpoch(final UInt64 epoch) {
    return atEpoch(epoch).getConstants().getSlotsPerEpoch();
  }

  public Bytes4 domainBeaconProposer(final UInt64 epoch) {
    return atEpoch(epoch).getConstants().getDomainBeaconProposer();
  }

  public long getSlotsPerHistoricalRoot(final UInt64 slot) {
    return atSlot(slot).getConstants().getSlotsPerHistoricalRoot();
  }

  public int getSlotsPerEpoch(final UInt64 slot) {
    return atSlot(slot).getConstants().getSlotsPerEpoch();
  }

  public int getSecondsPerSlot(final UInt64 slot) {
    return atSlot(slot).getConstants().getSecondsPerSlot();
  }

  public long getMaxDeposits(final BeaconState state) {
    return atState(state).getConstants().getMaxDeposits();
  }

  public long getEpochsPerEth1VotingPeriod(final UInt64 slot) {
    return atSlot(slot).getConstants().getEpochsPerEth1VotingPeriod();
  }

  public UInt64 getEth1FollowDistance(final UInt64 slot) {
    return atSlot(slot).getConstants().getEth1FollowDistance();
  }

  public UInt64 getSecondsPerEth1Block(final UInt64 slot) {
    return atSlot(slot).getConstants().getSecondsPerEth1Block();
  }

  // BeaconState
  public UInt64 getCurrentEpoch(final BeaconState state) {
    return atState(state).getBeaconStateUtil().getCurrentEpoch(state);
  }

  public UInt64 computeStartSlotAtEpoch(final UInt64 epoch) {
    return atEpoch(epoch).getBeaconStateUtil().computeStartSlotAtEpoch(epoch);
  }

  public UInt64 computeEpochAtSlot(final UInt64 slot) {
    return atSlot(slot).getBeaconStateUtil().computeEpochAtSlot(slot);
  }

  public int getBeaconProposerIndex(final BeaconState state, final UInt64 slot) {
    return atState(state).getBeaconStateUtil().getBeaconProposerIndex(state, slot);
  }

  public UInt64 getCommitteeCountPerSlot(final BeaconState state, final UInt64 epoch) {
    return atState(state).getBeaconStateUtil().getCommitteeCountPerSlot(state, epoch);
  }

  public Bytes32 getBlockRoot(final BeaconState state, final UInt64 epoch) {
    return atState(state).getBeaconStateUtil().getBlockRoot(state, epoch);
  }

  public Bytes32 getBlockRootAtSlot(final BeaconState state, final UInt64 slot) {
    return atState(state).getBeaconStateUtil().getBlockRootAtSlot(state, slot);
  }

  public Bytes32 getPreviousDutyDependentRoot(BeaconState state) {
    return atState(state).getBeaconStateUtil().getPreviousDutyDependentRoot(state);
  }

  public Bytes32 getCurrentDutyDependentRoot(BeaconState state) {
    return atState(state).getBeaconStateUtil().getCurrentDutyDependentRoot(state);
  }

  public UInt64 computeNextEpochBoundary(final UInt64 slot) {
    return atSlot(slot).getBeaconStateUtil().computeNextEpochBoundary(slot);
  }

  // ForkChoice utils
  public UInt64 getCurrentSlot(UInt64 currentTime, UInt64 genesisTime) {
    return getLatestSpec().getForkChoiceUtil().getCurrentSlot(currentTime, genesisTime);
  }

  public UInt64 getCurrentSlot(ReadOnlyStore store) {
    return getLatestSpec().getForkChoiceUtil().getCurrentSlot(store);
  }

  public UInt64 getSlotStartTime(UInt64 slotNumber, UInt64 genesisTime) {
    return atSlot(slotNumber).getForkChoiceUtil().getSlotStartTime(slotNumber, genesisTime);
  }

  public Optional<Bytes32> getAncestor(
      ReadOnlyForkChoiceStrategy forkChoiceStrategy, Bytes32 root, UInt64 slot) {
    return getLatestSpec().getForkChoiceUtil().getAncestor(forkChoiceStrategy, root, slot);
  }

  public NavigableMap<UInt64, Bytes32> getAncestors(
      ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      Bytes32 root,
      UInt64 startSlot,
      UInt64 step,
      UInt64 count) {
    return getLatestSpec()
        .getForkChoiceUtil()
        .getAncestors(forkChoiceStrategy, root, startSlot, step, count);
  }

  public NavigableMap<UInt64, Bytes32> getAncestorsOnFork(
      ReadOnlyForkChoiceStrategy forkChoiceStrategy, Bytes32 root, UInt64 startSlot) {
    return getLatestSpec()
        .getForkChoiceUtil()
        .getAncestorsOnFork(forkChoiceStrategy, root, startSlot);
  }

  public void onTick(MutableStore store, UInt64 time) {
    getLatestSpec().getForkChoiceUtil().onTick(store, time);
  }

  public AttestationProcessingResult validateAttestation(
      final ReadOnlyStore store,
      final ValidateableAttestation validateableAttestation,
      final Optional<BeaconState> maybeTargetState) {
    return atSlot(validateableAttestation.getAttestation().getData().getSlot())
        .getForkChoiceUtil()
        .validate(store, validateableAttestation, maybeTargetState);
  }

  public BlockImportResult onBlock(
      final MutableStore store,
      final SignedBeaconBlock signedBlock,
      final BeaconState blockSlotState,
      final IndexedAttestationCache indexedAttestationCache) {
    return atBlock(signedBlock)
        .getForkChoiceUtil()
        .onBlock(store, signedBlock, blockSlotState, indexedAttestationCache);
  }

  public boolean blockDescendsFromLatestFinalizedBlock(
      final BeaconBlock block,
      final ReadOnlyStore store,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy) {
    return atBlock(block)
        .getForkChoiceUtil()
        .blockDescendsFromLatestFinalizedBlock(block, store, forkChoiceStrategy);
  }

  // State Transition Utils
  public BeaconState initiateStateTransition(BeaconState preState, SignedBeaconBlock signedBlock)
      throws StateTransitionException {
    return atBlock(signedBlock).getStateTransition().initiate(preState, signedBlock);
  }

  public BeaconState initiateStateTransition(
      BeaconState preState, SignedBeaconBlock signedBlock, boolean validateStateRootAndSignatures)
      throws StateTransitionException {
    return atBlock(signedBlock)
        .getStateTransition()
        .initiate(preState, signedBlock, validateStateRootAndSignatures);
  }

  public BeaconState initiateStateTransition(
      BeaconState preState,
      SignedBeaconBlock signedBlock,
      boolean validateStateRootAndSignatures,
      final IndexedAttestationCache indexedAttestationCache)
      throws StateTransitionException {
    return atBlock(signedBlock)
        .getStateTransition()
        .initiate(preState, signedBlock, validateStateRootAndSignatures, indexedAttestationCache);
  }

  public BeaconState processAndValidateBlock(
      final SignedBeaconBlock signedBlock,
      final IndexedAttestationCache indexedAttestationCache,
      final BeaconState blockSlotState,
      final boolean validateStateRootAndSignatures)
      throws StateTransitionException {
    return atBlock(signedBlock)
        .getStateTransition()
        .processAndValidateBlock(
            signedBlock, blockSlotState, validateStateRootAndSignatures, indexedAttestationCache);
  }

  public BeaconState processBlock(BeaconState preState, BeaconBlock block)
      throws BlockProcessingException {
    return atBlock(block).getStateTransition().processBlock(preState, block);
  }

  public BeaconState processSlots(BeaconState preState, UInt64 slot)
      throws SlotProcessingException, EpochProcessingException {
    return atSlot(slot).getStateTransition().processSlots(preState, slot);
  }

  // Block Proposal
  public BeaconBlockAndState createNewUnsignedBlock(
      final UInt64 newSlot,
      final int proposerIndex,
      final BLSSignature randaoReveal,
      final BeaconState blockSlotState,
      final Bytes32 parentBlockSigningRoot,
      final Eth1Data eth1Data,
      final Bytes32 graffiti,
      final SSZList<Attestation> attestations,
      final SSZList<ProposerSlashing> proposerSlashings,
      final SSZList<AttesterSlashing> attesterSlashings,
      final SSZList<Deposit> deposits,
      final SSZList<SignedVoluntaryExit> voluntaryExits)
      throws StateTransitionException {
    return atSlot(newSlot)
        .getBlockProposalUtil()
        .createNewUnsignedBlock(
            newSlot,
            proposerIndex,
            randaoReveal,
            blockSlotState,
            parentBlockSigningRoot,
            eth1Data,
            graffiti,
            attestations,
            proposerSlashings,
            attesterSlashings,
            deposits,
            voluntaryExits);
  }

  // Block Processing Utils
  public void processBlockHeader(MutableBeaconState state, BeaconBlockSummary blockHeader)
      throws BlockProcessingException {
    atState(state).getBlockProcessorUtil().processBlockHeader(state, blockHeader);
  }

  public void processProposerSlashings(
      MutableBeaconState state, SSZList<ProposerSlashing> proposerSlashings)
      throws BlockProcessingException {
    atState(state).getBlockProcessorUtil().processProposerSlashings(state, proposerSlashings);
  }

  public void processAttesterSlashings(
      MutableBeaconState state, SSZList<AttesterSlashing> attesterSlashings)
      throws BlockProcessingException {
    atState(state).getBlockProcessorUtil().processAttesterSlashings(state, attesterSlashings);
  }

  public void processAttestations(MutableBeaconState state, SSZList<Attestation> attestations)
      throws BlockProcessingException {
    atState(state).getBlockProcessorUtil().processAttestations(state, attestations);
  }

  public void processAttestations(
      MutableBeaconState state,
      SSZList<Attestation> attestations,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    atState(state)
        .getBlockProcessorUtil()
        .processAttestations(state, attestations, indexedAttestationCache);
  }

  public void processDeposits(MutableBeaconState state, SSZList<? extends Deposit> deposits)
      throws BlockProcessingException {
    atState(state).getBlockProcessorUtil().processDeposits(state, deposits);
  }

  public void processVoluntaryExits(MutableBeaconState state, SSZList<SignedVoluntaryExit> exits)
      throws BlockProcessingException {
    atState(state).getBlockProcessorUtil().processVoluntaryExits(state, exits);
  }

  public boolean isEnoughVotesToUpdateEth1Data(
      BeaconState state, Eth1Data eth1Data, final int additionalVotes) {
    final BlockProcessorUtil blockProcessor = atState(state).getBlockProcessorUtil();
    final long existingVotes = blockProcessor.getVoteCount(state, eth1Data);
    return blockProcessor.isEnoughVotesToUpdateEth1Data(existingVotes + additionalVotes);
  }

  // Validator Utils
  public UInt64 getMaxLookaheadEpoch(final BeaconState state) {
    return atState(state).getValidatorsUtil().getMaxLookaheadEpoch(state);
  }

  public List<Integer> getActiveValidatorIndices(final BeaconState state, final UInt64 epoch) {
    return atEpoch(epoch).getValidatorsUtil().getActiveValidatorIndices(state, epoch);
  }

  public int countActiveValidators(final BeaconState state, final UInt64 epoch) {
    return getActiveValidatorIndices(state, epoch).size();
  }

  public Optional<BLSPublicKey> getValidatorPubKey(
      final BeaconState state, final UInt64 proposerIndex) {
    return atState(state).getValidatorsUtil().getValidatorPubKey(state, proposerIndex);
  }

  public Optional<Integer> getValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    return atState(state).getValidatorsUtil().getValidatorIndex(state, publicKey);
  }

  // Attestation helpers
  public List<Integer> getAttestingIndices(
      BeaconState state, AttestationData data, SszBitlist bits) {
    return atState(state).getAttestationUtil().getAttestingIndices(state, data, bits);
  }

  public AttestationData getGenericAttestationData(
      final UInt64 slot,
      final BeaconState state,
      final BeaconBlock block,
      final UInt64 committeeIndex) {
    return atSlot(slot)
        .getAttestationUtil()
        .getGenericAttestationData(slot, state, block, committeeIndex);
  }

  // Operation Validation
  public Optional<OperationInvalidReason> validateAttestation(
      final BeaconState state, final AttestationData data) {
    return atState(state).getBlockProcessorUtil().validateAttestation(state, data);
  }

  public Optional<OperationInvalidReason> validateVoluntaryExit(
      final BeaconState state, final SignedVoluntaryExit signedExit) {
    return atState(state).getBlockProcessorUtil().validateVoluntaryExit(state, signedExit);
  }

  public Optional<OperationInvalidReason> validateAttesterSlashing(
      final BeaconState state, final AttesterSlashing attesterSlashing) {
    return atState(state).getBlockProcessorUtil().validateAttesterSlashing(state, attesterSlashing);
  }

  public boolean verifyVoluntaryExitSignature(
      BeaconState state, SignedVoluntaryExit exit, BLSSignatureVerifier signatureVerifier) {
    return atState(state)
        .getBlockProcessorUtil()
        .verifyVoluntaryExit(state, exit, signatureVerifier);
  }

  public boolean verifyProposerSlashingSignature(
      BeaconState state,
      ProposerSlashing proposerSlashing,
      BLSSignatureVerifier signatureVerifier) {
    return atState(state)
        .getBlockProcessorUtil()
        .verifyProposerSlashingSignature(state, proposerSlashing, signatureVerifier);
  }

  // Private helpers
  private Spec atState(final BeaconState state) {
    return atSlot(state.getSlot());
  }

  private Spec atBlock(final BeaconBlockSummary blockSummary) {
    return atSlot(blockSummary.getSlot());
  }

  private Spec getLatestSpec() {
    // When fork manifest is non-empty, we should pull the newest spec here
    return genesisSpec;
  }
}
