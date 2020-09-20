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

package tech.pegasys.teku.protoarray;

import com.google.common.base.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ProtoNode {

  // The `slot` and `stateRoot` is not necessary for `ProtoArray`, it just exists so external
  // components can
  // easily query the block slot. This is useful for upstream fork choice logic.
  private final UInt64 blockSlot;
  private final Bytes32 stateRoot;

  private final Bytes32 blockRoot;
  private final Bytes32 parentRoot;
  private final UInt64 justifiedEpoch;
  private final UInt64 finalizedEpoch;

  private UInt64 weight;
  private Optional<Integer> parentIndex;
  private Optional<Integer> bestChildIndex;
  private Optional<Integer> bestDescendantIndex;

  ProtoNode(
      final UInt64 blockSlot,
      final Bytes32 stateRoot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Optional<Integer> parentIndex,
      final UInt64 justifiedEpoch,
      final UInt64 finalizedEpoch,
      final UInt64 weight,
      final Optional<Integer> bestChildIndex,
      final Optional<Integer> bestDescendantIndex) {
    this.blockSlot = blockSlot;
    this.stateRoot = stateRoot;
    this.blockRoot = blockRoot;
    this.parentRoot = parentRoot;
    this.parentIndex = parentIndex;
    this.justifiedEpoch = justifiedEpoch;
    this.finalizedEpoch = finalizedEpoch;
    this.weight = weight;
    this.bestChildIndex = bestChildIndex;
    this.bestDescendantIndex = bestDescendantIndex;
  }

  public void adjustWeight(long delta) {
    if (delta < 0) {
      UInt64 deltaAbsoluteValue = UInt64.valueOf(Math.abs(delta));
      if (deltaAbsoluteValue.isGreaterThan(weight)) {
        throw new RuntimeException(
            "ProtoNode: Delta to be subtracted is greater than node weight for block "
                + blockRoot
                + " ("
                + blockSlot
                + "). Attempting to subtract "
                + deltaAbsoluteValue
                + " from "
                + weight);
      }
      weight = weight.minus(deltaAbsoluteValue);
    } else {
      weight = weight.plus(delta);
    }
  }

  public Bytes32 getParentRoot() {
    return parentRoot;
  }

  public UInt64 getWeight() {
    return weight;
  }

  public UInt64 getBlockSlot() {
    return blockSlot;
  }

  public Bytes32 getStateRoot() {
    return stateRoot;
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public Optional<Integer> getParentIndex() {
    return parentIndex;
  }

  public UInt64 getJustifiedEpoch() {
    return justifiedEpoch;
  }

  public UInt64 getFinalizedEpoch() {
    return finalizedEpoch;
  }

  public void setParentIndex(Optional<Integer> parentIndex) {
    this.parentIndex = parentIndex;
  }

  public Optional<Integer> getBestChildIndex() {
    return bestChildIndex;
  }

  public void setBestChildIndex(Optional<Integer> bestChildIndex) {
    this.bestChildIndex = bestChildIndex;
  }

  public Optional<Integer> getBestDescendantIndex() {
    return bestDescendantIndex;
  }

  public void setBestDescendantIndex(Optional<Integer> bestDescendantIndex) {
    this.bestDescendantIndex = bestDescendantIndex;
  }

  public BlockInformation createBlockInformation() {
    return new BlockInformation(
        blockSlot, blockRoot, parentRoot, stateRoot, justifiedEpoch, finalizedEpoch);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProtoNode)) return false;
    ProtoNode protoNode = (ProtoNode) o;
    return Objects.equal(getBlockSlot(), protoNode.getBlockSlot())
        && Objects.equal(getStateRoot(), protoNode.getStateRoot())
        && Objects.equal(getBlockRoot(), protoNode.getBlockRoot())
        && Objects.equal(getParentRoot(), protoNode.getParentRoot())
        && Objects.equal(getJustifiedEpoch(), protoNode.getJustifiedEpoch())
        && Objects.equal(getFinalizedEpoch(), protoNode.getFinalizedEpoch())
        && Objects.equal(getWeight(), protoNode.getWeight())
        && Objects.equal(getParentIndex(), protoNode.getParentIndex())
        && Objects.equal(getBestChildIndex(), protoNode.getBestChildIndex())
        && Objects.equal(getBestDescendantIndex(), protoNode.getBestDescendantIndex());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        getBlockSlot(),
        getStateRoot(),
        getBlockRoot(),
        getParentRoot(),
        getJustifiedEpoch(),
        getFinalizedEpoch(),
        getWeight(),
        getParentIndex(),
        getBestChildIndex(),
        getBestDescendantIndex());
  }
}
