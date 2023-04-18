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

package tech.pegasys.teku.api;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse.getValidatorStatus;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.api.blockselector.BlockSelectorFactory;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.api.migrated.BlockHeadersResponse;
import tech.pegasys.teku.api.migrated.BlockRewardData;
import tech.pegasys.teku.api.migrated.StateSyncCommitteesData;
import tech.pegasys.teku.api.migrated.StateValidatorBalanceData;
import tech.pegasys.teku.api.migrated.StateValidatorData;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.api.response.SszResponse;
import tech.pegasys.teku.api.response.v1.beacon.GenesisData;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.stateselector.StateSelectorFactory;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.Merkleizable;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ChainDataProvider {
  private static final Logger LOG = LogManager.getLogger();
  private final BlockSelectorFactory defaultBlockSelectorFactory;
  private final StateSelectorFactory defaultStateSelectorFactory;
  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final SchemaObjectProvider schemaObjectProvider;

  private final RecentChainData recentChainData;

  public ChainDataProvider(
      final Spec spec,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
    this.recentChainData = recentChainData;
    this.schemaObjectProvider = new SchemaObjectProvider(spec);
    this.defaultBlockSelectorFactory = new BlockSelectorFactory(spec, combinedChainDataClient);
    this.defaultStateSelectorFactory = new StateSelectorFactory(spec, combinedChainDataClient);
  }

  public UInt64 getCurrentEpoch(
      tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state) {
    return spec.getCurrentEpoch(state);
  }

  public UInt64 getGenesisTime() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    return recentChainData.getGenesisTime();
  }

  public GenesisData getGenesisData() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    final tech.pegasys.teku.spec.datastructures.genesis.GenesisData genesisData =
        recentChainData.getGenesisData().orElseThrow(ChainDataUnavailableException::new);
    return new GenesisData(
        genesisData.getGenesisTime(),
        genesisData.getGenesisValidatorsRoot(),
        spec.atEpoch(ZERO).getConfig().getGenesisForkVersion());
  }

  public tech.pegasys.teku.spec.datastructures.genesis.GenesisData getGenesisStateData() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    return recentChainData.getGenesisData().orElseThrow(ChainDataUnavailableException::new);
  }

  public Bytes4 getGenesisForkVersion() {
    return spec.atEpoch(ZERO).getConfig().getGenesisForkVersion();
  }

  public SafeFuture<Optional<BlockAndMetaData>> getBlockAndMetaData(final String slotParameter) {
    return defaultBlockSelectorFactory.defaultBlockSelector(slotParameter).getBlock();
  }

  public SafeFuture<Optional<ObjectAndMetaData<SignedBeaconBlock>>> getBlindedBlock(
      final String slotParameter) {
    return getBlock(slotParameter)
        .thenApply(
            maybeBlock ->
                maybeBlock.map(
                    blockAndData ->
                        blockAndData.map(
                            block ->
                                block.blind(spec.atSlot(block.getSlot()).getSchemaDefinitions()))));
  }

  public SafeFuture<Optional<ObjectAndMetaData<SignedBeaconBlock>>> getBlock(
      final String slotParameter) {
    return fromBlock(slotParameter, signedBeaconBlock -> signedBeaconBlock);
  }

  public SafeFuture<Optional<ObjectAndMetaData<SignedBeaconBlock>>> getSignedBeaconBlock(
      final String slotParameter) {
    return fromBlock(slotParameter, block -> block);
  }

  public SafeFuture<Optional<ObjectAndMetaData<Bytes32>>> getBlockRoot(final String slotParameter) {
    return fromBlock(slotParameter, SignedBeaconBlock::getRoot);
  }

  public SafeFuture<Optional<ObjectAndMetaData<List<Attestation>>>> getBlockAttestations(
      final String slotParameter) {
    return fromBlock(
        slotParameter,
        block -> block.getMessage().getBody().getAttestations().stream().collect(toList()));
  }

  public boolean isStoreAvailable() {
    return combinedChainDataClient.isStoreAvailable();
  }

  public SafeFuture<Optional<ObjectAndMetaData<BeaconState>>> getSchemaBeaconState(
      final String stateIdParam) {
    return fromState(stateIdParam, schemaObjectProvider::getBeaconState);
  }

  public SafeFuture<Optional<StateAndMetaData>> getBeaconStateAtHead() {
    return defaultStateSelectorFactory.headSelector().getState();
  }

  public SafeFuture<Optional<StateAndMetaData>> getBeaconStateAndMetadata(
      final String stateIdParam) {
    return defaultStateSelectorFactory.defaultStateSelector(stateIdParam).getState();
  }

  public SafeFuture<List<BlockAndMetaData>> getAllBlocksAtSlot(final UInt64 slot) {
    return defaultBlockSelectorFactory.nonCanonicalBlocksSelector(slot).getBlocks();
  }

  public SafeFuture<Optional<tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState>>
      getBeaconStateByBlockRoot(final String blockRootParam) {
    return defaultStateSelectorFactory
        .byBlockRootStateSelector(blockRootParam)
        .getState()
        .thenApply(maybeState -> maybeState.map(ObjectAndMetaData::getData));
  }

  public SafeFuture<Optional<SszResponse>> getBeaconStateSszByBlockRoot(
      final String blockRootParam) {
    return defaultStateSelectorFactory
        .byBlockRootStateSelector(blockRootParam)
        .getState()
        .thenApply(
            maybeState ->
                maybeState
                    .map(ObjectAndMetaData::getData)
                    .map(
                        state ->
                            new SszResponse(
                                new ByteArrayInputStream(state.sszSerialize().toArrayUnsafe()),
                                state.hashTreeRoot().toUnprefixedHexString(),
                                spec.atSlot(state.getSlot()).getMilestone())));
  }

  public ForkChoiceData getForkChoiceData() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    return new ForkChoiceData(
        recentChainData.getJustifiedCheckpoint().orElseThrow(),
        recentChainData.getFinalizedCheckpoint().orElseThrow(),
        recentChainData
            .getForkChoiceStrategy()
            .map(ReadOnlyForkChoiceStrategy::getBlockData)
            .orElse(emptyList()));
  }

  private Optional<Integer> validatorParameterToIndex(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state,
      final String validatorParameter) {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }

    if (validatorParameter.toLowerCase().startsWith("0x")) {
      final Bytes48 keyBytes = getBytes48FromParameter(validatorParameter);
      try {
        return spec.getValidatorIndex(state, BLSPublicKey.fromBytesCompressed(keyBytes));
      } catch (IllegalArgumentException ex) {
        return Optional.empty();
      }
    }
    try {
      final UInt64 numericValidator = UInt64.valueOf(validatorParameter);
      if (numericValidator.isGreaterThan(UInt64.valueOf(Integer.MAX_VALUE))) {
        throw new BadRequestException(
            String.format("Validator Index is too high to use: %s", validatorParameter));
      }
      final int validatorIndex = numericValidator.intValue();
      final int validatorCount = state.getValidators().size();
      if (validatorIndex > validatorCount) {
        return Optional.empty();
      }
      return Optional.of(validatorIndex);
    } catch (NumberFormatException ex) {
      throw new BadRequestException(String.format("Invalid validator: %s", validatorParameter));
    }
  }

  private Bytes48 getBytes48FromParameter(final String validatorParameter) {
    try {
      if (validatorParameter.length() != 98) {
        throw new IllegalArgumentException(
            String.format(
                "Expected a length of 98 for a hex encoded bytes48 with 0x prefix, but got length %s",
                validatorParameter.length()));
      }
      return Bytes48.fromHexString(validatorParameter);
    } catch (IllegalArgumentException ex) {
      throw new BadRequestException(
          String.format("Invalid public key: %s; %s", validatorParameter, ex.getMessage()));
    }
  }

  public SafeFuture<Optional<ObjectAndMetaData<Fork>>> getStateFork(final String stateIdParam) {
    return fromState(stateIdParam, state -> new Fork(state.getFork()));
  }

  public SafeFuture<Optional<ObjectAndMetaData<List<StateValidatorBalanceData>>>>
      getStateValidatorBalances(final String stateIdParam, final List<String> validators) {
    return fromState(stateIdParam, state -> getValidatorBalancesFromState(state, validators));
  }

  @VisibleForTesting
  List<StateValidatorBalanceData> getValidatorBalancesFromState(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state,
      final List<String> validators) {
    return getValidatorSelector(state, validators)
        .mapToObj(index -> StateValidatorBalanceData.fromState(state, index))
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
  }

  public Optional<Bytes32> getStateRootFromBlockRoot(final Bytes32 blockRoot) {
    return combinedChainDataClient
        .getStateByBlockRoot(blockRoot)
        .join()
        .map(Merkleizable::hashTreeRoot);
  }

  public SafeFuture<BlockHeadersResponse> getBlockHeaders(
      final Optional<Bytes32> parentRoot, final Optional<UInt64> slot) {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    if (parentRoot.isPresent()) {
      return SafeFuture.completedFuture(new BlockHeadersResponse(false, false, emptyList()));
    }

    final UInt64 actualSlot = slot.orElse(combinedChainDataClient.getHeadSlot());
    return defaultBlockSelectorFactory
        .nonCanonicalBlocksSelector(actualSlot)
        .getBlocks()
        .thenApply(
            blockAndMetadataList -> {
              final boolean executionOptimistic =
                  blockAndMetadataList.stream().anyMatch(BlockAndMetaData::isExecutionOptimistic);
              return new BlockHeadersResponse(
                  executionOptimistic,
                  combinedChainDataClient.isFinalized(actualSlot),
                  blockAndMetadataList);
            });
  }

  public SafeFuture<Optional<ObjectAndMetaData<List<StateValidatorData>>>> getStateValidators(
      final String stateIdParam,
      final List<String> validators,
      final Set<ValidatorStatus> statusFilter) {
    return fromState(
        stateIdParam, state -> getFilteredValidatorList(state, validators, statusFilter));
  }

  public SafeFuture<Optional<ObjectAndMetaData<Optional<Bytes32>>>> getRandaoAtEpoch(
      final String stateIdParam, final Optional<UInt64> epoch) {
    return fromState(stateIdParam, state -> getRandaoAtEpochFromState(state, epoch));
  }

  @VisibleForTesting
  Optional<Bytes32> getRandaoAtEpochFromState(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state,
      final Optional<UInt64> maybeEpoch) {
    final UInt64 stateEpoch = spec.computeEpochAtSlot(state.getSlot());
    final int epochsPerHistoricalVector =
        spec.atEpoch(stateEpoch).getConfig().getEpochsPerHistoricalVector();
    final UInt64 epoch = maybeEpoch.orElseGet(() -> spec.computeEpochAtSlot(state.getSlot()));
    if (epoch.isGreaterThan(stateEpoch)) {
      return Optional.empty();
    } else if (epoch.isLessThan(stateEpoch)
        && stateEpoch.minusMinZero(epochsPerHistoricalVector).isGreaterThan(0)
        && stateEpoch.minusMinZero(epochsPerHistoricalVector).isGreaterThanOrEqualTo(epoch)) {
      // ignoring the first period of epoch=0 to epochsPerHistoricalVector,
      // return empty if the epoch is not within `epochsPerHistoricalVector` of the state epoch
      return Optional.empty();
    }
    return Optional.of(
        spec.atSlot(state.getSlot()).beaconStateAccessors().getRandaoMix(state, epoch));
  }

  @VisibleForTesting
  List<StateValidatorData> getFilteredValidatorList(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state,
      final List<String> validators,
      final Set<ValidatorStatus> statusFilter) {
    final UInt64 epoch = spec.getCurrentEpoch(state);
    return getValidatorSelector(state, validators)
        .filter(getStatusPredicate(state, statusFilter))
        .mapToObj(index -> StateValidatorData.fromState(state, index, epoch, FAR_FUTURE_EPOCH))
        .flatMap(Optional::stream)
        .collect(toList());
  }

  public Optional<ObjectAndMetaData<StateValidatorData>> getStateValidator(
      final StateAndMetaData stateData, final String validatorIdParam) {
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state =
        stateData.getData();
    final UInt64 epoch = getCurrentEpoch(state);
    final Optional<StateValidatorData> maybeValidator =
        getValidatorSelector(state, List.of(validatorIdParam))
            .mapToObj(index -> StateValidatorData.fromState(state, index, epoch, FAR_FUTURE_EPOCH))
            .flatMap(Optional::stream)
            .findFirst();

    return maybeValidator.map(data -> stateData.map(__ -> data));
  }

  public SafeFuture<Optional<ObjectAndMetaData<List<CommitteeAssignment>>>> getStateCommittees(
      final String stateIdParameter,
      final Optional<UInt64> epoch,
      final Optional<UInt64> committeeIndex,
      final Optional<UInt64> slot) {
    return fromState(
        stateIdParameter, state -> getCommitteesFromState(state, epoch, committeeIndex, slot));
  }

  public SafeFuture<Optional<ValidatorStatuses>> getValidatorInclusionAtEpoch(final UInt64 epoch) {
    final Optional<UInt64> maybeCurrentEpoch = getCurrentEpoch();
    if (maybeCurrentEpoch.isEmpty()) {
      throw new ServiceUnavailableException();
    }
    if (epoch.isGreaterThanOrEqualTo(maybeCurrentEpoch.get())) {
      throw new IllegalArgumentException("Cannot query epoch until the epoch is completed.");
    }
    // 'current' epoch is requested, and computation is done on epoch transition to 'next' epoch
    // so to get the correct values, we need to get validator statuses from the last slot of the
    // epoch requested
    // For this reason, we can't process the actual current epoch, it will be a completed epoch
    // required
    final UInt64 slotRequired = spec.computeStartSlotAtEpoch(epoch.plus(1)).minus(1);
    return getBeaconStateAndMetadata(slotRequired.toString())
        .thenApply(
            maybeStateAndMetadata ->
                maybeStateAndMetadata.map(
                    stateAndMetadata ->
                        spec.atSlot(stateAndMetadata.getData().getSlot())
                            .getValidatorStatusFactory()
                            .createValidatorStatuses(stateAndMetadata.getData())));
  }

  public Optional<UInt64> getCurrentEpoch() {
    return recentChainData.getCurrentEpoch();
  }

  List<CommitteeAssignment> getCommitteesFromState(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state,
      final Optional<UInt64> epoch,
      final Optional<UInt64> committeeIndex,
      final Optional<UInt64> slot) {
    final Predicate<CommitteeAssignment> slotFilter =
        slot.isEmpty() ? __ -> true : (assignment) -> assignment.getSlot().equals(slot.get());

    final Predicate<CommitteeAssignment> committeeFilter =
        committeeIndex.isEmpty()
            ? __ -> true
            : (assignment) -> assignment.getCommitteeIndex().compareTo(committeeIndex.get()) == 0;

    final UInt64 stateEpoch = spec.computeEpochAtSlot(state.getSlot());
    if (epoch.isPresent() && epoch.get().isGreaterThan(stateEpoch.plus(ONE))) {
      throw new BadRequestException(
          "Epoch " + epoch.get() + " is too far ahead of state epoch " + stateEpoch);
    }
    if (slot.isPresent()) {
      final UInt64 computeEpochAtSlot = spec.computeEpochAtSlot(slot.get());
      if (!computeEpochAtSlot.equals(epoch.orElse(stateEpoch))) {
        throw new BadRequestException(
            "Slot " + slot.get() + " is not in epoch " + epoch.orElse(stateEpoch));
      }
    }
    return combinedChainDataClient.getCommitteesFromState(state, epoch.orElse(stateEpoch)).stream()
        .filter(slotFilter)
        .filter(committeeFilter)
        .collect(toList());
  }

  private IntPredicate getStatusPredicate(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state,
      final Set<ValidatorStatus> statusFilter) {
    final UInt64 epoch = spec.getCurrentEpoch(state);
    return statusFilter.isEmpty()
        ? i -> true
        : i -> statusFilter.contains(getValidatorStatus(state, i, epoch, FAR_FUTURE_EPOCH));
  }

  private IntStream getValidatorSelector(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state,
      final List<String> validators) {
    return validators.isEmpty()
        ? IntStream.range(0, state.getValidators().size())
        : validators.stream()
            .flatMapToInt(
                validatorParameter ->
                    validatorParameterToIndex(state, validatorParameter).stream().mapToInt(a -> a));
  }

  public List<ProtoNodeData> getChainHeads() {
    return recentChainData.getChainHeads();
  }

  // Returns false
  //  - if Altair milestone is not supported,
  //  - if a slot is specified and that slot is a phase 0 slot
  // otherwise true will be returned

  public boolean stateParameterMaySupportAltair(final String epochParam) {
    if (!spec.isMilestoneSupported(SpecMilestone.ALTAIR)) {
      return false;
    }
    try {
      final UInt64 slot = UInt64.valueOf(epochParam);
      return spec.atSlot(slot).getMilestone() != SpecMilestone.PHASE0;
    } catch (NumberFormatException e) {
      LOG.trace(e);
    }
    return true;
  }

  public SafeFuture<Optional<ObjectAndMetaData<LightClientBootstrap>>> getLightClientBoostrap(
      final Bytes32 blockRootParam) {
    return defaultStateSelectorFactory
        .forBlockRoot(blockRootParam)
        .getState()
        .thenApply(maybeStateData -> maybeStateData.flatMap(this::getLightClientBootstrap));
  }

  private Optional<ObjectAndMetaData<LightClientBootstrap>> getLightClientBootstrap(
      final StateAndMetaData stateAndMetaData) {
    return spec.getLightClientUtil(stateAndMetaData.getData().getSlot())
        .map(clientUtil -> stateAndMetaData.map(clientUtil::getLightClientBootstrap));
  }

  public SafeFuture<Optional<ObjectAndMetaData<StateSyncCommitteesData>>> getStateSyncCommittees(
      final String stateIdParam, final Optional<UInt64> epoch) {
    return fromState(stateIdParam, state -> getSyncCommitteesFromState(state, epoch));
  }

  private StateSyncCommitteesData getSyncCommitteesFromState(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state,
      final Optional<UInt64> epochQueryParam) {
    final UInt64 epoch = epochQueryParam.orElse(spec.computeEpochAtSlot(state.getSlot()));
    final UInt64 slot = spec.computeStartSlotAtEpoch(epoch);

    final Optional<SyncCommittee> maybeCommittee =
        spec.getSyncCommitteeUtil(slot).map(util -> util.getSyncCommittee(state, epoch));
    // * if the requested epoch is outside of valid range, an illegalArgumentException is raised
    // * if getSyncCommitteeUtil was not present, maybeCommittee will be empty,
    //   indicating the state is pre-altair, and in this case, an empty committees list can be
    // returned
    if (maybeCommittee.isEmpty()) {
      return new StateSyncCommitteesData(List.of(), List.of());
    }

    final SyncCommittee committee = maybeCommittee.get();
    final List<UInt64> committeeIndices =
        committee.getPubkeys().stream()
            .flatMap(pubkey -> spec.getValidatorIndex(state, pubkey.getBLSPublicKey()).stream())
            .map(UInt64::valueOf)
            .collect(toList());

    return new StateSyncCommitteesData(
        committeeIndices,
        Lists.partition(
            committeeIndices, spec.atEpoch(epoch).getConfig().getTargetCommitteeSize()));
  }

  public SafeFuture<Optional<SyncCommitteeRewardData>> getSyncCommitteeRewardsFromBlockId(
      final String blockId, final Set<String> validators) {
    return getBlockAndMetaData(blockId)
        .thenCompose(
            result -> {
              if (result.isEmpty() || result.get().getData().getBeaconBlock().isEmpty()) {
                return SafeFuture.completedFuture(Optional.empty());
              }
              final BlockAndMetaData blockAndMetaData = result.get();
              final BeaconBlock block = blockAndMetaData.getData().getBeaconBlock().get();
              final SyncCommitteeRewardData data =
                  new SyncCommitteeRewardData(
                      blockAndMetaData.isExecutionOptimistic(), blockAndMetaData.isFinalized());

              return combinedChainDataClient
                  .getStateByBlockRoot(block.getRoot())
                  .thenApply(
                      maybeState ->
                          maybeState.map(
                              state -> getSyncCommitteeRewardData(validators, block, data, state)));
            });
  }

  private SyncCommitteeRewardData getSyncCommitteeRewardData(
      Set<String> validators,
      BeaconBlock block,
      SyncCommitteeRewardData data,
      tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state) {
    if (!spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      throw new BadRequestException(
          "Slot "
              + block.getSlot()
              + " is pre altair, and no sync committee information is available");
    }

    final UInt64 epoch = spec.computeEpochAtSlot(block.getSlot());
    final SyncCommittee committee =
        spec.getSyncCommitteeUtil(block.getSlot()).orElseThrow().getSyncCommittee(state, epoch);
    final List<BLSPublicKey> committeeKeys =
        committee.getPubkeys().stream().map(SszPublicKey::getBLSPublicKey).collect(toList());
    final Map<Integer, Integer> committeeIndices =
        getCommitteeIndices(committeeKeys, validators, state);
    final UInt64 participantReward = spec.getSyncCommitteeParticipantReward(state);
    return calculateRewards(committeeIndices, participantReward.longValue(), block, data);
  }

  @VisibleForTesting
  protected Map<Integer, Integer> getCommitteeIndices(
      final List<BLSPublicKey> committeeKeys,
      final Set<String> validators,
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state) {
    if (validators.isEmpty()) {
      final List<Integer> result =
          committeeKeys.stream()
              .flatMap(pubkey -> spec.getValidatorIndex(state, pubkey).stream())
              .collect(toList());

      return IntStream.range(0, result.size())
          .boxed()
          .collect(Collectors.<Integer, Integer, Integer>toMap(Function.identity(), result::get));
    }

    checkValidatorsList(committeeKeys, validators);

    final Map<Integer, Integer> output = new HashMap<>();
    for (int i = 0; i < committeeKeys.size(); i++) {
      final BLSPublicKey key = committeeKeys.get(i);
      final Optional<Integer> validatorIndex = spec.getValidatorIndex(state, key);
      if (validatorIndex.isPresent()
          && (validators.contains(key.toHexString())
              || validators.contains(validatorIndex.get().toString()))) {
        output.put(i, validatorIndex.get());
      }
    }

    return output;
  }

  private void checkValidatorsList(List<BLSPublicKey> committeeKeys, Set<String> validators) {
    final Set<BLSPublicKey> keysSet = new HashSet<>(committeeKeys);
    for (String v : validators) {
      final String errorMessage =
          String.format(
              "'%s' is not a valid hex encoded public key or validator index in the committee", v);

      if (v.startsWith("0x")) {
        if (!keysSet.contains(BLSPublicKey.fromHexString(v))) {
          throw new BadRequestException(errorMessage);
        }
      } else {
        try {
          final int index = Integer.parseInt(v);
          if (index < 0 || index >= committeeKeys.size()) {
            throw new BadRequestException(errorMessage);
          }
        } catch (NumberFormatException e) {
          throw new BadRequestException(errorMessage);
        }
      }
    }
  }

  @VisibleForTesting
  protected SyncCommitteeRewardData calculateRewards(
      final Map<Integer, Integer> committeeIndices,
      final Long participantReward,
      final BeaconBlock block,
      final SyncCommitteeRewardData data) {
    final Optional<SyncAggregate> aggregate = block.getBody().getOptionalSyncAggregate();
    if (aggregate.isEmpty()) {
      return data;
    }

    committeeIndices.forEach(
        (i, key) -> {
          if (aggregate.get().getSyncCommitteeBits().getBit(i)) {
            data.increaseReward(key, participantReward);
          } else {
            data.decreaseReward(key, participantReward);
          }
        });

    return data;
  }

  public SafeFuture<Optional<ObjectAndMetaData<BlockRewardData>>> getBlockRewardsFromBlockId(
      final String blockId) {
    return getBlockAndMetaData(blockId)
        .thenCompose(
            result -> {
              if (result.isEmpty() || result.get().getData().getBeaconBlock().isEmpty()) {
                return SafeFuture.completedFuture(Optional.empty());
              }
              final BlockAndMetaData blockAndMetaData = result.get();
              final BeaconBlock block = blockAndMetaData.getData().getBeaconBlock().get();

              return combinedChainDataClient
                  .getStateByBlockRoot(block.getRoot())
                  .thenApply(
                      maybeState ->
                          maybeState.map(state -> getBlockRewardData(blockAndMetaData, state)));
            });
  }

  @VisibleForTesting
  protected ObjectAndMetaData<BlockRewardData> getBlockRewardData(
      final BlockAndMetaData blockAndMetaData,
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state) {
    final BeaconBlock block = blockAndMetaData.getData().getMessage();
    if (!spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      throw new BadRequestException(
          "Slot "
              + block.getSlot()
              + " is pre altair, and no sync committee information is available");
    }

    final UInt64 participantReward = spec.getSyncCommitteeParticipantReward(state);
    final long proposerReward =
        participantReward
            .times(PROPOSER_WEIGHT)
            .dividedBy(WEIGHT_DENOMINATOR.minus(PROPOSER_WEIGHT))
            .longValue();
    return blockAndMetaData.map(__ -> calculateBlockRewards(proposerReward, block, state));
  }

  private BlockRewardData calculateBlockRewards(
      final long proposerReward,
      final BeaconBlock block,
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state) {
    final SyncAggregate aggregate = block.getBody().getOptionalSyncAggregate().orElseThrow();

    final int proposerIndex = spec.getBeaconProposerIndex(state, block.getSlot());
    final long attestationsBlockRewards = calculateAttestationRewards(state);
    final long syncAggregateBlockRewards =
        calculateProposerSyncAggregateBlockRewards(proposerReward, aggregate);
    final long proposerSlashingsBlockRewards = calculateProposerSlashingsRewards(block, state);
    final long attesterSlashingsBlockRewards = calculateAttesterSlashingsRewards(block, state);

    return new BlockRewardData(
        proposerIndex,
        attestationsBlockRewards,
        syncAggregateBlockRewards,
        proposerSlashingsBlockRewards,
        attesterSlashingsBlockRewards);
  }

  @VisibleForTesting
  protected long calculateProposerSyncAggregateBlockRewards(
      long proposerReward, SyncAggregate aggregate) {
    final SszBitvector syncCommitteeBits = aggregate.getSyncCommitteeBits();
    return proposerReward * syncCommitteeBits.getBitCount();
  }

  @VisibleForTesting
  protected long calculateProposerSlashingsRewards(
      final BeaconBlock beaconBlock,
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state) {
    final SszList<ProposerSlashing> proposerSlashings =
        beaconBlock.getBody().getProposerSlashings();

    final UInt64 epoch = spec.computeEpochAtSlot(state.getSlot());
    final SpecConfig specConfig = spec.getSpecConfig(epoch);

    long proposerSlashingsRewards = 0;
    for (ProposerSlashing slashing : proposerSlashings) {
      final int slashedIndex = slashing.getHeader1().getMessage().getProposerIndex().intValue();
      proposerSlashingsRewards =
          calculateSlashingRewards(specConfig, state, slashedIndex, proposerSlashingsRewards);
    }

    return proposerSlashingsRewards;
  }

  @VisibleForTesting
  protected long calculateAttesterSlashingsRewards(
      final BeaconBlock beaconBlock,
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state) {
    final SszList<AttesterSlashing> attesterSlashings =
        beaconBlock.getBody().getAttesterSlashings();

    final UInt64 epoch = spec.computeEpochAtSlot(state.getSlot());
    final SpecConfig specConfig = spec.getSpecConfig(epoch);

    long attesterSlashingsRewards = 0;
    for (AttesterSlashing slashing : attesterSlashings) {
      for (final UInt64 index : slashing.getIntersectingValidatorIndices()) {
        attesterSlashingsRewards =
            calculateSlashingRewards(specConfig, state, index.intValue(), attesterSlashingsRewards);
      }
    }

    return attesterSlashingsRewards;
  }

  private long calculateSlashingRewards(
      final SpecConfig specConfig,
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state,
      final int slashedIndex,
      final long currentRewards) {
    final Validator validator = state.getValidators().get(slashedIndex);
    final UInt64 whistleblowerReward =
        validator.getEffectiveBalance().dividedBy(specConfig.getWhistleblowerRewardQuotient());
    final UInt64 proposerReward =
        whistleblowerReward.dividedBy(specConfig.getProposerRewardQuotient());
    final UInt64 rewardsAdditions = proposerReward.plus(whistleblowerReward.minus(proposerReward));
    return currentRewards + rewardsAdditions.longValue();
  }

  @VisibleForTesting
  protected long calculateAttestationRewards(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state) {
    final SpecMilestone specMilestone = getMilestoneAtSlot(state.getSlot());
    final SpecVersion specVersion = spec.forMilestone(specMilestone);
    final EpochProcessor epochProcessor = specVersion.getEpochProcessor();
    final ValidatorStatuses validatorStatuses =
        specVersion.getValidatorStatusFactory().createValidatorStatuses(state);
    final RewardAndPenaltyDeltas rewardAndPenaltyDeltas =
        epochProcessor.getRewardAndPenaltyDeltas(state, validatorStatuses);

    long rewards = 0;
    for (int i = 0; i < state.getValidators().size(); i++) {
      final RewardAndPenaltyDeltas.RewardAndPenalty rewardAndPenalty =
          rewardAndPenaltyDeltas.getDelta(i);
      rewards +=
          rewardAndPenalty.getReward().longValue() - rewardAndPenalty.getPenalty().longValue();
    }

    return rewards;
  }

  public SpecMilestone getMilestoneAtSlot(final UInt64 slot) {
    return spec.atSlot(slot).getMilestone();
  }

  public SafeFuture<Optional<ObjectAndMetaData<List<Withdrawal>>>> getExpectedWithdrawals(
      String stateParameter, Optional<UInt64> optionalProposalSlot) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateParameter)
        .getState()
        .thenApply(
            maybeStateData ->
                maybeStateData.map(
                    stateAndMetaData -> {
                      List<Withdrawal> withdrawals =
                          getExpectedWithdrawalsFromState(
                              stateAndMetaData.getData(), optionalProposalSlot);
                      return new ObjectAndMetaData<>(
                          withdrawals,
                          stateAndMetaData.getMilestone(),
                          stateAndMetaData.isExecutionOptimistic(),
                          stateAndMetaData.isCanonical(),
                          stateAndMetaData.isFinalized());
                    }));
  }

  List<Withdrawal> getExpectedWithdrawalsFromState(
      tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState data,
      Optional<UInt64> optionalProposalSlot) {
    final UInt64 proposalSlot = optionalProposalSlot.orElse(data.getSlot().increment());
    // Apply some sanity checks prior to computing pre-state
    if (!spec.atSlot(proposalSlot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      throw new BadRequestException(
          "Requested withdrawals for a pre-capella slot: " + proposalSlot);
    } else if (proposalSlot.isLessThanOrEqualTo(data.getSlot())) {
      // expectedWithdrawals is computing a forward-looking set of withdrawals. if looking for
      // withdrawals from a state, this is the incorrect endpoint.
      throw new BadRequestException(
          "Requested withdrawals for a historic slot, state slot: "
              + data.getSlot()
              + " vs. proposal slot: "
              + proposalSlot);
    } else if (proposalSlot.minusMinZero(128).isGreaterThan(data.getSlot())) {
      // 128 slots is 4 epochs, which would suggest 4 epochs of empty blocks to process
      throw new BadRequestException(
          "Requested withdrawals for a proposal slot: "
              + proposalSlot
              + " that is more than 128 slots ahead of the supplied state slot: "
              + data.getSlot());
    }
    try {
      // need to get preState
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState preState =
          spec.processSlots(data, proposalSlot);
      return spec.atSlot(proposalSlot)
          .getBlockProcessor()
          .getExpectedWithdrawals(preState)
          .orElse(List.of());
    } catch (SlotProcessingException | EpochProcessingException e) {
      LOG.debug("Failed to get expected withdrawals for slot {}", proposalSlot, e);
    }
    return List.of();
  }

  public SafeFuture<Optional<Bytes32>> getFinalizedBlockRoot(final UInt64 slot) {
    final SafeFuture<Optional<SignedBeaconBlock>> futureFinalizedBlock =
        combinedChainDataClient.getFinalizedBlockInEffectAtSlot(slot);
    return futureFinalizedBlock.thenApply(
        maybeFinalizedBlock ->
            maybeFinalizedBlock
                .filter(block -> block.getSlot().equals(slot))
                .map(SignedBeaconBlock::getRoot));
  }

  public SpecMilestone getMilestoneAtHead() {
    return spec.atSlot(recentChainData.getHeadSlot()).getMilestone();
  }

  private <T> SafeFuture<Optional<ObjectAndMetaData<T>>> fromBlock(
      final String slotParameter, final Function<SignedBeaconBlock, T> mapper) {
    return defaultBlockSelectorFactory
        .defaultBlockSelector(slotParameter)
        .getBlock()
        .thenApply(maybeBlockData -> maybeBlockData.map(blockData -> blockData.map(mapper)));
  }

  private <T> SafeFuture<Optional<ObjectAndMetaData<T>>> fromState(
      final String stateIdParam,
      final Function<tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState, T>
          mapper) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateIdParam)
        .getState()
        .thenApply(maybeStateData -> maybeStateData.map(blockData -> blockData.map(mapper)));
  }
}
