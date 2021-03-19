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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockInvariants;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyContent;
import tech.pegasys.teku.spec.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProcessorUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.collections.SszBitlist;
import tech.pegasys.teku.ssz.type.Bytes4;

public class Spec {
  // Eventually we will have multiple SpecVersions, where each version is active for a specific
  // range of epochs
  private final SpecVersion genesisSpec;
  private final ForkManifest forkManifest;

  private Spec(final SpecVersion genesisSpec, final ForkManifest forkManifest) {
    Preconditions.checkArgument(forkManifest != null);
    Preconditions.checkArgument(genesisSpec != null);
    this.genesisSpec = genesisSpec;
    this.forkManifest = forkManifest;
  }

  private Spec(final SpecVersion genesisSpec) {
    this(genesisSpec, ForkManifest.create(genesisSpec.getConfig()));
  }

  public static Spec create(final SpecConfiguration config) {
    final SpecVersion initialSpec = SpecVersion.createPhase0(config.config());
    return new Spec(initialSpec);
  }

  public static Spec create(final SpecConfiguration config, final ForkManifest forkManifest) {
    final SpecVersion initialSpec = SpecVersion.createPhase0(config.config());
    return new Spec(initialSpec, forkManifest);
  }

  public SpecVersion atEpoch(final UInt64 epoch) {
    return genesisSpec;
  }

  public SpecVersion atSlot(final UInt64 slot) {
    // Calculate using the latest spec
    final UInt64 epoch = getLatestSpec().getBeaconStateUtil().computeEpochAtSlot(slot);
    return atEpoch(epoch);
  }

  public SpecConfig getSpecConfig(final UInt64 epoch) {
    return atEpoch(epoch).getConfig();
  }

  public BeaconStateUtil getBeaconStateUtil(final UInt64 slot) {
    return atSlot(slot).getBeaconStateUtil();
  }

  public SpecVersion getGenesisSpec() {
    return atEpoch(UInt64.ZERO);
  }

  public SpecConfig getGenesisSpecConfig() {
    return getGenesisSpec().getConfig();
  }

  public BeaconStateUtil getGenesisBeaconStateUtil() {
    return getGenesisSpec().getBeaconStateUtil();
  }

  public SchemaDefinitions getGenesisSchemaDefinitions() {
    return getGenesisSpec().getSchemaDefinitions();
  }

  public ForkManifest getForkManifest() {
    return forkManifest;
  }

  public Fork fork(final UInt64 epoch) {
    return forkManifest.get(epoch);
  }

  // Config helpers
  public int slotsPerEpoch(final UInt64 epoch) {
    return atEpoch(epoch).getConfig().getSlotsPerEpoch();
  }

  public Bytes4 domainBeaconProposer(final UInt64 epoch) {
    return atEpoch(epoch).getConfig().getDomainBeaconProposer();
  }

  public long getSlotsPerHistoricalRoot(final UInt64 slot) {
    return atSlot(slot).getConfig().getSlotsPerHistoricalRoot();
  }

  public int getSlotsPerEpoch(final UInt64 slot) {
    return atSlot(slot).getConfig().getSlotsPerEpoch();
  }

  public int getSecondsPerSlot(final UInt64 slot) {
    return atSlot(slot).getConfig().getSecondsPerSlot();
  }

  public long getMaxDeposits(final BeaconState state) {
    return atState(state).getConfig().getMaxDeposits();
  }

  public long getEpochsPerEth1VotingPeriod(final UInt64 slot) {
    return atSlot(slot).getConfig().getEpochsPerEth1VotingPeriod();
  }

  public UInt64 getEth1FollowDistance(final UInt64 slot) {
    return atSlot(slot).getConfig().getEth1FollowDistance();
  }

  public UInt64 getSecondsPerEth1Block(final UInt64 slot) {
    return atSlot(slot).getConfig().getSecondsPerEth1Block();
  }

  // Serialization
  public BeaconState deserializeBeaconState(final Bytes serializedState) {
    final UInt64 slot = BeaconStateInvariants.extractSlot(serializedState);
    return atSlot(slot)
        .getSchemaDefinitions()
        .getBeaconStateSchema()
        .sszDeserialize(serializedState);
  }

  public SignedBeaconBlock deserializeSignedBeaconBlock(final Bytes serializedState) {
    final UInt64 slot = BeaconBlockInvariants.extractSignedBeaconBlockSlot(serializedState);
    return atSlot(slot)
        .getSchemaDefinitions()
        .getSignedBeaconBlockSchema()
        .sszDeserialize(serializedState);
  }

  public BeaconBlock deserializeBeaconBlock(final Bytes serializedState) {
    final UInt64 slot = BeaconBlockInvariants.extractBeaconBlockSlot(serializedState);
    return atSlot(slot)
        .getSchemaDefinitions()
        .getBeaconBlockSchema()
        .sszDeserialize(serializedState);
  }

  // BeaconState
  public UInt64 getCurrentEpoch(final BeaconState state) {
    return atState(state).getBeaconStateUtil().getCurrentEpoch(state);
  }

  public UInt64 getPreviousEpoch(final BeaconState state) {
    return atState(state).getBeaconStateUtil().getPreviousEpoch(state);
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
      final BeaconState blockSlotState,
      final Bytes32 parentBlockSigningRoot,
      final Consumer<BeaconBlockBodyContent> bodyBuilder)
      throws StateTransitionException {
    return atSlot(newSlot)
        .getBlockProposalUtil()
        .createNewUnsignedBlock(
            newSlot, proposerIndex, blockSlotState, parentBlockSigningRoot, bodyBuilder);
  }

  // Block Processing Utils
  public void processBlockHeader(MutableBeaconState state, BeaconBlockSummary blockHeader)
      throws BlockProcessingException {
    atState(state).getBlockProcessorUtil().processBlockHeader(state, blockHeader);
  }

  public void processProposerSlashings(
      MutableBeaconState state, SszList<ProposerSlashing> proposerSlashings)
      throws BlockProcessingException {
    atState(state).getBlockProcessorUtil().processProposerSlashings(state, proposerSlashings);
  }

  public void processAttesterSlashings(
      MutableBeaconState state, SszList<AttesterSlashing> attesterSlashings)
      throws BlockProcessingException {
    atState(state).getBlockProcessorUtil().processAttesterSlashings(state, attesterSlashings);
  }

  public void processAttestations(MutableBeaconState state, SszList<Attestation> attestations)
      throws BlockProcessingException {
    atState(state).getBlockProcessorUtil().processAttestations(state, attestations);
  }

  public void processAttestations(
      MutableBeaconState state,
      SszList<Attestation> attestations,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    atState(state)
        .getBlockProcessorUtil()
        .processAttestations(state, attestations, indexedAttestationCache);
  }

  public void processDeposits(MutableBeaconState state, SszList<? extends Deposit> deposits)
      throws BlockProcessingException {
    atState(state).getBlockProcessorUtil().processDeposits(state, deposits);
  }

  public void processVoluntaryExits(MutableBeaconState state, SszList<SignedVoluntaryExit> exits)
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

  // Private helpers
  private SpecVersion atState(final BeaconState state) {
    return atSlot(state.getSlot());
  }

  private SpecVersion atBlock(final BeaconBlockSummary blockSummary) {
    return atSlot(blockSummary.getSlot());
  }

  private SpecVersion getLatestSpec() {
    // When fork manifest is non-empty, we should pull the newest spec here
    return genesisSpec;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Spec spec = (Spec) o;
    return Objects.equals(forkManifest, spec.forkManifest);
  }

  @Override
  public int hashCode() {
    return Objects.hash(forkManifest);
  }
}
