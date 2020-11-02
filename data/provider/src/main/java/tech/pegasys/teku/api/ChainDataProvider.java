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

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.api.DataProviderFailures.chainUnavailable;
import static tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse.getValidatorStatus;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.getValidatorIndex;

import com.google.common.annotations.VisibleForTesting;
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
import tech.pegasys.teku.api.response.GetBlockResponse;
import tech.pegasys.teku.api.response.GetForkResponse;
import tech.pegasys.teku.api.response.v1.beacon.BlockHeader;
import tech.pegasys.teku.api.response.v1.beacon.EpochCommitteeResponse;
import tech.pegasys.teku.api.response.v1.beacon.FinalityCheckpointsResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorBalanceResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BeaconChainHead;
import tech.pegasys.teku.api.schema.BeaconHead;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.BeaconValidators;
import tech.pegasys.teku.api.schema.Committee;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.PublicKeyException;
import tech.pegasys.teku.api.schema.Root;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.ValidatorsRequest;
import tech.pegasys.teku.api.stateselector.StateSelectorFactory;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.datastructures.util.Merkleizable;
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

  public Optional<BeaconHead> getBeaconHead() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }

    return recentChainData.getHeadBlockAndState().map(BeaconHead::new);
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

  public SafeFuture<Optional<List<Committee>>> getCommitteesAtEpoch(final UInt64 epoch) {
    if (!combinedChainDataClient.isChainDataFullyAvailable()) {
      return chainUnavailable();
    }

    final UInt64 earliestQueryableSlot =
        CommitteeUtil.getEarliestQueryableSlotForTargetEpoch(epoch);
    if (recentChainData.getHeadSlot().isLessThan(earliestQueryableSlot)) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    final UInt64 queryEpoch = compute_epoch_at_slot(earliestQueryableSlot);
    return combinedChainDataClient
        .getCheckpointStateAtEpoch(queryEpoch)
        .thenApply(
            maybeResult ->
                maybeResult.map(
                    checkpointState ->
                        combinedChainDataClient
                            .getCommitteesFromState(checkpointState.getState(), epoch).stream()
                            .map(Committee::new)
                            .collect(toList())));
  }

  public SafeFuture<Optional<List<EpochCommitteeResponse>>> getCommitteesAtEpochBySlotV1(
      final UInt64 stateSlot,
      final UInt64 epoch,
      final Optional<UInt64> maybeSlot,
      final Optional<Integer> maybeIndex) {
    final UInt64 earliestQueryableSlot =
        CommitteeUtil.getEarliestQueryableSlotForTargetEpoch(epoch);

    if (stateSlot.isLessThan(earliestQueryableSlot)) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    final Predicate<CommitteeAssignment> filterForSlot =
        (committee) -> maybeSlot.map(slot -> committee.getSlot().equals(slot)).orElse(true);

    final Predicate<CommitteeAssignment> filterForCommitteeIndex =
        (committee) ->
            maybeIndex.map(index -> committee.getCommitteeIndex().intValue() == index).orElse(true);

    return combinedChainDataClient
        .getStateAtSlotExact(stateSlot)
        .thenApply(
            maybeResult ->
                maybeResult.map(
                    state ->
                        combinedChainDataClient.getCommitteesFromState(state, epoch).stream()
                            .filter(filterForSlot)
                            .filter(filterForCommitteeIndex)
                            .map(EpochCommitteeResponse::new)
                            .collect(toList())));
  }

  public SafeFuture<Optional<List<EpochCommitteeResponse>>> getCommitteesAtEpochByStateRoot(
      final Bytes32 stateRoot,
      final UInt64 epoch,
      final Optional<UInt64> maybeSlot,
      final Optional<Integer> maybeIndex) {
    final UInt64 earliestQueryableSlot =
        CommitteeUtil.getEarliestQueryableSlotForTargetEpoch(epoch);

    final Predicate<CommitteeAssignment> filterForSlot =
        (committee) -> maybeSlot.map(slot -> committee.getSlot().equals(slot)).orElse(true);

    final Predicate<CommitteeAssignment> filterForCommitteeIndex =
        (committee) ->
            maybeIndex.map(index -> committee.getCommitteeIndex().intValue() == index).orElse(true);

    return combinedChainDataClient
        .getStateByStateRoot(stateRoot)
        .thenApply(
            maybeResult ->
                maybeResult
                    .flatMap(
                        state ->
                            state.getSlot().isLessThan(earliestQueryableSlot)
                                ? Optional.empty()
                                : Optional.of(state))
                    .map(
                        state ->
                            combinedChainDataClient.getCommitteesFromState(state, epoch).stream()
                                .filter(filterForSlot)
                                .filter(filterForCommitteeIndex)
                                .map(EpochCommitteeResponse::new)
                                .collect(toList())));
  }

  public SafeFuture<Optional<GetBlockResponse>> getBlockBySlot(final UInt64 slot) {
    if (!isStoreAvailable()) {
      return chainUnavailable();
    }
    return combinedChainDataClient
        .getBlockInEffectAtSlot(slot)
        .thenApply(block -> block.map(GetBlockResponse::new));
  }

  public SafeFuture<Optional<BlockHeader>> getBlockHeader(final String slotParameter) {
    if (!isStoreAvailable()) {
      return chainUnavailable();
    }

    return defaultBlockSelectorFactory
        .defaultBlockSelector(slotParameter)
        .getSingleBlock()
        .thenApply(maybeBlock -> maybeBlock.map(block -> new BlockHeader(block, true)));
  }

  public boolean isStoreAvailable() {
    return combinedChainDataClient.isStoreAvailable();
  }

  public Optional<Bytes32> getBestBlockRoot() {
    return combinedChainDataClient.getBestBlockRoot();
  }

  public UInt64 getCurrentEpoch() {
    return combinedChainDataClient.getCurrentEpoch();
  }

  public SafeFuture<Optional<GetBlockResponse>> getBlockByBlockRoot(final Bytes32 blockParam) {
    if (!isStoreAvailable()) {
      return chainUnavailable();
    }
    return combinedChainDataClient
        .getBlockByBlockRoot(blockParam)
        .thenApply(block -> block.map(GetBlockResponse::new));
  }

  public SafeFuture<Optional<BeaconState>> getStateByBlockRoot(final Bytes32 blockRoot) {
    if (!isStoreAvailable()) {
      return chainUnavailable();
    }
    return combinedChainDataClient
        .getStateByBlockRoot(blockRoot)
        .thenApply(state -> state.map(BeaconState::new));
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

  public SafeFuture<Optional<BeaconState>> getStateByStateRoot(final Bytes32 stateRoot) {
    if (!isStoreAvailable()) {
      return chainUnavailable();
    }
    return combinedChainDataClient
        .getStateByStateRoot(stateRoot)
        .thenApply(state -> state.map(BeaconState::new));
  }

  public SafeFuture<Optional<BeaconState>> getStateAtSlot(final UInt64 slot) {
    if (!combinedChainDataClient.isChainDataFullyAvailable()) {
      return chainUnavailable();
    }

    return combinedChainDataClient
        .getStateAtSlotExact(slot)
        .thenApply(stateInternal -> stateInternal.map(BeaconState::new));
  }

  // TODO: remove once old API's are removed
  public SafeFuture<Optional<Bytes32>> getStateRootAtSlot(final UInt64 slot) {
    if (!combinedChainDataClient.isChainDataFullyAvailable()) {
      return chainUnavailable();
    }

    return SafeFuture.of(
        () ->
            combinedChainDataClient
                .getStateAtSlotExact(slot)
                .thenApplyChecked(
                    maybeState ->
                        maybeState.map(
                            tech.pegasys.teku.datastructures.state.BeaconState::hash_tree_root)));
  }

  public SafeFuture<Optional<BeaconValidators>> getValidatorsByValidatorsRequest(
      final ValidatorsRequest request) {
    if (request.pubkeys.isEmpty()) {
      // Short-circuit if we're not requesting anything
      return SafeFuture.completedFuture(Optional.of(BeaconValidators.emptySet()));
    }
    if (!combinedChainDataClient.isChainDataFullyAvailable()) {
      return chainUnavailable();
    }

    return SafeFuture.of(
        () -> {
          UInt64 slot =
              request.epoch == null
                  ? combinedChainDataClient.getHeadSlot()
                  : BeaconStateUtil.compute_start_slot_at_epoch(request.epoch);

          return combinedChainDataClient
              .getBlockAndStateInEffectAtSlot(slot)
              .thenApply(
                  optionalState ->
                      optionalState.map(
                          state -> new BeaconValidators(state.getState(), request.pubkeys)));
        });
  }

  public boolean isFinalized(final SignedBeaconBlock signedBeaconBlock) {
    return combinedChainDataClient.isFinalized(signedBeaconBlock.message.slot);
  }

  public boolean isFinalized(final UInt64 slot) {
    return combinedChainDataClient.isFinalized(slot);
  }

  public boolean isFinalizedEpoch(final UInt64 epoch) {
    return combinedChainDataClient.isFinalizedEpoch(epoch);
  }

  public Optional<BeaconChainHead> getHeadState() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    return recentChainData.getHeadBlockAndState().map(BeaconChainHead::new);
  }

  public Optional<UInt64> blockParameterToSlot(final String pathParam) {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    try {
      switch (pathParam) {
        case ("head"):
          return recentChainData.getCurrentSlot();
        case ("genesis"):
          return Optional.of(UInt64.ZERO);
        case ("finalized"):
          return recentChainData.getFinalizedCheckpoint().map(Checkpoint::getEpochStartSlot);
      }
      if (pathParam.toLowerCase().startsWith("0x")) {
        // block root
        Bytes32 blockRoot = Bytes32.fromHexString(pathParam);
        return combinedChainDataClient.getSlotByBlockRoot(blockRoot).join();
      } else {
        final UInt64 slot = UInt64.valueOf(pathParam);
        final UInt64 headSlot = recentChainData.getHeadSlot();
        if (slot.isGreaterThan(headSlot)) {
          throw new IllegalArgumentException(
              String.format("Invalid block: %s is beyond head slot %s", slot, headSlot));
        }
        return Optional.of(UInt64.valueOf(pathParam));
      }
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(String.format("Invalid block: %s", pathParam));
    }
  }

  public void requireStoreAvailable() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
  }

  /**
   * Convert a State Parameter {state_id} from a URL to a slot number
   *
   * @param pathParam head, genesis, finalized, justified, &lt;slot&gt;
   * @return Optional slot of the desired state
   * @throws IllegalArgumentException if state cannot be parsed or is in the future.
   */
  public Optional<UInt64> stateParameterToSlot(final String pathParam) {
    try {
      switch (pathParam) {
        case ("head"):
          return recentChainData.getCurrentSlot();
        case ("genesis"):
          return Optional.of(UInt64.ZERO);
        case ("finalized"):
          return recentChainData.getFinalizedCheckpoint().map(Checkpoint::getEpochStartSlot);
        case ("justified"):
          return recentChainData.getJustifiedCheckpoint().map(Checkpoint::getEpochStartSlot);
      }
      final UInt64 slot = UInt64.valueOf(pathParam);
      final UInt64 headSlot = recentChainData.getHeadSlot();
      if (slot.isGreaterThan(headSlot)) {
        throw new IllegalArgumentException(
            String.format("Invalid state: %s is beyond head slot %s", slot, headSlot));
      }
      return Optional.of(UInt64.valueOf(pathParam));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(String.format("Invalid state: %s", pathParam));
    }
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

  public SafeFuture<Optional<Fork>> getForkAtSlot(final UInt64 slot) {
    return combinedChainDataClient
        .getStateAtSlotExact(slot)
        .thenApply(maybeState -> maybeState.map(state -> new Fork(state.getFork())));
  }

  public SafeFuture<Optional<ValidatorResponse>> getValidatorDetailsBySlot(
      final UInt64 slot, final Optional<Integer> validatorIndex) {
    if (validatorIndex.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    } else {
      return getValidatorsDetailsBySlot(slot, List.of(validatorIndex.get()))
          .thenApply(
              maybeValidators ->
                  maybeValidators.flatMap(
                      validators -> {
                        checkState(
                            validators.size() <= 1,
                            "Received more than one validator for a single index");
                        return validators.stream().findFirst();
                      }));
    }
  }

  public SafeFuture<Optional<List<ValidatorResponse>>> getValidatorsDetailsBySlot(
      final UInt64 slot, final List<Integer> validatorIndices) {

    return combinedChainDataClient
        .getStateAtSlotExact(slot)
        .thenApply(maybeState -> maybeState.map(state -> getValidators(validatorIndices, state)));
  }

  public SafeFuture<Optional<ValidatorResponse>> getValidatorDetailsByStateRoot(
      final Bytes32 stateRoot, final Optional<Integer> validatorIndex) {
    return getValidatorsDetailsByStateRoot(stateRoot, List.of(validatorIndex.get()), List.of())
        .thenApply(
            maybeValidators ->
                maybeValidators.flatMap(
                    validators -> {
                      checkState(
                          validators.size() <= 1,
                          "Received more than one validator for a single index");
                      return validators.stream().findFirst();
                    }));
  }

  public SafeFuture<Optional<List<ValidatorResponse>>> getValidatorsDetailsByStateRoot(
      final Bytes32 stateRoot,
      final List<Integer> validatorIndices,
      final List<ValidatorStatus> statusFilter) {

    return combinedChainDataClient
        .getStateByStateRoot(stateRoot)
        .thenApply(maybeState -> maybeState.map(state -> getValidators(validatorIndices, state)));
  }

  private List<ValidatorResponse> getValidators(
      final List<Integer> validatorIndices,
      final tech.pegasys.teku.datastructures.state.BeaconState state) {
    return validatorIndices.stream()
        .flatMap(index -> ValidatorResponse.fromState(state, index).stream())
        .collect(toList());
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
}
