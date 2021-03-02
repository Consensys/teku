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

package tech.pegasys.teku.spec.datastructures.blocks;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class StateAndBlockSummary implements BeaconBlockSummary {
  protected final BeaconState state;
  protected final BeaconBlockSummary blockSummary;

  protected StateAndBlockSummary(final BeaconBlockSummary blockSummary, final BeaconState state) {
    checkNotNull(blockSummary);
    checkNotNull(state);
    checkArgument(
        blockSummary.getStateRoot().equals(state.hashTreeRoot()),
        "Block state root must match the supplied state");
    this.blockSummary = blockSummary;
    this.state = state;
  }

  public static StateAndBlockSummary create(final BeaconState state) {
    return create(BeaconBlockHeader.fromState(state), state);
  }

  public static StateAndBlockSummary create(
      final BeaconBlockSummary blockSummary, final BeaconState state) {
    return new StateAndBlockSummary(blockSummary, state);
  }

  public static StateAndBlockSummary create(final SignedBlockAndState blockAndState) {
    return new StateAndBlockSummary(blockAndState.getBlock(), blockAndState.getState());
  }

  public BeaconState getState() {
    return state;
  }

  public BeaconBlockSummary getBlockSummary() {
    return blockSummary;
  }

  @Override
  public UInt64 getSlot() {
    return blockSummary.getSlot();
  }

  @Override
  public UInt64 getProposerIndex() {
    return blockSummary.getProposerIndex();
  }

  @Override
  public Bytes32 getParentRoot() {
    return blockSummary.getParentRoot();
  }

  @Override
  public Bytes32 getStateRoot() {
    return blockSummary.getStateRoot();
  }

  @Override
  public Bytes32 getBodyRoot() {
    return blockSummary.getBodyRoot();
  }

  @Override
  public Bytes32 getRoot() {
    return blockSummary.getRoot();
  }

  @Override
  public Optional<BeaconBlock> getBeaconBlock() {
    return blockSummary.getBeaconBlock();
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBeaconBlock() {
    return blockSummary.getSignedBeaconBlock();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final StateAndBlockSummary that = (StateAndBlockSummary) o;
    return Objects.equals(state, that.state) && Objects.equals(blockSummary, that.blockSummary);
  }

  @Override
  public int hashCode() {
    return Objects.hash(state, blockSummary);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("blockSummary", blockSummary).toString();
  }
}
