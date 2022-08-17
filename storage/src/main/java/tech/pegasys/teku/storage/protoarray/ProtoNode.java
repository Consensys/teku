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

package tech.pegasys.teku.storage.protoarray;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class ProtoNode {

  private final UInt64 blockSlot;
  private final Bytes32 stateRoot;

  private final Bytes32 blockRoot;
  private final Bytes32 parentRoot;

  private BlockCheckpoints checkpoints;

  /**
   * The block hash from the execution payload.
   *
   * <p>{@link Bytes32#ZERO} if the block does not have an execution payload or uses the default
   * payload.
   */
  private final Bytes32 executionBlockHash;

  private UInt64 weight;
  private Optional<Integer> parentIndex;
  private Optional<Integer> bestChildIndex;
  private Optional<Integer> bestDescendantIndex;

  private ProtoNodeValidationStatus validationStatus;

  ProtoNode(
      final UInt64 blockSlot,
      final Bytes32 stateRoot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Optional<Integer> parentIndex,
      final BlockCheckpoints checkpoints,
      final Bytes32 executionBlockHash,
      final UInt64 weight,
      final Optional<Integer> bestChildIndex,
      final Optional<Integer> bestDescendantIndex,
      final ProtoNodeValidationStatus validationStatus) {
    this.blockSlot = blockSlot;
    this.stateRoot = stateRoot;
    this.blockRoot = blockRoot;
    this.parentRoot = parentRoot;
    this.parentIndex = parentIndex;
    this.checkpoints = checkpoints;
    this.executionBlockHash = executionBlockHash;
    this.weight = weight;
    this.bestChildIndex = bestChildIndex;
    this.bestDescendantIndex = bestDescendantIndex;
    this.validationStatus = validationStatus;
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

  public Checkpoint getJustifiedCheckpoint() {
    return checkpoints.getJustifiedCheckpoint();
  }

  public Checkpoint getFinalizedCheckpoint() {
    return checkpoints.getFinalizedCheckpoint();
  }

  public Checkpoint getUnrealizedJustifiedCheckpoint() {
    return checkpoints.getUnrealizedJustifiedCheckpoint();
  }

  public Checkpoint getUnrealizedFinalizedCheckpoint() {
    return checkpoints.getUnrealizedFinalizedCheckpoint();
  }

  public Bytes32 getExecutionBlockHash() {
    return executionBlockHash;
  }

  public void pullUpCheckpoints() {
    checkpoints = checkpoints.realizeNextEpoch();
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

  public boolean isFullyValidated() {
    return validationStatus == ProtoNodeValidationStatus.VALID;
  }

  public boolean isInvalid() {
    return validationStatus == ProtoNodeValidationStatus.INVALID;
  }

  public boolean isOptimistic() {
    return validationStatus == ProtoNodeValidationStatus.OPTIMISTIC;
  }

  public void setValidationStatus(final ProtoNodeValidationStatus validationStatus) {
    checkState(
        this.validationStatus == ProtoNodeValidationStatus.OPTIMISTIC
            || this.validationStatus == validationStatus,
        "Cannot change node validity from %s to %s",
        this.validationStatus,
        validationStatus);
    this.validationStatus = validationStatus;
  }

  public ProtoNodeData getBlockData() {
    return new ProtoNodeData(
        blockSlot,
        blockRoot,
        parentRoot,
        stateRoot,
        executionBlockHash,
        validationStatus == ProtoNodeValidationStatus.OPTIMISTIC,
        checkpoints);
  }

  public Map<String, String> getData() {
    return ImmutableMap.<String, String>builder()
        .put("slot", blockSlot.toString())
        .put("blockRoot", blockRoot.toString())
        .put("parentRoot", parentRoot.toString())
        .put("stateRoot", stateRoot.toString())
        .put("justifiedEpoch", getJustifiedCheckpoint().toString())
        .put("finalizedEpoch", getFinalizedCheckpoint().toString())
        .put("unrealizedJustifiedCheckpoint", getUnrealizedJustifiedCheckpoint().toString())
        .put("unrealizedFinalizedCheckpoint", getUnrealizedFinalizedCheckpoint().toString())
        .put("executionBlockHash", executionBlockHash.toString())
        .put("validationStatus", validationStatus.name())
        .put("weight", weight.toString())
        .build();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProtoNode protoNode = (ProtoNode) o;
    return Objects.equals(blockSlot, protoNode.blockSlot)
        && Objects.equals(stateRoot, protoNode.stateRoot)
        && Objects.equals(blockRoot, protoNode.blockRoot)
        && Objects.equals(parentRoot, protoNode.parentRoot)
        && Objects.equals(checkpoints, protoNode.checkpoints)
        && Objects.equals(executionBlockHash, protoNode.executionBlockHash)
        && Objects.equals(weight, protoNode.weight)
        && Objects.equals(parentIndex, protoNode.parentIndex)
        && Objects.equals(bestChildIndex, protoNode.bestChildIndex)
        && Objects.equals(bestDescendantIndex, protoNode.bestDescendantIndex)
        && validationStatus == protoNode.validationStatus;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        blockSlot,
        stateRoot,
        blockRoot,
        parentRoot,
        checkpoints,
        executionBlockHash,
        weight,
        parentIndex,
        bestChildIndex,
        bestDescendantIndex,
        validationStatus);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("blockSlot", blockSlot)
        .add("stateRoot", stateRoot)
        .add("blockRoot", blockRoot)
        .add("parentRoot", parentRoot)
        .add("justifiedCheckpoint", getJustifiedCheckpoint())
        .add("finalizedCheckpoint", getFinalizedCheckpoint())
        .add("unrealizedJustifiedCheckpoint", getUnrealizedJustifiedCheckpoint())
        .add("unrealizedFinalizedCheckpoint", getUnrealizedFinalizedCheckpoint())
        .add("executionBlockHash", executionBlockHash)
        .add("weight", weight)
        .add("parentIndex", parentIndex)
        .add("bestChildIndex", bestChildIndex)
        .add("bestDescendantIndex", bestDescendantIndex)
        .add("validationStatus", validationStatus)
        .toString();
  }

  public String toLogString() {
    return LogFormatter.formatBlock(blockSlot, blockRoot);
  }
}
