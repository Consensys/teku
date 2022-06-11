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
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ChainHead implements MinimalBeaconBlockSummary {

  private final MinimalBeaconBlockSummary blockData;
  private final Bytes32 executionPayloadBlockHash;
  private final boolean isOptimistic;
  private final SafeFuture<StateAndBlockSummary> stateAndBlockSummaryFuture;

  private ChainHead(
      final MinimalBeaconBlockSummary blockData,
      final Bytes32 executionPayloadBlockHash,
      final boolean isOptimistic,
      final SafeFuture<StateAndBlockSummary> stateAndBlockSummaryFuture) {
    this.blockData = blockData;
    this.executionPayloadBlockHash = executionPayloadBlockHash;
    this.isOptimistic = isOptimistic;
    this.stateAndBlockSummaryFuture = stateAndBlockSummaryFuture;
  }

  public static ChainHead create(ChainHead chainHead) {
    return new ChainHead(
        chainHead.blockData,
        chainHead.executionPayloadBlockHash,
        chainHead.isOptimistic,
        chainHead.stateAndBlockSummaryFuture);
  }

  public static ChainHead create(StateAndBlockSummary blockAndState) {
    return new ChainHead(
        blockAndState.getBlockSummary(),
        blockAndState.getExecutionBlockHash().orElse(Bytes32.ZERO),
        false,
        SafeFuture.completedFuture(blockAndState));
  }

  public static ChainHead create(SignedBlockAndState blockAndState) {
    return new ChainHead(
        blockAndState.getBlockSummary(),
        blockAndState.getExecutionBlockHash().orElse(Bytes32.ZERO),
        false,
        SafeFuture.completedFuture(blockAndState));
  }

  public static ChainHead create(
      final ProtoNodeData blockData,
      final SafeFuture<StateAndBlockSummary> stateAndBlockSummaryFuture) {
    return new ChainHead(
        blockData,
        blockData.getExecutionBlockHash(),
        blockData.isOptimistic(),
        stateAndBlockSummaryFuture);
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

  public boolean isOptimistic() {
    return isOptimistic;
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
