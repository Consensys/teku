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

package tech.pegasys.teku.api;

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse.getValidatorStatus;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.blockselector.BlockSelectorFactory;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.response.SszResponse;
import tech.pegasys.teku.api.response.v1.beacon.BlockHeader;
import tech.pegasys.teku.api.response.v1.beacon.EpochCommitteeResponse;
import tech.pegasys.teku.api.response.v1.beacon.FinalityCheckpointsResponse;
import tech.pegasys.teku.api.response.v1.beacon.GenesisData;
import tech.pegasys.teku.api.response.v1.beacon.StateSyncCommittees;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorBalanceResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.response.v1.debug.ChainHead;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.PublicKeyException;
import tech.pegasys.teku.api.schema.Root;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.api.stateselector.StateSelectorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
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
    this.defaultBlockSelectorFactory = new BlockSelectorFactory(combinedChainDataClient);
    this.defaultStateSelectorFactory = new StateSelectorFactory(combinedChainDataClient);
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

  public SafeFuture<Optional<BlockHeader>> getBlockHeader(final String slotParameter) {
    return defaultBlockSelectorFactory
        .defaultBlockSelector(slotParameter)
        .getSingleBlock()
        .thenApply(maybeBlock -> maybeBlock.map(block -> new BlockHeader(block, true)));
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlock(final String slotParameter) {
    return defaultBlockSelectorFactory
        .defaultBlockSelector(slotParameter)
        .getSingleBlock()
        .thenApply(maybeBlock -> maybeBlock.map(schemaObjectProvider::getSignedBeaconBlock));
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockV2(final String slotParameter) {
    return defaultBlockSelectorFactory
        .defaultBlockSelector(slotParameter)
        .getSingleBlock()
        .thenApply(maybeBlock -> maybeBlock.map(schemaObjectProvider::getSignedBeaconBlock));
  }

  public SafeFuture<Optional<SszResponse>> getBlockSsz(final String slotParameter) {
    return defaultBlockSelectorFactory
        .defaultBlockSelector(slotParameter)
        .getSingleBlock()
        .thenApply(
            maybeBlock ->
                maybeBlock.map(
                    block ->
                        new SszResponse(
                            new ByteArrayInputStream(block.sszSerialize().toArrayUnsafe()),
                            block.hashTreeRoot().toUnprefixedHexString(),
                            spec.atSlot(block.getSlot()).getMilestone())));
  }

  public SafeFuture<Optional<Root>> getBlockRoot(final String slotParameter) {
    return defaultBlockSelectorFactory
        .defaultBlockSelector(slotParameter)
        .getSingleBlock()
        .thenApply(maybeBlock -> maybeBlock.map(block -> new Root(block.getRoot())));
  }

  public SafeFuture<Optional<List<Attestation>>> getBlockAttestations(final String slotParameter) {
    return defaultBlockSelectorFactory
        .defaultBlockSelector(slotParameter)
        .getSingleBlock()
        .thenApply(
            maybeBlock ->
                maybeBlock.map(
                    block ->
                        block.getMessage().getBody().getAttestations().stream()
                            .map(Attestation::new)
                            .collect(toList())));
  }

  public boolean isStoreAvailable() {
    return combinedChainDataClient.isStoreAvailable();
  }

  public SafeFuture<Optional<FinalityCheckpointsResponse>> getStateFinalityCheckpoints(
      final String stateIdParam) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateIdParam)
        .getState()
        .thenApply(state -> state.map(FinalityCheckpointsResponse::fromState));
  }

  public SafeFuture<Optional<Root>> getStateRoot(final String stateIdParam) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateIdParam)
        .getState()
        .thenApply(maybeState -> maybeState.map(state -> new Root(state.hashTreeRoot())));
  }

  public SafeFuture<Optional<BeaconState>> getBeaconState(final String stateIdParam) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateIdParam)
        .getState()
        .thenApply(maybeState -> maybeState.map(schemaObjectProvider::getBeaconState));
  }

  public SafeFuture<Optional<SszResponse>> getBeaconStateSsz(final String stateIdParam) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateIdParam)
        .getState()
        .thenApply(
            maybeState ->
                maybeState.map(
                    state ->
                        new SszResponse(
                            new ByteArrayInputStream(state.sszSerialize().toArrayUnsafe()),
                            state.hashTreeRoot().toUnprefixedHexString(),
                            spec.atSlot(state.getSlot()).getMilestone())));
  }

  public SafeFuture<Set<SignedBeaconBlock>> getAllBlocksAtSlot(final String slot) {
    if (slot.startsWith("0x")) {
      throw new BadRequestException(
          String.format("block roots are not currently supported: %s", slot));
    } else {
      return defaultBlockSelectorFactory
          .nonCanonicalBlocksSelector(UInt64.valueOf(slot))
          .getBlock()
          .thenApply(
              blockList ->
                  blockList.stream()
                      .map(schemaObjectProvider::getSignedBeaconBlock)
                      .collect(Collectors.toSet()));
    }
  }

  public SafeFuture<Optional<SszResponse>> getBeaconStateSszByBlockRoot(
      final String blockRootParam) {
    return defaultStateSelectorFactory
        .byBlockRootStateSelector(blockRootParam)
        .getState()
        .thenApply(
            maybeState ->
                maybeState.map(
                    state ->
                        new SszResponse(
                            new ByteArrayInputStream(state.sszSerialize().toArrayUnsafe()),
                            state.hashTreeRoot().toUnprefixedHexString(),
                            spec.atSlot(state.getSlot()).getMilestone())));
  }

  public boolean isFinalized(final SignedBeaconBlock signedBeaconBlock) {
    return combinedChainDataClient.isFinalized(signedBeaconBlock.getMessage().slot);
  }

  public boolean isFinalized(final UInt64 slot) {
    return combinedChainDataClient.isFinalized(slot);
  }

  /**
   * Convert a {validator_id} from a URL to a validator index
   *
   * @param validatorParameter numeric, or validator pubkey
   * @return optional of the validator index
   * @throws IllegalArgumentException if validator cannot be parsed or is out of bounds.
   * @throws ChainDataUnavailableException if store is not able to process requests.
   */
  public Optional<Integer> validatorParameterToIndex(final String validatorParameter) {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    return recentChainData
        .getBestState()
        .map(state -> validatorParameterToIndex(state, validatorParameter))
        .orElse(Optional.empty());
  }

  private Optional<Integer> validatorParameterToIndex(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state,
      final String validatorParameter) {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }

    if (validatorParameter.toLowerCase().startsWith("0x")) {
      try {
        BLSPubKey publicKey = BLSPubKey.fromHexString(validatorParameter);
        return spec.getValidatorIndex(state, publicKey.asBLSPublicKey());
      } catch (PublicKeyException ex) {
        throw new BadRequestException(String.format("Invalid public key: %s", validatorParameter));
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

  public SafeFuture<Optional<Fork>> getStateFork(final String stateIdParam) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateIdParam)
        .getState()
        .thenApply(maybeState -> maybeState.map(state -> new Fork(state.getFork())));
  }

  public SafeFuture<Optional<List<ValidatorBalanceResponse>>> getStateValidatorBalances(
      final String stateIdParam, final List<String> validators) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateIdParam)
        .getState()
        .thenApply(
            maybeState ->
                maybeState.map(state -> getValidatorBalancesFromState(state, validators)));
  }

  @VisibleForTesting
  List<ValidatorBalanceResponse> getValidatorBalancesFromState(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state,
      final List<String> validators) {
    return getValidatorSelector(state, validators)
        .mapToObj(index -> ValidatorBalanceResponse.fromState(state, index))
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
  }

  public Optional<Bytes32> getStateRootFromBlockRoot(final Bytes32 blockRoot) {
    return combinedChainDataClient
        .getStateByBlockRoot(blockRoot)
        .join()
        .map(Merkleizable::hashTreeRoot);
  }

  public SafeFuture<List<BlockHeader>> getBlockHeaders(
      final Optional<Bytes32> parentRoot, final Optional<UInt64> slot) {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    if (parentRoot.isPresent()) {
      return SafeFuture.completedFuture(List.of());
    }

    return defaultBlockSelectorFactory
        .forSlot(slot.orElse(combinedChainDataClient.getHeadSlot()))
        .getBlock()
        .thenApply(
            blockList ->
                blockList.stream().map(block -> new BlockHeader(block, true)).collect(toList()));
  }

  public SafeFuture<Optional<List<ValidatorResponse>>> getStateValidators(
      final String stateIdParam,
      final List<String> validators,
      final Set<ValidatorStatus> statusFilter) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateIdParam)
        .getState()
        .thenApply(
            maybeState ->
                maybeState.map(state -> getFilteredValidatorList(state, validators, statusFilter)));
  }

  @VisibleForTesting
  List<ValidatorResponse> getFilteredValidatorList(
      final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state,
      final List<String> validators,
      final Set<ValidatorStatus> statusFilter) {
    final UInt64 epoch = spec.getCurrentEpoch(state);
    return getValidatorSelector(state, validators)
        .filter(getStatusPredicate(state, statusFilter))
        .mapToObj(index -> ValidatorResponse.fromState(state, index, epoch, FAR_FUTURE_EPOCH))
        .flatMap(Optional::stream)
        .collect(toList());
  }

  public SafeFuture<Optional<ValidatorResponse>> getStateValidator(
      final String stateIdParam, final String validatorIdParam) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateIdParam)
        .getState()
        .thenApply(maybeState -> getValidatorFromState(maybeState, validatorIdParam));
  }

  private Optional<ValidatorResponse> getValidatorFromState(
      final Optional<tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState>
          maybeState,
      final String validatorIdParam) {
    if (maybeState.isEmpty()) {
      return Optional.empty();
    }
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state =
        maybeState.get();
    final UInt64 epoch = spec.getCurrentEpoch(state);
    return getValidatorSelector(state, List.of(validatorIdParam))
        .mapToObj(index -> ValidatorResponse.fromState(state, index, epoch, FAR_FUTURE_EPOCH))
        .flatMap(Optional::stream)
        .findFirst();
  }

  public SafeFuture<Optional<List<EpochCommitteeResponse>>> getStateCommittees(
      final String stateIdParameter,
      final Optional<UInt64> epoch,
      final Optional<UInt64> committeeIndex,
      final Optional<UInt64> slot) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateIdParameter)
        .getState()
        .thenApply(
            maybeState ->
                maybeState.map(
                    state -> getCommitteesFromState(state, epoch, committeeIndex, slot)));
  }

  public Optional<UInt64> getCurrentEpoch() {
    return recentChainData.getCurrentEpoch();
  }

  List<EpochCommitteeResponse> getCommitteesFromState(
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
        .map(EpochCommitteeResponse::new)
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

  public SafeFuture<Optional<List<ChainHead>>> getChainHeads() {
    List<ChainHead> result = new ArrayList<>();
    recentChainData.getChainHeads().forEach((k, v) -> result.add(new ChainHead(v, k)));

    if (result.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    return SafeFuture.completedFuture(Optional.of(result));
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

  public SafeFuture<Optional<StateSyncCommittees>> getStateSyncCommittees(
      final String stateIdParam, final Optional<UInt64> epoch) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateIdParam)
        .getState()
        .thenApply(maybeState -> maybeState.map(state -> getSyncCommitteesFromState(state, epoch)));
  }

  private StateSyncCommittees getSyncCommitteesFromState(
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
      return new StateSyncCommittees(List.of(), List.of());
    }

    final SyncCommittee committee = maybeCommittee.get();
    final List<UInt64> committeeIndices =
        committee.getPubkeys().stream()
            .flatMap(pubkey -> spec.getValidatorIndex(state, pubkey.getBLSPublicKey()).stream())
            .map(UInt64::valueOf)
            .collect(toList());

    return new StateSyncCommittees(
        committeeIndices,
        Lists.partition(
            committeeIndices, spec.atEpoch(epoch).getConfig().getTargetCommitteeSize()));
  }

  public SpecMilestone getMilestoneAtSlot(final UInt64 slot) {
    return spec.atSlot(slot).getMilestone();
  }

  public Version getVersionAtSlot(final UInt64 slot) {
    return Version.fromMilestone(spec.atSlot(slot).getMilestone());
  }
}
