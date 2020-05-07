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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
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

  public BeaconHead getBeaconHead() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }

    Bytes32 headBlockRoot =
        recentChainData.getBestBlockRoot().orElseThrow(ChainDataUnavailableException::new);
    tech.pegasys.teku.datastructures.blocks.BeaconBlock headBlock =
        recentChainData
            .getBlockByRoot(headBlockRoot)
            .orElseThrow(ChainDataUnavailableException::new);

    return new BeaconHead(headBlock.getSlot(), headBlockRoot, headBlock.getState_root());
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
    if (!isStoreAvailable() || combinedChainDataClient.getBestBlockRoot().isEmpty()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
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
    final Bytes32 bestRoot =
        combinedChainDataClient.getBestBlockRoot().orElseThrow(ChainDataUnavailableException::new);
    return combinedChainDataClient
        .getBlockAndStateInEffectAtSlot(startingSlot, bestRoot)
        .thenApply(
            maybeResult ->
                maybeResult.map(
                    result ->
                        combinedChainDataClient.getCommitteesFromState(result.getState(), slot)
                            .stream()
                            .map(Committee::new)
                            .collect(Collectors.toList())));
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockBySlot(final UnsignedLong slot) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getBlockBySlot(slot)
        .thenApply(block -> block.map(SignedBeaconBlock::new));
  }

  public boolean isStoreAvailable() {
    return combinedChainDataClient != null && combinedChainDataClient.isStoreAvailable();
  }

  public Optional<Bytes32> getBestBlockRoot() {
    return combinedChainDataClient.getBestBlockRoot();
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockParam) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getBlockByBlockRoot(blockParam)
        .thenApply(block -> block.map(SignedBeaconBlock::new));
  }

  public SafeFuture<Optional<BeaconState>> getStateByBlockRoot(final Bytes32 blockRoot) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getStateByBlockRoot(blockRoot)
        .thenApply(state -> state.map(BeaconState::new));
  }

  public SafeFuture<Optional<BeaconState>> getStateAtSlot(final UnsignedLong slot) {
    return SafeFuture.of(
        () -> {
          if (!isStoreAvailable()) {
            return SafeFuture.failedFuture(new ChainDataUnavailableException());
          }
          final Bytes32 bestRoot =
              combinedChainDataClient
                  .getBestBlockRoot()
                  .orElseThrow(ChainDataUnavailableException::new);
          return combinedChainDataClient
              .getBlockAndStateInEffectAtSlot(slot, bestRoot)
              .thenApply(state -> state.map(BeaconState::new));
        });
  }

  public SafeFuture<Optional<Bytes32>> getHashTreeRootAtSlot(final UnsignedLong slot) {
    return SafeFuture.of(
        () -> {
          if (!isStoreAvailable()) {
            return SafeFuture.failedFuture(new ChainDataUnavailableException());
          }
          final Bytes32 headRoot =
              combinedChainDataClient
                  .getBestBlockRoot()
                  .orElseThrow(ChainDataUnavailableException::new);
          return combinedChainDataClient
              .getBlockAtSlotExact(slot, headRoot)
              .thenApply(block -> block.map(b -> b.getMessage().getState_root()));
        });
  }

  public SafeFuture<Optional<BeaconValidators>> getValidatorsByValidatorsRequest(
      final ValidatorsRequest request) {
    if (request.pubkeys.isEmpty()) {
      // Short-circuit if we're not requesting anything
      return SafeFuture.completedFuture(Optional.of(BeaconValidators.emptySet()));
    }

    return SafeFuture.of(
        () -> {
          final Bytes32 bestBlockRoot =
              recentChainData.getBestBlockRoot().orElseThrow(ChainDataUnavailableException::new);
          UnsignedLong slot =
              request.epoch == null
                  ? combinedChainDataClient.getBestSlot()
                  : BeaconStateUtil.compute_start_slot_at_epoch(request.epoch);

          return combinedChainDataClient
              .getBlockAndStateInEffectAtSlot(slot, bestBlockRoot)
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
    return combinedChainDataClient.getHeadStateFromStore().map(BeaconChainHead::new);
  }
}
