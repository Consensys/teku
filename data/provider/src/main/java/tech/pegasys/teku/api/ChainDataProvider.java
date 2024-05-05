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

package tech.pegasys.teku.api;

import static java.util.Collections.emptyList;
import static tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse.getValidatorStatus;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.api.blobselector.BlobSidecarSelectorFactory;
import tech.pegasys.teku.api.blockselector.BlockSelectorFactory;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.api.migrated.AttestationRewardsData;
import tech.pegasys.teku.api.migrated.BlockHeadersResponse;
import tech.pegasys.teku.api.migrated.BlockRewardData;
import tech.pegasys.teku.api.migrated.GetAttestationRewardsResponse;
import tech.pegasys.teku.api.migrated.StateSyncCommitteesData;
import tech.pegasys.teku.api.migrated.StateValidatorBalanceData;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.api.response.v1.beacon.GenesisData;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.rewards.EpochAttestationRewardsCalculator;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.stateselector.StateSelectorFactory;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.metadata.BlobSidecarsAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ChainDataProvider {
  private static final Logger LOG = LogManager.getLogger();
  private final BlockSelectorFactory blockSelectorFactory;
  private final StateSelectorFactory stateSelectorFactory;
  private final BlobSidecarSelectorFactory blobSidecarSelectorFactory;
  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final SchemaObjectProvider schemaObjectProvider;
  private final RecentChainData recentChainData;
  private final RewardCalculator rewardCalculator;

  public ChainDataProvider(
      final Spec spec,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient,
      final RewardCalculator rewardCalculator) {
    this(
        spec,
        recentChainData,
        combinedChainDataClient,
        new BlockSelectorFactory(spec, combinedChainDataClient),
        new StateSelectorFactory(spec, combinedChainDataClient),
        new BlobSidecarSelectorFactory(spec, combinedChainDataClient),
        rewardCalculator);
  }

  @VisibleForTesting
  ChainDataProvider(
      final Spec spec,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient,
      final BlockSelectorFactory blockSelectorFactory,
      final StateSelectorFactory stateSelectorFactory,
      final BlobSidecarSelectorFactory blobSidecarSelectorFactory,
      final RewardCalculator rewardCalculator) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
    this.recentChainData = recentChainData;
    this.schemaObjectProvider = new SchemaObjectProvider(spec);
    this.blockSelectorFactory = blockSelectorFactory;
    this.stateSelectorFactory = stateSelectorFactory;
    this.blobSidecarSelectorFactory = blobSidecarSelectorFactory;
    this.rewardCalculator = rewardCalculator;
  }

  public UInt64 getCurrentEpoch(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state) {
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

  public SafeFuture<Optional<BlockAndMetaData>> getBlockAndMetaData(final String blockIdParam) {
    return blockSelectorFactory.createSelectorForBlockId(blockIdParam).getBlock();
  }

  public SafeFuture<Optional<ObjectAndMetaData<SignedBeaconBlock>>> getBlindedBlock(
      final String blockIdParam) {
    return getBlock(blockIdParam)
        .thenApply(
            maybeBlock ->
                maybeBlock.map(
                    blockAndData ->
                        blockAndData.map(
                            block ->
                                block.blind(spec.atSlot(block.getSlot()).getSchemaDefinitions()))));
  }

  public SafeFuture<Optional<ObjectAndMetaData<SignedBeaconBlock>>> getBlock(
      final String blockIdParam) {
    return fromBlock(blockIdParam, Function.identity());
  }

  public SafeFuture<Optional<BlobSidecarsAndMetaData>> getBlobSidecars(
      final String blockIdParam, final List<UInt64> indices) {
    return blobSidecarSelectorFactory
        .createSelectorForBlockId(blockIdParam)
        .getBlobSidecars(indices);
  }

  public SafeFuture<Optional<List<BlobSidecar>>> getAllBlobSidecarsAtSlot(
      final UInt64 slot, final List<UInt64> indices) {
    return blobSidecarSelectorFactory
        .slotSelectorForAll(slot)
        .getBlobSidecars(indices)
        .thenApply(
            maybeBlobSideCarsMetaData -> maybeBlobSideCarsMetaData.map(ObjectAndMetaData::getData));
  }

  public SafeFuture<Optional<ObjectAndMetaData<Bytes32>>> getBlockRoot(final String blockIdParam) {
    return fromBlock(blockIdParam, SignedBeaconBlock::getRoot);
  }

  public SafeFuture<Optional<ObjectAndMetaData<List<Attestation>>>> getBlockAttestations(
      final String blockIdParam) {
    return fromBlock(
        blockIdParam, block -> block.getMessage().getBody().getAttestations().stream().toList());
  }

  public boolean isStoreAvailable() {
    return combinedChainDataClient.isStoreAvailable();
  }

  public SafeFuture<Optional<ObjectAndMetaData<BeaconState>>> getSchemaBeaconState(
      final String stateIdParam) {
    return fromState(stateIdParam, schemaObjectProvider::getBeaconState);
  }

  public SafeFuture<Optional<StateAndMetaData>> getBeaconStateAtHead() {
    return stateSelectorFactory.headSelector().getState();
  }

  public SafeFuture<Optional<StateAndMetaData>> getBeaconStateAndMetadata(
      final String stateIdParam) {
    return stateSelectorFactory.createSelectorForStateId(stateIdParam).getState();
  }

  public SafeFuture<List<BlockAndMetaData>> getAllBlocksAtSlot(final UInt64 slot) {
    return blockSelectorFactory.nonCanonicalBlocksSelector(slot).getBlocks();
  }

  public SafeFuture<Optional<tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState>>
      getBeaconStateByBlockId(final String blockIdParam) {
    return stateSelectorFactory
        .createSelectorForBlockId(blockIdParam)
        .getState()
        .thenApply(maybeState -> maybeState.map(ObjectAndMetaData::getData));
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

    if (validatorParameter.toLowerCase(Locale.ROOT).startsWith("0x")) {
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
        .toList();
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
    return blockSelectorFactory
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
        .toList();
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
      final String stateIdParam,
      final Optional<UInt64> epoch,
      final Optional<UInt64> committeeIndex,
      final Optional<UInt64> slot) {
    return fromState(
        stateIdParam, state -> getCommitteesFromState(state, epoch, committeeIndex, slot));
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
        .toList();
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
    return stateSelectorFactory
        .blockRootSelector(blockRootParam)
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
            .toList();

    return new StateSyncCommitteesData(
        committeeIndices,
        Lists.partition(
            committeeIndices, spec.atEpoch(epoch).getConfig().getTargetCommitteeSize()));
  }

  public SafeFuture<Optional<SyncCommitteeRewardData>> getSyncCommitteeRewardsFromBlockId(
      final String blockIdParam, final Set<String> validators) {
    return getBlockAndMetaData(blockIdParam)
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
                          maybeState.map(
                              state ->
                                  rewardCalculator.getSyncCommitteeRewardData(
                                      validators, blockAndMetaData, state)));
            });
  }

  public SafeFuture<Optional<ObjectAndMetaData<BlockRewardData>>> getBlockRewardsFromBlockId(
      final String blockIdParam) {
    return getBlockAndMetaData(blockIdParam)
        .thenCompose(
            maybeBlockAndMetadata -> {
              if (maybeBlockAndMetadata.isEmpty()
                  || maybeBlockAndMetadata.get().getData().getBeaconBlock().isEmpty()) {
                return SafeFuture.completedFuture(Optional.empty());
              }
              final BlockAndMetaData blockAndMetaData = maybeBlockAndMetadata.get();
              final BeaconBlock block = blockAndMetaData.getData().getBeaconBlock().get();

              return combinedChainDataClient
                  .getStateAtSlotExact(block.getSlot().decrement())
                  .thenApply(
                      maybeState ->
                          maybeState.map(
                              state ->
                                  rewardCalculator.getBlockRewardDataAndMetaData(
                                      blockAndMetaData, state)));
            });
  }

  public SafeFuture<Optional<GetAttestationRewardsResponse>> calculateAttestationRewardsAtEpoch(
      final UInt64 epoch, final List<String> validatorsPubKeys) {
    final UInt64 slot;
    try {
      slot = findSlotAtEndOfNextEpoch(epoch);
    } catch (final ArithmeticException e) {
      final UInt64 availableEpoch = findLatestAvailableEpochForRewardCalculation().orElseThrow();
      throw new BadRequestException(
          "Invalid epoch range. Latest available epoch for reward calculation is "
              + availableEpoch);
    }

    if (!spec.atEpoch(epoch).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      throw new BadRequestException(
          "Can't calculate attestation rewards for for epoch " + epoch + " pre Altair");
    }

    return getBeaconStateAndMetadata(slot.toString())
        .thenApply(
            maybeState -> {
              final StateAndMetaData stateAndMetadata =
                  maybeState.orElseThrow(
                      () -> {
                        final String availableEpochString =
                            findLatestAvailableEpochForRewardCalculation()
                                .map(UInt64::toString)
                                .orElse("<unknown>");
                        return new BadRequestException(
                            "Invalid epoch range. Latest available epoch for reward calculation is "
                                + availableEpochString);
                      });

              final AttestationRewardsData attestationRewardsData =
                  new EpochAttestationRewardsCalculator(
                          spec.atSlot(slot), stateAndMetadata.getData(), validatorsPubKeys)
                      .calculate();

              return Optional.of(
                  new GetAttestationRewardsResponse(
                      stateAndMetadata.isExecutionOptimistic(),
                      stateAndMetadata.isFinalized(),
                      attestationRewardsData));
            });
  }

  private UInt64 findSlotAtEndOfNextEpoch(final UInt64 epoch) {
    /*
     We need to fetch the state corresponding to the slot at the end of the next epoch because attestations can
     be included up to the end of the next epoch of the slot they are attesting to.

     Example: if user is requests rewards for epoch 10, we find the slot at the start of epoch 12 and
     subtract 1 to get the end slot of epoch 11.
    */
    return spec.computeStartSlotAtEpoch(epoch.plus(2)).minus(1);
  }

  private Optional<UInt64> findLatestAvailableEpochForRewardCalculation() {
    final int minEpochInterval = 2;
    return recentChainData
        .getCurrentEpoch()
        .flatMap(
            epoch -> {
              if (epoch.isGreaterThanOrEqualTo(minEpochInterval)) {
                return Optional.of(epoch.minus(minEpochInterval));
              } else {
                return Optional.empty();
              }
            });
  }

  public SafeFuture<Optional<ObjectAndMetaData<List<Withdrawal>>>> getExpectedWithdrawals(
      final String stateIdParam, final Optional<UInt64> optionalProposalSlot) {
    return stateSelectorFactory
        .createSelectorForStateId(stateIdParam)
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
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState data,
      final Optional<UInt64> optionalProposalSlot) {
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
          .getWithdrawalList();
    } catch (SlotProcessingException | EpochProcessingException e) {
      LOG.debug("Failed to get expected withdrawals for slot {}", proposalSlot, e);
    }
    return List.of();
  }

  public SafeFuture<Optional<Bytes32>> getFinalizedBlockRoot(final UInt64 slot) {
    return combinedChainDataClient
        .getFinalizedBlockAtSlotExact(slot)
        .thenApply(maybeFinalizedBlock -> maybeFinalizedBlock.map(SignedBeaconBlock::getRoot));
  }

  public SpecMilestone getMilestoneAtHead() {
    return spec.atSlot(recentChainData.getHeadSlot()).getMilestone();
  }

  private <T> SafeFuture<Optional<ObjectAndMetaData<T>>> fromBlock(
      final String blockIdParam, final Function<SignedBeaconBlock, T> mapper) {
    return blockSelectorFactory
        .createSelectorForBlockId(blockIdParam)
        .getBlock()
        .thenApply(maybeBlockData -> maybeBlockData.map(blockData -> blockData.map(mapper)));
  }

  private <T> SafeFuture<Optional<ObjectAndMetaData<T>>> fromState(
      final String stateIdParam,
      final Function<tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState, T>
          mapper) {
    return stateSelectorFactory
        .createSelectorForStateId(stateIdParam)
        .getState()
        .thenApply(maybeStateData -> maybeStateData.map(blockData -> blockData.map(mapper)));
  }
}
