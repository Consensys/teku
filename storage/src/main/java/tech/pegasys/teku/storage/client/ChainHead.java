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
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ChainHead implements BeaconBlockSummary {

  private final BeaconBlockSummary block;
  private final SafeFuture<BeaconState> state;

  private ChainHead(final BeaconBlockSummary block, final SafeFuture<BeaconState> state) {
    this.block = block;
    this.state = state;
  }

  public static ChainHead create(ChainHead chainHead) {
    return new ChainHead(chainHead.block, chainHead.getState());
  }

  public static ChainHead create(StateAndBlockSummary blockAndState) {
    return new ChainHead(
        blockAndState.getBlockSummary(), SafeFuture.completedFuture(blockAndState.getState()));
  }

  public static ChainHead create(SignedBlockAndState blockAndState) {
    return new ChainHead(
        blockAndState.getBlockSummary(), SafeFuture.completedFuture(blockAndState.getState()));
  }

  public SafeFuture<BeaconState> getState() {
    return state;
  }

  public SafeFuture<StateAndBlockSummary> asStateAndBlockSummary() {
    return state.thenApply(state -> StateAndBlockSummary.create(block, state));
  }

  @Override
  public UInt64 getSlot() {
    return block.getSlot();
  }

  @Override
  public UInt64 getProposerIndex() {
    return block.getProposerIndex();
  }

  @Override
  public Bytes32 getParentRoot() {
    return block.getParentRoot();
  }

  @Override
  public Bytes32 getStateRoot() {
    return block.getStateRoot();
  }

  @Override
  public Bytes32 getBodyRoot() {
    return block.getBodyRoot();
  }

  @Override
  public Bytes32 getRoot() {
    return block.getRoot();
  }

  @Override
  public Optional<BeaconBlock> getBeaconBlock() {
    return block.getBeaconBlock();
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBeaconBlock() {
    return block.getSignedBeaconBlock();
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
    return Objects.equals(block, chainHead.block);
  }

  @Override
  public int hashCode() {
    return Objects.hash(block);
  }
}
