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

package tech.pegasys.artemis.api;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.api.schema.BeaconChainHead;
import tech.pegasys.artemis.api.schema.BeaconHead;
import tech.pegasys.artemis.api.schema.BeaconState;
import tech.pegasys.artemis.api.schema.BeaconValidators;
import tech.pegasys.artemis.api.schema.Committee;
import tech.pegasys.artemis.api.schema.Fork;
import tech.pegasys.artemis.api.schema.SignedBeaconBlock;
import tech.pegasys.artemis.api.schema.ValidatorsRequest;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.storage.ChainDataUnavailableException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;

public class ChainDataProvider {
  private final CombinedChainDataClient combinedChainDataClient;

  private final ChainStorageClient chainStorageClient;

  public ChainDataProvider(
      final ChainStorageClient chainStorageClient,
      final CombinedChainDataClient combinedChainDataClient) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.chainStorageClient = chainStorageClient;
  }

  public UnsignedLong getGenesisTime() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    return chainStorageClient.getGenesisTime();
  }

  public BeaconHead getBeaconHead() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }

    Bytes32 headBlockRoot =
        chainStorageClient.getBestBlockRoot().orElseThrow(ChainDataUnavailableException::new);
    tech.pegasys.artemis.datastructures.blocks.BeaconBlock headBlock =
        chainStorageClient
            .getBlockByRoot(headBlockRoot)
            .orElseThrow(ChainDataUnavailableException::new);

    return new BeaconHead(headBlock.getSlot(), headBlockRoot, headBlock.getState_root());
  }

  public Fork getFork() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }

    tech.pegasys.artemis.datastructures.state.BeaconState bestBlockRootState =
        chainStorageClient.getBestBlockRootState().orElseThrow(ChainDataUnavailableException::new);
    return new Fork(bestBlockRootState.getFork());
  }

  public SafeFuture<List<Committee>> getCommitteesAtEpoch(UnsignedLong epoch) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getCommitteeAssignmentAtEpoch(epoch)
        .thenApply(result -> result.stream().map(Committee::new).collect(Collectors.toList()))
        .exceptionally(err -> List.of());
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockBySlot(UnsignedLong slot) {
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

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(Bytes32 blockParam) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getBlockByBlockRoot(blockParam)
        .thenApply(block -> block.map(SignedBeaconBlock::new));
  }

  public SafeFuture<Optional<BeaconState>> getStateByBlockRoot(Bytes32 blockRoot) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getStateByBlockRoot(blockRoot)
        .thenApply(state -> state.map(BeaconState::new))
        .exceptionally(err -> Optional.empty());
  }

  public SafeFuture<Optional<BeaconState>> getStateAtSlot(UnsignedLong slot) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getStateAtSlot(slot)
        .thenApply(state -> state.map(BeaconState::new))
        .exceptionally(err -> Optional.empty());
  }

  public SafeFuture<Optional<Bytes32>> getHashTreeRootAtSlot(UnsignedLong slot) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getStateAtSlot(slot)
        .thenApply(state -> Optional.of(state.get().hash_tree_root()))
        .exceptionally(err -> Optional.empty());
  }

  public SafeFuture<Optional<BeaconValidators>> getValidatorsByValidatorsRequest(
      final ValidatorsRequest request) {
    UnsignedLong slot =
        request.epoch == null
            ? combinedChainDataClient.getBestSlot()
            : BeaconStateUtil.compute_start_slot_at_epoch(request.epoch);

    return combinedChainDataClient
        .getStateAtSlot(slot)
        .thenApply(
            optionalBeaconState -> {
              if (optionalBeaconState.isEmpty()) {
                return Optional.empty();
              }
              return Optional.of(
                  new BeaconValidators(
                      new BeaconState(optionalBeaconState.get()), request.pubkeys));
            });
  }

  public boolean isFinalized(final SignedBeaconBlock signedBeaconBlock) {
    return combinedChainDataClient.isFinalized(signedBeaconBlock.message.slot);
  }

  public boolean isFinalized(final UnsignedLong slot) {
    return combinedChainDataClient.isFinalized(slot);
  }

  public Optional<BeaconChainHead> getHeadState() {
    return combinedChainDataClient.getHeadStateFromStore().map(BeaconChainHead::new);
  }
}
