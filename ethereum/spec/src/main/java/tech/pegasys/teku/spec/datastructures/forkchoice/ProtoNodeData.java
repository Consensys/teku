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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;

public class ProtoNodeData implements MinimalBeaconBlockSummary {

  private final UInt64 slot;
  private final Bytes32 root;
  private final Bytes32 parentRoot;
  private final Bytes32 stateRoot;
  private final Bytes32 executionBlockHash;
  private final boolean isOptimistic;

  public ProtoNodeData(
      final UInt64 slot,
      final Bytes32 root,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final Bytes32 executionBlockHash,
      final boolean isOptimistic) {
    this.slot = slot;
    this.root = root;
    this.parentRoot = parentRoot;
    this.stateRoot = stateRoot;
    this.executionBlockHash = executionBlockHash;
    this.isOptimistic = isOptimistic;
  }

  @Override
  public UInt64 getSlot() {
    return slot;
  }

  @Override
  public Bytes32 getRoot() {
    return root;
  }

  @Override
  public Bytes32 getParentRoot() {
    return parentRoot;
  }

  @Override
  public Bytes32 getStateRoot() {
    return stateRoot;
  }

  public Bytes32 getExecutionBlockHash() {
    return executionBlockHash;
  }

  public boolean isOptimistic() {
    return isOptimistic;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProtoNodeData that = (ProtoNodeData) o;
    return isOptimistic == that.isOptimistic
        && Objects.equals(slot, that.slot)
        && Objects.equals(root, that.root)
        && Objects.equals(parentRoot, that.parentRoot)
        && Objects.equals(stateRoot, that.stateRoot)
        && Objects.equals(executionBlockHash, that.executionBlockHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, root, parentRoot, stateRoot, executionBlockHash, isOptimistic);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("root", root)
        .add("parentRoot", parentRoot)
        .add("stateRoot", stateRoot)
        .add("executionBlockHash", executionBlockHash)
        .add("isOptimistic", isOptimistic)
        .toString();
  }
}
