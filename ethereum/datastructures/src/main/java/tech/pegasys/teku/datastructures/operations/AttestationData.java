/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.operations;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.containers.Container5;
import tech.pegasys.teku.ssz.backing.containers.ContainerType5;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.UInt64View;

public class AttestationData
    extends Container5<
        AttestationData, UInt64View, UInt64View, Bytes32View, Checkpoint, Checkpoint> {

  public static class AttestationDataType
      extends ContainerType5<
          AttestationData, UInt64View, UInt64View, Bytes32View, Checkpoint, Checkpoint> {

    public AttestationDataType() {
      super(
          "AttestationData",
          namedType("slot", BasicViewTypes.UINT64_TYPE),
          namedType("index", BasicViewTypes.UINT64_TYPE),
          namedType("beacon_block_root", BasicViewTypes.BYTES32_TYPE),
          namedType("source", Checkpoint.TYPE),
          namedType("target", Checkpoint.TYPE));
    }

    @Override
    public AttestationData createFromBackingNode(TreeNode node) {
      return new AttestationData(this, node);
    }
  }

  public static final AttestationDataType TYPE = new AttestationDataType();

  private AttestationData(AttestationDataType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public AttestationData(
      UInt64 slot, UInt64 index, Bytes32 beacon_block_root, Checkpoint source, Checkpoint target) {
    super(
        TYPE,
        new UInt64View(slot),
        new UInt64View(index),
        new Bytes32View(beacon_block_root),
        source,
        target);
  }

  public AttestationData(UInt64 slot, AttestationData data) {
    this(slot, data.getIndex(), data.getBeacon_block_root(), data.getSource(), data.getTarget());
  }

  public UInt64 getEarliestSlotForForkChoice() {
    // Attestations can't be processed by fork choice until their slot is in the past and until we
    // are in the same epoch as their target.
    return getSlot().plus(UInt64.ONE).max(getTarget().getEpochStartSlot());
  }

  public UInt64 getSlot() {
    return getField0().get();
  }

  public UInt64 getIndex() {
    return getField1().get();
  }

  public Bytes32 getBeacon_block_root() {
    return getField2().get();
  }

  public Checkpoint getSource() {
    return getField3();
  }

  public Checkpoint getTarget() {
    return getField4();
  }
}
