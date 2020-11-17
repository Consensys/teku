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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.getValidatorIndex;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import com.google.common.annotations.VisibleForTesting;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.blockselector.BlockSelectorFactory;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.response.GetForkResponse;
import tech.pegasys.teku.api.response.StateSszResponse;
import tech.pegasys.teku.api.response.v1.beacon.BlockHeader;
import tech.pegasys.teku.api.response.v1.beacon.EpochCommitteeResponse;
import tech.pegasys.teku.api.response.v1.beacon.FinalityCheckpointsResponse;
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
import tech.pegasys.teku.api.stateselector.StateSelectorFactory;
import tech.pegasys.teku.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ChainDataProvider {
  private final BlockSelectorFactory defaultBlockSelectorFactory;
  private final StateSelectorFactory defaultStateSelectorFactory;
  private final CombinedChainDataClient combinedChainDataClient;

  private final RecentChainData recentChainData;

  public ChainDataProvider(
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.recentChainData = recentChainData;
    this.defaultBlockSelectorFactory = new BlockSelectorFactory(combinedChainDataClient);
    this.defaultStateSelectorFactory = new StateSelectorFactory(combinedChainDataClient);
  }

  public UInt64 getGenesisTime() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    return recentChainData.getGenesisTime();
  }

  public GetForkResponse getForkInfo() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }

    tech.pegasys.teku.datastructures.state.BeaconState bestBlockRootState =
        recentChainData.getBestState().orElseThrow(ChainDataUnavailableException::new);

    final ForkInfo forkInfo = bestBlockRootState.getForkInfo();
    return new GetForkResponse(forkInfo);
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
        .thenApply(maybeBlock -> maybeBlock.map(SignedBeaconBlock::new));
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
        .thenApply(state -> state.map(BeaconState::new));
  }

  public SafeFuture<Optional<StateSszResponse>> getBeaconStateSsz(final String stateIdParam) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateIdParam)
        .getState()
        .thenApply(
            maybeState ->
                maybeState.map(
                    state ->
                        new StateSszResponse(
                            new ByteArrayInputStream(
                                SimpleOffsetSerializer.serialize(state).toArrayUnsafe()),
                            state.hash_tree_root().toUnprefixedHexString())));
  }

  public SafeFuture<Optional<StateSszResponse>> getBeaconStateSszByBlockRoot(
      final String blockRootParam) {
    return defaultStateSelectorFactory
        .byBlockRootStateSelector(blockRootParam)
        .getState()
        .thenApply(
            maybeState ->
                maybeState.map(
                    state ->
                        new StateSszResponse(
                            new ByteArrayInputStream(
                                SimpleOffsetSerializer.serialize(state).toArrayUnsafe()),
                            state.hash_tree_root().toUnprefixedHexString())));
  }

  public boolean isFinalized(final SignedBeaconBlock signedBeaconBlock) {
    return combinedChainDataClient.isFinalized(signedBeaconBlock.message.slot);
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
      final tech.pegasys.teku.datastructures.state.BeaconState state,
      final String validatorParameter) {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }

    if (validatorParameter.toLowerCase().startsWith("0x")) {
      try {
        BLSPubKey publicKey = BLSPubKey.fromHexString(validatorParameter);
        return getValidatorIndex(state, publicKey.asBLSPublicKey());
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
      final tech.pegasys.teku.datastructures.state.BeaconState state,
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
        .map(Merkleizable::hash_tree_root);
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
      final tech.pegasys.teku.datastructures.state.BeaconState state,
      final List<String> validators,
      final Set<ValidatorStatus> statusFilter) {
    return getValidatorSelector(state, validators)
        .filter(getStatusPredicate(state, statusFilter))
        .mapToObj(index -> ValidatorResponse.fromState(state, index))
        .flatMap(Optional::stream)
        .collect(toList());
  }

  public SafeFuture<Optional<ValidatorResponse>> getStateValidator(
      final String stateIdParam, final String validatorIdParam) {
    return defaultStateSelectorFactory
        .defaultStateSelector(stateIdParam)
        .getState()
        .thenApply(
            maybeState -> maybeState.map(state -> getValidatorFromState(state, validatorIdParam)));
  }

  private ValidatorResponse getValidatorFromState(
      final tech.pegasys.teku.datastructures.state.BeaconState state,
      final String validatorIdParam) {
    return getValidatorSelector(state, List.of(validatorIdParam))
        .mapToObj(index -> ValidatorResponse.fromState(state, index))
        .flatMap(Optional::stream)
        .findFirst()
        .orElseThrow(() -> new BadRequestException("Validator not found: " + validatorIdParam));
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

  List<EpochCommitteeResponse> getCommitteesFromState(
      final tech.pegasys.teku.datastructures.state.BeaconState state,
      final Optional<UInt64> epoch,
      final Optional<UInt64> committeeIndex,
      final Optional<UInt64> slot) {
    final Predicate<CommitteeAssignment> slotFilter =
        slot.isEmpty() ? __ -> true : (assignment) -> assignment.getSlot().equals(slot.get());

    final Predicate<CommitteeAssignment> committeeFilter =
        committeeIndex.isEmpty()
            ? __ -> true
            : (assignment) -> assignment.getCommitteeIndex().compareTo(committeeIndex.get()) == 0;

    final UInt64 stateEpoch = compute_epoch_at_slot(state.getSlot());
    if (epoch.isPresent() && epoch.get().isGreaterThan(stateEpoch.plus(ONE))) {
      throw new BadRequestException(
          "Epoch " + epoch.get() + " is too far ahead of state epoch " + stateEpoch);
    }
    if (slot.isPresent() && !compute_epoch_at_slot(slot.get()).equals(epoch.orElse(stateEpoch))) {
      throw new BadRequestException(
          "Slot " + slot.get() + " is not in epoch " + epoch.orElse(stateEpoch));
    }
    return combinedChainDataClient.getCommitteesFromState(state, epoch.orElse(stateEpoch)).stream()
        .filter(slotFilter)
        .filter(committeeFilter)
        .map(EpochCommitteeResponse::new)
        .collect(toList());
  }

  private IntPredicate getStatusPredicate(
      final tech.pegasys.teku.datastructures.state.BeaconState state,
      final Set<ValidatorStatus> statusFilter) {
    return statusFilter.isEmpty()
        ? i -> true
        : i -> statusFilter.contains(getValidatorStatus(state, i));
  }

  private IntStream getValidatorSelector(
      final tech.pegasys.teku.datastructures.state.BeaconState state,
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

  public List<Fork> getForkSchedule() {
    final Optional<ForkInfo> maybeForkInfo = recentChainData.getForkInfoAtCurrentTime();

    return maybeForkInfo
        .map(forkInfo -> List.of(new Fork(forkInfo.getFork())))
        .orElse(Collections.emptyList());
  }
}
