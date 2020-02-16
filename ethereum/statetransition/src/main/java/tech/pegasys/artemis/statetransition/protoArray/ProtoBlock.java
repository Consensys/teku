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

package tech.pegasys.artemis.statetransition.protoArray;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;

public class ProtoBlock {

  // child with greatest weight
  private Optional<Integer> bestChildIndex;

  // descendant with greatest weight
  private Integer bestDescendantIndex;

  // effective total balance of the validators that have voted
  // (using their latest attestation) for this block or one of its descendants
  private UnsignedLong weight;

  private final Bytes32 root;
  private final Bytes32 parentRoot;

  public ProtoBlock(
      Integer bestChildIndex,
      int bestDescendantIndex,
      UnsignedLong weight,
      Bytes32 root,
      Bytes32 parentRoot) {
    this.bestChildIndex = Optional.ofNullable(bestChildIndex);
    this.bestDescendantIndex = bestDescendantIndex;
    this.weight = weight;
    this.root = root;
    this.parentRoot = parentRoot;
  }

  public static ProtoBlock createNewProtoBlock(int blockIndex, Bytes32 root, Bytes32 parentRoot) {
    return new ProtoBlock(null, blockIndex, UnsignedLong.ZERO, root, parentRoot);
  }

  public Optional<Integer> getBestChildIndex() {
    return bestChildIndex;
  }

  public void setBestChildIndex(int bestChildIndex) {
    this.bestChildIndex = Optional.of(bestChildIndex);
  }

  public Integer getBestDescendantIndex() {
    return bestDescendantIndex;
  }

  public void setBestDescendantIndex(int bestDescendantIndex) {
    this.bestDescendantIndex = bestDescendantIndex;
  }

  public UnsignedLong getWeight() {
    return weight;
  }

  public void setWeight(UnsignedLong weight) {
    this.weight = weight;
  }

  public Bytes32 getRoot() {
    return root;
  }

  public Bytes32 getParentRoot() {
    return parentRoot;
  }
}
