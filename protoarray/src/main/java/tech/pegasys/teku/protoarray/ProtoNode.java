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

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;

public class ProtoNode {

  // The `slot` and `stateRoot` is not necessary for `ProtoArray`, it just exists so external
  // components can
  // easily query the block slot. This is useful for upstream fork choice logic.
  private final UnsignedLong blockSlot;
  private final Bytes32 stateRoot;

  private final Bytes32 blockRoot;
  private final Bytes32 parentRoot;
  private final UnsignedLong justifiedEpoch;
  private final UnsignedLong finalizedEpoch;

  private UnsignedLong weight;
  private Optional<Integer> parentIndex;
  private Optional<Integer> bestChildIndex;
  private Optional<Integer> bestDescendantIndex;

  ProtoNode(
      final UnsignedLong blockSlot,
      final Bytes32 stateRoot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Optional<Integer> parentIndex,
      final UnsignedLong justifiedEpoch,
      final UnsignedLong finalizedEpoch,
      final UnsignedLong weight,
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
      UnsignedLong deltaAbsoluteValue = UnsignedLong.valueOf(Math.abs(delta));
      if (deltaAbsoluteValue.compareTo(weight) > 0) {
        throw new RuntimeException(
            "ProtoNode: Delta to be subtracted is greater than node weight.");
      }
      weight = weight.minus(deltaAbsoluteValue);
    } else {
      weight = weight.plus(UnsignedLong.valueOf(delta));
    }
  }

  public Bytes32 getParentRoot() {
    return parentRoot;
  }

  public UnsignedLong getWeight() {
    return weight;
  }

  public UnsignedLong getBlockSlot() {
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

  public UnsignedLong getJustifiedEpoch() {
    return justifiedEpoch;
  }

  public UnsignedLong getFinalizedEpoch() {
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
}
