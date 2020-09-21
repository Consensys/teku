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

import static tech.pegasys.teku.api.DataProviderFailures.chainUnavailable;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.GetBlockResponse;
import tech.pegasys.teku.api.response.GetForkResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BeaconChainHead;
import tech.pegasys.teku.api.schema.BeaconHead;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.BeaconValidators;
import tech.pegasys.teku.api.schema.Committee;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.ValidatorsRequest;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.datastructures.util.ValidatorsUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ChainDataProvider {
  private final CombinedChainDataClient combinedChainDataClient;

  private final RecentChainData recentChainData;

  public ChainDataProvider(
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.recentChainData = recentChainData;
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
                            .collect(Collectors.toList())));
  }

  public SafeFuture<Optional<GetBlockResponse>> getBlockBySlot(final UInt64 slot) {
    if (!isStoreAvailable()) {
      return chainUnavailable();
    }
    return combinedChainDataClient
        .getBlockInEffectAtSlot(slot)
        .thenApply(block -> block.map(GetBlockResponse::new));
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

  public Optional<UInt64> stateParameterToSlot(final String pathParam) {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
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
    if (pathParam.toLowerCase().startsWith("0x")) {
      // state root
      Bytes32 stateRoot = Bytes32.fromHexString(pathParam);
      return combinedChainDataClient.getSlotByStateRoot(stateRoot).join();
    } else {
      return Optional.of(UInt64.valueOf(pathParam));
    }
  }

  public SafeFuture<Optional<Fork>> getForkAtSlot(final UInt64 slot) {
    final SafeFuture<Optional<tech.pegasys.teku.datastructures.state.BeaconState>> state =
        combinedChainDataClient.getStateAtSlotExact(slot);
    return state.thenApply(this::getForkFromState);
  }

  private Optional<Fork> getForkFromState(
      final Optional<tech.pegasys.teku.datastructures.state.BeaconState> maybeState) {
    if (maybeState.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new Fork(maybeState.get().getFork()));
  }

  public Optional<Integer> validatorParameterToIndex(final String validatorParameter) {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    final Optional<tech.pegasys.teku.datastructures.state.BeaconState> maybeState =
        recentChainData.getBestState();
    if (maybeState.isEmpty()) {
      return Optional.empty();
    }
    final tech.pegasys.teku.datastructures.state.BeaconState state = maybeState.get();

    if (validatorParameter.toLowerCase().startsWith("0x")) {
      BLSPubKey publicKey = BLSPubKey.fromHexString(validatorParameter);
      return ValidatorsUtil.getValidatorIndex(state, publicKey.asBLSPublicKey());
    } else {
      return Optional.of(Integer.parseUnsignedInt(validatorParameter));
    }
  }

  public SafeFuture<Optional<ValidatorResponse>> getValidatorDetails(
      final Optional<UInt64> maybeSlot, final Optional<Integer> maybeIndex) {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }

    if (maybeSlot.isEmpty() || maybeIndex.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    return combinedChainDataClient
        .getStateAtSlotExact(maybeSlot.get())
        .thenApply(maybeState -> getValidatorDetailsFromState(maybeState, maybeIndex.get()));
  }

  private Optional<ValidatorResponse> getValidatorDetailsFromState(
      final Optional<tech.pegasys.teku.datastructures.state.BeaconState> maybeState,
      final Integer validatorIndex) {
    if (maybeState.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(ValidatorResponse.fromState(maybeState.get(), validatorIndex));
  }
}
