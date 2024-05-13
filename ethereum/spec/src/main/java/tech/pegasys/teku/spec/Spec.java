/*
 * Copyright Consensys Software Inc., 2022
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

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.millisToSeconds;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.CheckReturnValue;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.Merkleizable;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.config.NetworkingSpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockInvariants;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.datastructures.util.ForkAndSpecMilestone;
import tech.pegasys.teku.spec.genesis.GenesisGenerator;
import tech.pegasys.teku.spec.logic.StateTransition;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.LightClientUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class Spec {
  private final Map<SpecMilestone, SpecVersion> specVersions;
  private final ForkSchedule forkSchedule;
  private final StateTransition stateTransition;

  private Spec(
      final Map<SpecMilestone, SpecVersion> specVersions, final ForkSchedule forkSchedule) {
    Preconditions.checkArgument(specVersions != null && specVersions.size() > 0);
    Preconditions.checkArgument(forkSchedule != null);
    this.specVersions = specVersions;
    this.forkSchedule = forkSchedule;

    // Setup state transition
    this.stateTransition = new StateTransition(this::atSlot);
  }

  static Spec create(final SpecConfig config, final SpecMilestone highestMilestoneSupported) {
    final Map<SpecMilestone, SpecVersion> specVersions = new HashMap<>();
    final ForkSchedule.Builder forkScheduleBuilder = ForkSchedule.builder();

    for (SpecMilestone milestone : SpecMilestone.getMilestonesUpTo(highestMilestoneSupported)) {
      SpecVersion.create(milestone, config)
          .ifPresent(
              milestoneSpec -> {
                forkScheduleBuilder.addNextMilestone(milestoneSpec);
                specVersions.put(milestone, milestoneSpec);
              });
    }

    final ForkSchedule forkSchedule = forkScheduleBuilder.build();

    return new Spec(specVersions, forkSchedule);
  }

  public SpecVersion forMilestone(final SpecMilestone milestone) {
    return specVersions.get(milestone);
  }

  public SpecVersion atEpoch(final UInt64 epoch) {
    return specVersions.get(forkSchedule.getSpecMilestoneAtEpoch(epoch));
  }

  public SpecVersion atSlot(final UInt64 slot) {
    return specVersions.get(forkSchedule.getSpecMilestoneAtSlot(slot));
  }

  public SpecVersion atTime(final UInt64 genesisTime, final UInt64 currentTime) {
    return specVersions.get(forkSchedule.getSpecMilestoneAtTime(genesisTime, currentTime));
  }

  private SpecVersion atTimeMillis(final UInt64 genesisTimeMillis, final UInt64 currentTimeMillis) {
    return atTime(millisToSeconds(genesisTimeMillis), millisToSeconds(currentTimeMillis));
  }

  public SpecConfig getSpecConfig(final UInt64 epoch) {
    return atEpoch(epoch).getConfig();
  }

  public BeaconStateUtil getBeaconStateUtil(final UInt64 slot) {
    return atSlot(slot).getBeaconStateUtil();
  }

  public Optional<SyncCommitteeUtil> getSyncCommitteeUtil(final UInt64 slot) {
    return atSlot(slot).getSyncCommitteeUtil();
  }

  public SyncCommitteeUtil getSyncCommitteeUtilRequired(final UInt64 slot) {
    return getSyncCommitteeUtil(slot)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Fork at slot " + slot + " does not support sync committees"));
  }

  public Optional<LightClientUtil> getLightClientUtil(final UInt64 slot) {
    return atSlot(slot).getLightClientUtil();
  }

  public LightClientUtil getLightClientUtilRequired(final UInt64 slot) {
    return getLightClientUtil(slot)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Fork at slot " + slot + " does not support light clients"));
  }

  public SpecVersion getGenesisSpec() {
    return atEpoch(UInt64.ZERO);
  }

  public SpecConfig getGenesisSpecConfig() {
    return getGenesisSpec().getConfig();
  }

  /**
   * Base networking constants
   *
   * <p>These constants are unified among forks and are not overridden, new constant name is used if
   * it's changed in the new fork
   */
  public NetworkingSpecConfig getNetworkingConfig() {
    // Networking config is constant along forks
    return getGenesisSpec().getConfig().getNetworkingConfig();
  }

  /**
   * Networking config with Deneb constants. Use {@link SpecConfigDeneb#required(SpecConfig)} when
   * you are sure that Deneb is available, otherwise use this method
   */
  public Optional<NetworkingSpecConfigDeneb> getNetworkingConfigDeneb() {
    return Optional.ofNullable(forMilestone(DENEB))
        .map(SpecVersion::getConfig)
        .map(specConfig -> (NetworkingSpecConfigDeneb) specConfig.getNetworkingConfig());
  }

  public SchemaDefinitions getGenesisSchemaDefinitions() {
    return getGenesisSpec().getSchemaDefinitions();
  }

  public ForkSchedule getForkSchedule() {
    return forkSchedule;
  }

  /**
   * @return Milestones that are actively transitioned to. Does not include milestones that are
   *     immediately eclipsed by later milestones that activate at the same epoch.
   */
  public List<ForkAndSpecMilestone> getEnabledMilestones() {
    return forkSchedule.getActiveMilestones();
  }

  /**
   * Returns true if the given milestone is at or prior to our highest supported milestone
   *
   * @param milestone The milestone to be checked
   * @return True if the milestone is supported
   */
  public boolean isMilestoneSupported(final SpecMilestone milestone) {
    return forkSchedule.getSupportedMilestones().contains(milestone);
  }

  public Fork fork(final UInt64 epoch) {
    return forkSchedule.getFork(epoch);
  }

  // Config helpers
  public int slotsPerEpoch(final UInt64 epoch) {
    return atEpoch(epoch).getConfig().getSlotsPerEpoch();
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

  public UInt64 getMillisPerSlot(final UInt64 slot) {
    return secondsToMillis(getSecondsPerSlot(slot));
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

  public int getSecondsPerEth1Block(final UInt64 slot) {
    return atSlot(slot).getConfig().getSecondsPerEth1Block();
  }

  public int getSyncCommitteeSize(final UInt64 slot) {
    return atSlot(slot)
        .getConfig()
        .toVersionAltair()
        .map(SpecConfigAltair::getSyncCommitteeSize)
        .orElse(0);
  }

  // Genesis
  public BeaconState initializeBeaconStateFromEth1(
      final Bytes32 eth1BlockHash,
      final UInt64 eth1Timestamp,
      final List<Deposit> deposits,
      final Optional<ExecutionPayloadHeader> payloadHeader) {
    final GenesisGenerator genesisGenerator = createGenesisGenerator();
    genesisGenerator.updateCandidateState(eth1BlockHash, eth1Timestamp, deposits);
    payloadHeader.ifPresent(genesisGenerator::updateExecutionPayloadHeader);
    return genesisGenerator.getGenesisState();
  }

  public GenesisGenerator createGenesisGenerator() {
    return new GenesisGenerator(getGenesisSpec(), forkSchedule.getGenesisFork());
  }

  // Serialization
  public BeaconState deserializeBeaconState(final Bytes serializedState) {
    final UInt64 slot = BeaconStateInvariants.extractSlot(serializedState);
    return atSlot(slot)
        .getSchemaDefinitions()
        .getBeaconStateSchema()
        .sszDeserialize(serializedState);
  }

  public SignedBeaconBlock deserializeSignedBeaconBlock(final Bytes serializedSignedBlock) {
    final UInt64 slot =
        BeaconBlockInvariants.extractSignedBlockContainerSlot(serializedSignedBlock);
    return atSlot(slot)
        .getSchemaDefinitions()
        .getSignedBeaconBlockSchema()
        .sszDeserialize(serializedSignedBlock);
  }

  public SignedBlockContainer deserializeSignedBlockContainer(
      final Bytes serializedSignedBlockContainer, final Optional<String> milestone) {

    final SchemaDefinitions schemaDefinition =
        getSchemaDefinitionsForMilestone(milestone)
            .orElseGet(getSchemaDefinitionForSignedBlockSSZ(serializedSignedBlockContainer));

    return schemaDefinition
        .getSignedBlockContainerSchema()
        .sszDeserialize(serializedSignedBlockContainer);
  }

  public SignedBlockContainer deserializeSignedBlindedBlockContainer(
      final Bytes serializedSignedBlindedBlockContainer, final Optional<String> milestone) {

    final SchemaDefinitions schemaDefinition =
        getSchemaDefinitionsForMilestone(milestone)
            .orElseGet(getSchemaDefinitionForSignedBlockSSZ(serializedSignedBlindedBlockContainer));

    return schemaDefinition
        .getSignedBlindedBlockContainerSchema()
        .sszDeserialize(serializedSignedBlindedBlockContainer);
  }

  private Optional<SchemaDefinitions> getSchemaDefinitionsForMilestone(
      final Optional<String> milestone) {
    return milestone
        .map(SpecMilestone::forName)
        .map(specVersions::get)
        .map(SpecVersion::getSchemaDefinitions);
  }

  private Supplier<SchemaDefinitions> getSchemaDefinitionForSignedBlockSSZ(
      final Bytes serializedSignedBlindedBlockContainer) {
    return () -> {
      final UInt64 slot =
          BeaconBlockInvariants.extractSignedBlockContainerSlot(
              serializedSignedBlindedBlockContainer);
      return atSlot(slot).getSchemaDefinitions();
    };
  }

  public BeaconBlock deserializeBeaconBlock(final Bytes serializedBlock) {
    final UInt64 slot = BeaconBlockInvariants.extractBeaconBlockSlot(serializedBlock);
    return atSlot(slot)
        .getSchemaDefinitions()
        .getBeaconBlockSchema()
        .sszDeserialize(serializedBlock);
  }

  public SszList<Blob> deserializeBlobsInBlock(final Bytes serializedBlobs, final UInt64 slot) {
    return atSlot(slot)
        .getSchemaDefinitions()
        .toVersionDeneb()
        .orElseThrow(() -> new RuntimeException("Deneb milestone is required to deserialize blobs"))
        .getBlobsInBlockSchema()
        .sszDeserialize(serializedBlobs);
  }

  public BlobSidecar deserializeBlobSidecar(final Bytes serializedBlobSidecar, final UInt64 slot) {
    return atSlot(slot)
        .getSchemaDefinitions()
        .toVersionDeneb()
        .orElseThrow(
            () -> new RuntimeException("Deneb milestone is required to deserialize blob sidecar"))
        .getBlobSidecarSchema()
        .sszDeserialize(serializedBlobSidecar);
  }

  public ExecutionPayloadHeader deserializeJsonExecutionPayloadHeader(
      final ObjectMapper objectMapper, final File jsonFile, final UInt64 slot) throws IOException {
    return atSlot(slot)
        .getSchemaDefinitions()
        .toVersionBellatrix()
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Bellatrix milestone is required to deserialize execution payload header"))
        .getExecutionPayloadHeaderSchema()
        .jsonDeserialize(objectMapper.createParser(jsonFile));
  }

  // BeaconState
  public UInt64 getCurrentEpoch(final BeaconState state) {
    return atState(state).beaconStateAccessors().getCurrentEpoch(state);
  }

  public UInt64 getPreviousEpoch(final BeaconState state) {
    return atState(state).beaconStateAccessors().getPreviousEpoch(state);
  }

  public Bytes32 getSeed(final BeaconState state, final UInt64 epoch, final Bytes4 domainType)
      throws IllegalArgumentException {
    return atState(state).beaconStateAccessors().getSeed(state, epoch, domainType);
  }

  public UInt64 computeStartSlotAtEpoch(final UInt64 epoch) {
    return atEpoch(epoch).miscHelpers().computeStartSlotAtEpoch(epoch);
  }

  public UInt64 computeEpochAtSlot(final UInt64 slot) {
    return atSlot(slot).miscHelpers().computeEpochAtSlot(slot);
  }

  public UInt64 computeTimeAtSlot(final BeaconState state, final UInt64 slot) {
    return atSlot(slot).miscHelpers().computeTimeAtSlot(state.getGenesisTime(), slot);
  }

  public Bytes computeSigningRoot(final BeaconBlock block, final Bytes32 domain) {
    return atBlock(block).miscHelpers().computeSigningRoot(block, domain);
  }

  public Bytes computeSigningRoot(final BeaconBlockHeader blockHeader, final Bytes32 domain) {
    return atSlot(blockHeader.getSlot()).miscHelpers().computeSigningRoot(blockHeader, domain);
  }

  public Bytes computeSigningRoot(final AggregateAndProof proof, final Bytes32 domain) {
    return atSlot(proof.getAggregate().getData().getSlot())
        .miscHelpers()
        .computeSigningRoot(proof, domain);
  }

  public Bytes computeSigningRoot(final UInt64 slot, final Bytes32 domain) {
    return atSlot(slot).miscHelpers().computeSigningRoot(slot, domain);
  }

  public Bytes computeBuilderApplicationSigningRoot(final UInt64 slot, final Merkleizable object) {
    final MiscHelpers miscHelpers = atSlot(slot).miscHelpers();
    return miscHelpers.computeSigningRoot(
        object, miscHelpers.computeDomain(Domain.APPLICATION_BUILDER));
  }

  public Bytes4 computeForkDigest(
      final Bytes4 currentVersion, final Bytes32 genesisValidatorsRoot) {
    return atForkVersion(currentVersion)
        .miscHelpers()
        .computeForkDigest(currentVersion, genesisValidatorsRoot);
  }

  public int getBeaconProposerIndex(final BeaconState state, final UInt64 slot) {
    return atState(state).beaconStateAccessors().getBeaconProposerIndex(state, slot);
  }

  public UInt64 getCommitteeCountPerSlot(final BeaconState state, final UInt64 epoch) {
    return atState(state).beaconStateAccessors().getCommitteeCountPerSlot(state, epoch);
  }

  public Bytes32 getBlockRoot(final BeaconState state, final UInt64 epoch) {
    return atState(state).beaconStateAccessors().getBlockRoot(state, epoch);
  }

  public Bytes32 getBlockRootAtSlot(final BeaconState state, final UInt64 slot) {
    return atState(state).beaconStateAccessors().getBlockRootAtSlot(state, slot);
  }

  public Bytes32 getDomain(
      final Bytes4 domainType,
      final UInt64 epoch,
      final Fork fork,
      final Bytes32 genesisValidatorsRoot) {
    return atEpoch(epoch)
        .beaconStateAccessors()
        .getDomain(domainType, epoch, fork, genesisValidatorsRoot);
  }

  public Bytes32 getVoluntaryExitDomain(
      final UInt64 epoch, final Fork fork, final Bytes32 genesisValidatorsRoot) {
    return atEpoch(epoch)
        .beaconStateAccessors()
        .getVoluntaryExitDomain(epoch, fork, genesisValidatorsRoot);
  }

  public Bytes32 getRandaoMix(final BeaconState state, final UInt64 epoch) {
    return atState(state).beaconStateAccessors().getRandaoMix(state, epoch);
  }

  public boolean verifyProposerSlashingSignature(
      final BeaconState state,
      final ProposerSlashing proposerSlashing,
      final BLSSignatureVerifier signatureVerifier) {
    final UInt64 epoch = getProposerSlashingEpoch(proposerSlashing);
    return atEpoch(epoch)
        .operationSignatureVerifier()
        .verifyProposerSlashingSignature(
            state.getFork(), state, proposerSlashing, signatureVerifier);
  }

  public boolean verifyVoluntaryExitSignature(
      final BeaconState state,
      final SignedVoluntaryExit signedExit,
      final BLSSignatureVerifier signatureVerifier) {
    final UInt64 epoch = signedExit.getMessage().getEpoch();
    return atEpoch(epoch)
        .operationSignatureVerifier()
        .verifyVoluntaryExitSignature(state, signedExit, signatureVerifier);
  }

  public Bytes32 getPreviousDutyDependentRoot(final BeaconState state) {
    return atState(state).getBeaconStateUtil().getPreviousDutyDependentRoot(state);
  }

  public Bytes32 getCurrentDutyDependentRoot(final BeaconState state) {
    return atState(state).getBeaconStateUtil().getCurrentDutyDependentRoot(state);
  }

  public UInt64 computeNextEpochBoundary(final UInt64 slot) {
    return atSlot(slot).getBeaconStateUtil().computeNextEpochBoundary(slot);
  }

  public int computeSubnetForAttestation(final BeaconState state, final Attestation attestation) {
    return atState(state).getBeaconStateUtil().computeSubnetForAttestation(state, attestation);
  }

  public int computeSubnetForCommittee(
      final UInt64 attestationSlot, final UInt64 committeeIndex, final UInt64 committeesPerSlot) {
    return atSlot(attestationSlot)
        .getBeaconStateUtil()
        .computeSubnetForCommittee(attestationSlot, committeeIndex, committeesPerSlot);
  }

  public UInt64 getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(final UInt64 epoch) {
    return atEpoch(epoch)
        .miscHelpers()
        .getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(epoch);
  }

  // ForkChoice utils
  public UInt64 getCurrentSlot(final UInt64 currentTime, final UInt64 genesisTime) {
    return atTime(genesisTime, currentTime)
        .getForkChoiceUtil()
        .getCurrentSlot(currentTime, genesisTime);
  }

  public UInt64 getCurrentSlotForMillis(
      final UInt64 currentTimeMillis, final UInt64 genesisTimeMillis) {
    return atTimeMillis(genesisTimeMillis, currentTimeMillis)
        .getForkChoiceUtil()
        .getCurrentSlotForMillis(currentTimeMillis, genesisTimeMillis);
  }

  public UInt64 getCurrentSlot(final ReadOnlyStore store) {
    return atTime(store.getGenesisTime(), store.getTimeSeconds())
        .getForkChoiceUtil()
        .getCurrentSlot(store);
  }

  public UInt64 getCurrentEpoch(final ReadOnlyStore store) {
    return computeEpochAtSlot(getCurrentSlot(store));
  }

  public UInt64 getSlotStartTime(final UInt64 slotNumber, final UInt64 genesisTime) {
    return atSlot(slotNumber).getForkChoiceUtil().getSlotStartTime(slotNumber, genesisTime);
  }

  public UInt64 getSlotStartTimeMillis(final UInt64 slotNumber, final UInt64 genesisTimeMillis) {
    return atSlot(slotNumber)
        .getForkChoiceUtil()
        .getSlotStartTimeMillis(slotNumber, genesisTimeMillis);
  }

  public Optional<Bytes32> getAncestor(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy, final Bytes32 root, final UInt64 slot) {
    return forGetAncestor(forkChoiceStrategy, root, slot)
        .getForkChoiceUtil()
        .getAncestor(forkChoiceStrategy, root, slot);
  }

  public NavigableMap<UInt64, Bytes32> getAncestors(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 root,
      final UInt64 startSlot,
      final UInt64 step,
      final UInt64 count) {
    return forGetAncestor(forkChoiceStrategy, root, startSlot)
        .getForkChoiceUtil()
        .getAncestors(forkChoiceStrategy, root, startSlot, step, count);
  }

  public NavigableMap<UInt64, Bytes32> getAncestorsOnFork(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 root,
      final UInt64 startSlot) {
    return forGetAncestor(forkChoiceStrategy, root, startSlot)
        .getForkChoiceUtil()
        .getAncestorsOnFork(forkChoiceStrategy, root, startSlot);
  }

  private SpecVersion forGetAncestor(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 root,
      final UInt64 startSlot) {
    return atSlot(forkChoiceStrategy.blockSlot(root).orElse(startSlot));
  }

  public void onTick(final MutableStore store, final UInt64 timeMillis) {
    atTimeMillis(store.getGenesisTimeMillis(), timeMillis)
        .getForkChoiceUtil()
        .onTick(store, timeMillis);
  }

  public AttestationProcessingResult validateAttestation(
      final ReadOnlyStore store,
      final ValidatableAttestation validatableAttestation,
      final Optional<BeaconState> maybeState) {
    final UInt64 slot = validatableAttestation.getAttestation().getData().getSlot();
    final Fork fork = forkSchedule.getFork(computeEpochAtSlot(slot));
    return atSlot(slot)
        .getForkChoiceUtil()
        .validate(fork, store, validatableAttestation, maybeState);
  }

  public Optional<OperationInvalidReason> validateAttesterSlashing(
      final BeaconState state, final AttesterSlashing attesterSlashing) {
    // Attestations must both be from the same epoch or will wind up being rejected by any version
    final UInt64 epoch = computeEpochAtSlot(attesterSlashing.getAttestation1().getData().getSlot());
    return atEpoch(epoch)
        .getOperationValidator()
        .validateAttesterSlashing(state.getFork(), state, attesterSlashing);
  }

  public Optional<OperationInvalidReason> validateProposerSlashing(
      final BeaconState state, final ProposerSlashing proposerSlashing) {
    final UInt64 epoch = getProposerSlashingEpoch(proposerSlashing);
    return atEpoch(epoch)
        .getOperationValidator()
        .validateProposerSlashing(state.getFork(), state, proposerSlashing);
  }

  public Optional<OperationInvalidReason> validateVoluntaryExit(
      final BeaconState state, final SignedVoluntaryExit signedExit) {
    final UInt64 epoch = signedExit.getMessage().getEpoch();
    return atEpoch(epoch)
        .getOperationValidator()
        .validateVoluntaryExit(state.getFork(), state, signedExit);
  }

  public Optional<OperationInvalidReason> validateBlsToExecutionChange(
      final BeaconState state,
      final UInt64 currentTime,
      final BlsToExecutionChange blsToExecutionChange) {
    return atTime(state.getGenesisTime(), currentTime)
        .getOperationValidator()
        .validateBlsToExecutionChange(state.getFork(), state, blsToExecutionChange);
  }

  public boolean verifyBlsToExecutionChangeSignature(
      final BeaconState state,
      final SignedBlsToExecutionChange signedBlsToExecutionChange,
      final BLSSignatureVerifier signatureVerifier) {
    return atState(state)
        .operationSignatureVerifier()
        .verifyBlsToExecutionChangeSignature(state, signedBlsToExecutionChange, signatureVerifier);
  }

  public boolean isBlockProcessorOptimistic(final UInt64 slot) {
    return atSlot(slot).getBlockProcessor().isOptimistic();
  }

  public boolean blockDescendsFromLatestFinalizedBlock(
      final UInt64 blockSlot,
      final Bytes32 blockParentRoot,
      final ReadOnlyStore store,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy) {
    return atSlot(blockSlot)
        .getForkChoiceUtil()
        .blockDescendsFromLatestFinalizedBlock(
            blockSlot, blockParentRoot, store, forkChoiceStrategy);
  }

  public BeaconState processSlots(final BeaconState preState, final UInt64 slot)
      throws SlotProcessingException, EpochProcessingException {
    return stateTransition.processSlots(preState, slot);
  }

  // Block Proposal
  public SafeFuture<BeaconBlockAndState> createNewUnsignedBlock(
      final UInt64 proposalSlot,
      final int proposerIndex,
      final BeaconState blockSlotState,
      final Bytes32 parentBlockSigningRoot,
      final Function<BeaconBlockBodyBuilder, SafeFuture<Void>> bodyBuilder,
      final BlockProductionPerformance blockProductionPerformance) {
    return atSlot(proposalSlot)
        .getBlockProposalUtil()
        .createNewUnsignedBlock(
            proposalSlot,
            proposerIndex,
            blockSlotState,
            parentBlockSigningRoot,
            bodyBuilder,
            blockProductionPerformance);
  }

  // Blind Block Utils

  public SafeFuture<SignedBeaconBlock> unblindSignedBeaconBlock(
      final SignedBeaconBlock signedBlindedBeaconBlock,
      final Consumer<SignedBeaconBlockUnblinder> beaconBlockUnblinderConsumer) {
    return atSlot(signedBlindedBeaconBlock.getSlot())
        .getBlindBlockUtil()
        .map(
            converter ->
                converter.unblindSignedBeaconBlock(
                    signedBlindedBeaconBlock, beaconBlockUnblinderConsumer))
        .orElseGet(
            () -> {
              // this shouldn't happen: BlockFactory should skip unblinding when is not needed
              checkState(
                  !signedBlindedBeaconBlock.isBlinded(),
                  "Unblinder not available for the current spec but the given block was blinded");
              return SafeFuture.completedFuture(signedBlindedBeaconBlock);
            });
  }

  public SignedBeaconBlock blindSignedBeaconBlock(
      final SignedBeaconBlock unblindedSignedBeaconBlock) {
    return atSlot(unblindedSignedBeaconBlock.getSlot())
        .getBlindBlockUtil()
        .map(converter -> converter.blindSignedBeaconBlock(unblindedSignedBeaconBlock))
        .orElseGet(
            () -> {
              // this shouldn't happen: BlockFactory should skip blinding when is not needed
              checkState(
                  unblindedSignedBeaconBlock.getMessage().getBody().isBlinded(),
                  "Blinder not available for the current spec but the given block was unblinded");
              return unblindedSignedBeaconBlock;
            });
  }

  public Optional<List<Withdrawal>> getExpectedWithdrawals(final BeaconState state) {
    if (!atState(state).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      return Optional.empty();
    }
    return Optional.of(
        atState(state).getBlockProcessor().getExpectedWithdrawals(state).getWithdrawalList());
  }

  // Block Processor Utils

  public BlockProcessor getBlockProcessor(final UInt64 slot) {
    return atSlot(slot).getBlockProcessor();
  }

  public BeaconState processBlock(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws StateTransitionException {
    try {
      final BeaconState blockSlotState = stateTransition.processSlots(preState, block.getSlot());
      return getBlockProcessor(block.getSlot())
          .processAndValidateBlock(
              block,
              blockSlotState,
              IndexedAttestationCache.NOOP,
              signatureVerifier,
              payloadExecutor);
    } catch (SlotProcessingException | EpochProcessingException e) {
      throw new StateTransitionException(e);
    }
  }

  public BeaconState replayValidatedBlock(final BeaconState preState, final SignedBeaconBlock block)
      throws StateTransitionException {
    try {
      final BeaconState blockSlotState = stateTransition.processSlots(preState, block.getSlot());
      return getBlockProcessor(block.getSlot())
          .processUnsignedBlock(
              blockSlotState,
              block.getMessage(),
              IndexedAttestationCache.NOOP,
              BLSSignatureVerifier.NO_OP,
              Optional.empty());
    } catch (SlotProcessingException | EpochProcessingException | BlockProcessingException e) {
      throw new StateTransitionException(e);
    }
  }

  public BlockCheckpoints calculateBlockCheckpoints(final BeaconState state) {
    return atState(state).getEpochProcessor().calculateBlockCheckpoints(state);
  }

  @CheckReturnValue
  public Optional<OperationInvalidReason> validateAttestation(
      final BeaconState state, final AttestationData data) {
    return atState(state).getBlockProcessor().validateAttestation(state, data);
  }

  public UInt64 getSyncCommitteeParticipantReward(final BeaconState state) {
    final BeaconStateAltair beaconStateAltair = BeaconStateAltair.required(state);
    return atState(state).getBlockProcessor().computeParticipantReward(beaconStateAltair);
  }

  public boolean isEnoughVotesToUpdateEth1Data(
      final BeaconState state, final Eth1Data eth1Data, final int additionalVotes) {
    final BlockProcessor blockProcessor = atState(state).getBlockProcessor();
    final long existingVotes = blockProcessor.getVoteCount(state, eth1Data);
    return blockProcessor.isEnoughVotesToUpdateEth1Data(existingVotes + additionalVotes);
  }

  public UInt64 getMaxLookaheadEpoch(final BeaconState state) {
    return atState(state).beaconStateAccessors().getMaxLookaheadEpoch(state);
  }

  public IntList getActiveValidatorIndices(final BeaconState state, final UInt64 epoch) {
    return atEpoch(epoch).beaconStateAccessors().getActiveValidatorIndices(state, epoch);
  }

  public UInt64 getTotalActiveBalance(final BeaconState state) {
    return atState(state).beaconStateAccessors().getTotalActiveBalance(state);
  }

  public UInt64 getProposerBoostAmount(final BeaconState state) {
    return atState(state).beaconStateAccessors().getProposerBoostAmount(state);
  }

  public int getPreviousEpochAttestationCapacity(final BeaconState state) {
    return atState(state).beaconStateAccessors().getPreviousEpochAttestationCapacity(state);
  }

  public IntList getBeaconCommittee(
      final BeaconState state, final UInt64 slot, final UInt64 index) {
    return atState(state).beaconStateAccessors().getBeaconCommittee(state, slot, index);
  }

  public Int2IntMap getBeaconCommitteesSize(final BeaconState state, final UInt64 slot) {
    return atState(state).beaconStateAccessors().getBeaconCommitteesSize(state, slot);
  }

  public Optional<BLSPublicKey> getValidatorPubKey(
      final BeaconState state, final UInt64 validatorIndex) {
    return atState(state).beaconStateAccessors().getValidatorPubKey(state, validatorIndex);
  }

  // Validator Utils
  public int countActiveValidators(final BeaconState state, final UInt64 epoch) {
    return getActiveValidatorIndices(state, epoch).size();
  }

  public Optional<Integer> getValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    return atState(state).getValidatorsUtil().getValidatorIndex(state, publicKey);
  }

  public Optional<CommitteeAssignment> getCommitteeAssignment(
      final BeaconState state, final UInt64 epoch, final int validatorIndex) {
    return atEpoch(epoch).getValidatorsUtil().getCommitteeAssignment(state, epoch, validatorIndex);
  }

  public Map<Integer, CommitteeAssignment> getValidatorIndexToCommitteeAssignmentMap(
      final BeaconState state, final UInt64 epoch) {
    return atEpoch(epoch)
        .getValidatorsUtil()
        .getValidatorIndexToCommitteeAssignmentMap(state, epoch);
  }

  // Attestation helpers
  public IntList getAttestingIndices(final BeaconState state, final Attestation attestation) {
    return atState(state).getAttestationUtil().getAttestingIndices(state, attestation);
  }

  public AttestationData getGenericAttestationData(
      final UInt64 slot,
      final BeaconState state,
      final BeaconBlockSummary block,
      final UInt64 committeeIndex) {
    return atSlot(slot)
        .getAttestationUtil()
        .getGenericAttestationData(slot, state, block, committeeIndex);
  }

  public SafeFuture<AttestationProcessingResult> isValidIndexedAttestation(
      final BeaconState state,
      final ValidatableAttestation attestation,
      final AsyncBLSSignatureVerifier blsSignatureVerifier) {
    final UInt64 slot = attestation.getData().getSlot();
    return atSlot(slot)
        .getAttestationUtil()
        .isValidIndexedAttestationAsync(
            getForkAtSlot(slot), state, attestation, blsSignatureVerifier);
  }

  public boolean isMergeTransitionComplete(final BeaconState state) {
    return atState(state).miscHelpers().isMergeTransitionComplete(state);
  }

  // Deneb Utils
  public boolean isAvailabilityOfBlobSidecarsRequiredAtSlot(
      final ReadOnlyStore store, final UInt64 slot) {
    return isAvailabilityOfBlobSidecarsRequiredAtEpoch(store, computeEpochAtSlot(slot));
  }

  public boolean isAvailabilityOfBlobSidecarsRequiredAtEpoch(
      final ReadOnlyStore store, final UInt64 epoch) {
    if (!forkSchedule.getSpecMilestoneAtEpoch(epoch).isGreaterThanOrEqualTo(DENEB)) {
      return false;
    }
    final SpecConfig config = atEpoch(epoch).getConfig();
    final SpecConfigDeneb specConfigDeneb = SpecConfigDeneb.required(config);
    return getCurrentEpoch(store)
        .minusMinZero(epoch)
        .isLessThanOrEqualTo(specConfigDeneb.getMinEpochsForBlobSidecarsRequests());
  }

  public Optional<Integer> getMaxBlobsPerBlock() {
    return getSpecConfigDeneb().map(SpecConfigDeneb::getMaxBlobsPerBlock);
  }

  public Optional<Integer> getMaxBlobsPerBlock(final UInt64 slot) {
    return getSpecConfigDeneb(slot).map(SpecConfigDeneb::getMaxBlobsPerBlock);
  }

  public UInt64 computeSubnetForBlobSidecar(final BlobSidecar blobSidecar) {
    final SpecConfig config = atSlot(blobSidecar.getSlot()).getConfig();
    final SpecConfigDeneb specConfigDeneb = SpecConfigDeneb.required(config);
    return blobSidecar.getIndex().mod(specConfigDeneb.getBlobSidecarSubnetCount());
  }

  public Optional<UInt64> computeFirstSlotWithBlobSupport() {
    return getSpecConfigDeneb()
        .map(SpecConfigDeneb::getDenebForkEpoch)
        .map(this::computeStartSlotAtEpoch);
  }

  // Electra Utils
  public boolean isFormerDepositMechanismDisabled(final BeaconState state) {
    return atState(state).miscHelpers().isFormerDepositMechanismDisabled(state);
  }

  // Deneb private helpers
  private Optional<SpecConfigDeneb> getSpecConfigDeneb() {
    final SpecMilestone highestSupportedMilestone =
        getForkSchedule().getHighestSupportedMilestone();
    return Optional.ofNullable(forMilestone(highestSupportedMilestone))
        .map(SpecVersion::getConfig)
        .flatMap(SpecConfig::toVersionDeneb);
  }

  private Optional<SpecConfigDeneb> getSpecConfigDeneb(final UInt64 slot) {
    return atSlot(slot).getConfig().toVersionDeneb();
  }

  // Private helpers
  private SpecVersion atState(final BeaconState state) {
    return atSlot(state.getSlot());
  }

  private SpecVersion atBlock(final BeaconBlockSummary blockSummary) {
    return atSlot(blockSummary.getSlot());
  }

  private SpecVersion atForkVersion(final Bytes4 forkVersion) {
    final SpecMilestone milestone =
        forkSchedule
            .getSpecMilestoneAtForkVersion(forkVersion)
            .orElseThrow(
                () -> new IllegalArgumentException("Unknown fork version: " + forkVersion));

    return forMilestone(milestone);
  }

  private Fork getForkAtSlot(final UInt64 slot) {
    return forkSchedule.getFork(computeEpochAtSlot(slot));
  }

  private UInt64 getProposerSlashingEpoch(final ProposerSlashing proposerSlashing) {
    // Slashable blocks must be from same slot
    return computeEpochAtSlot(proposerSlashing.getHeader1().getMessage().getSlot());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Spec spec = (Spec) o;
    return Objects.equals(forkSchedule, spec.forkSchedule);
  }

  @Override
  public int hashCode() {
    return Objects.hash(forkSchedule);
  }
}
