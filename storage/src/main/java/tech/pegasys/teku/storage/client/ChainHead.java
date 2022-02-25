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

package tech.pegasys.teku.storage.client;

import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ChainHead implements MinimalBeaconBlockSummary {

  private final MinimalBeaconBlockSummary blockData;
  private final Bytes32 executionPayloadBlockHash;
  private final SafeFuture<StateAndBlockSummary> stateAndBlockSummaryFuture;

  private ChainHead(
      final MinimalBeaconBlockSummary blockData,
      final Bytes32 executionPayloadBlockHash,
      final SafeFuture<StateAndBlockSummary> stateAndBlockSummaryFuture) {
    this.blockData = blockData;
    this.executionPayloadBlockHash = executionPayloadBlockHash;
    this.stateAndBlockSummaryFuture = stateAndBlockSummaryFuture;
  }

  public static ChainHead create(ChainHead chainHead) {
    return new ChainHead(
        chainHead.blockData,
        chainHead.executionPayloadBlockHash,
        chainHead.stateAndBlockSummaryFuture);
  }

  public static ChainHead create(StateAndBlockSummary blockAndState) {
    return new ChainHead(
        blockAndState.getBlockSummary(),
        blockAndState.getExecutionBlockHash().orElse(Bytes32.ZERO),
        SafeFuture.completedFuture(blockAndState));
  }

  public static ChainHead create(SignedBlockAndState blockAndState) {
    return new ChainHead(
        blockAndState.getBlockSummary(),
        blockAndState.getExecutionBlockHash().orElse(Bytes32.ZERO),
        SafeFuture.completedFuture(blockAndState));
  }

  public static ChainHead create(
      final MinimalBeaconBlockSummary blockData,
      final Bytes32 executionPayloadBlockHash,
      final SafeFuture<StateAndBlockSummary> stateAndBlockSummaryFuture) {
    return new ChainHead(blockData, executionPayloadBlockHash, stateAndBlockSummaryFuture);
  }

  public SafeFuture<BeaconState> getState() {
    return stateAndBlockSummaryFuture.thenApply(StateAndBlockSummary::getState);
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlock() {
    return stateAndBlockSummaryFuture.thenApply(StateAndBlockSummary::getSignedBeaconBlock);
  }

  public SafeFuture<StateAndBlockSummary> asStateAndBlockSummary() {
    return stateAndBlockSummaryFuture;
  }

  @Override
  public UInt64 getSlot() {
    return blockData.getSlot();
  }

  @Override
  public Bytes32 getParentRoot() {
    return blockData.getParentRoot();
  }

  @Override
  public Bytes32 getStateRoot() {
    return blockData.getStateRoot();
  }

  @Override
  public Bytes32 getRoot() {
    return blockData.getRoot();
  }

  public Bytes32 getExecutionBlockHash() {
    return executionPayloadBlockHash;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ChainHead chainHead = (ChainHead) o;
    return Objects.equals(blockData, chainHead.blockData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(blockData);
  }
}
