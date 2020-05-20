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

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static tech.pegasys.teku.api.DataProviderFailures.chainUnavailable;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.GetBlockResponse;
import tech.pegasys.teku.api.schema.BeaconChainHead;
import tech.pegasys.teku.api.schema.BeaconHead;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.BeaconValidators;
import tech.pegasys.teku.api.schema.Committee;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.ValidatorsRequest;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.Constants;

public class ChainDataProvider {
  private final CombinedChainDataClient combinedChainDataClient;

  private final RecentChainData recentChainData;

  public ChainDataProvider(
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.recentChainData = recentChainData;
  }

  public UnsignedLong getGenesisTime() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    return recentChainData.getGenesisTime();
  }

  public Optional<BeaconHead> getBeaconHead() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }

    return recentChainData.getBestBlockAndState().map(BeaconHead::new);
  }

  public Fork getFork() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }

    tech.pegasys.teku.datastructures.state.BeaconState bestBlockRootState =
        recentChainData.getBestState().orElseThrow(ChainDataUnavailableException::new);
    return new Fork(bestBlockRootState.getFork());
  }

  public SafeFuture<Optional<List<Committee>>> getCommitteesAtEpoch(final UnsignedLong epoch) {
    if (!combinedChainDataClient.isChainDataFullyAvailable()) {
      return chainUnavailable();
    }
    final UnsignedLong committeesCalculatedAtEpoch = epoch.equals(ZERO) ? ZERO : epoch.minus(ONE);
    final UnsignedLong startingSlot = compute_start_slot_at_epoch(committeesCalculatedAtEpoch);
    final UnsignedLong slot = compute_start_slot_at_epoch(epoch);

    // one epoch in future is available, beyond that cannot be calculated
    if (slot.compareTo(
            recentChainData.getBestSlot().plus(UnsignedLong.valueOf(Constants.SLOTS_PER_EPOCH)))
        > 0) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    return combinedChainDataClient
        .getBlockAndStateInEffectAtSlot(startingSlot)
        .thenApply(
            maybeResult ->
                maybeResult.map(
                    result ->
                        combinedChainDataClient.getCommitteesFromState(result.getState(), slot)
                            .stream()
                            .map(Committee::new)
                            .collect(Collectors.toList())));
  }

  public SafeFuture<Optional<GetBlockResponse>> getBlockBySlot(final UnsignedLong slot) {
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

  public SafeFuture<Optional<BeaconState>> getStateAtSlot(final UnsignedLong slot) {
    return SafeFuture.of(
        () -> {
          if (!combinedChainDataClient.isChainDataFullyAvailable()) {
            return chainUnavailable();
          }

          return combinedChainDataClient
              .getBlockAndStateInEffectAtSlot(slot)
              .thenApply(state -> state.map(BeaconState::new));
        });
  }

  public SafeFuture<Optional<Bytes32>> getStateRootAtSlot(final UnsignedLong slot) {
    if (!combinedChainDataClient.isChainDataFullyAvailable()) {
      return chainUnavailable();
    }

    return SafeFuture.of(
        () ->
            combinedChainDataClient
                .getBlockAtSlotExact(slot)
                .thenApply(block -> block.map(b -> b.getMessage().getState_root())));
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
          UnsignedLong slot =
              request.epoch == null
                  ? combinedChainDataClient.getBestSlot()
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

  public boolean isFinalized(final UnsignedLong slot) {
    return combinedChainDataClient.isFinalized(slot);
  }

  public boolean isFinalizedEpoch(final UnsignedLong epoch) {
    return combinedChainDataClient.isFinalizedEpoch(epoch);
  }

  public Optional<BeaconChainHead> getHeadState() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    return recentChainData.getBestBlockAndState().map(BeaconChainHead::new);
  }
}
