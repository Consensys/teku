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

package tech.pegasys.teku.datastructures.state;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public class Checkpoint extends Container2<Checkpoint, SszUInt64, SszBytes32> {

  public static class CheckpointType extends ContainerType2<Checkpoint, SszUInt64, SszBytes32> {

    public CheckpointType() {
      super(
          "Checkpoint",
          namedSchema("epoch", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("root", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public Checkpoint createFromBackingNode(TreeNode node) {
      return new Checkpoint(this, node);
    }
  }

  public static final CheckpointType TYPE = new CheckpointType();

  private Checkpoint(CheckpointType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Checkpoint(UInt64 epoch, Bytes32 root) {
    super(TYPE, new SszUInt64(epoch), new SszBytes32(root));
  }

  public UInt64 getEpoch() {
    return getField0().get();
  }

  public Bytes32 getRoot() {
    return getField1().get();
  }

  public UInt64 getEpochStartSlot() {
    return compute_start_slot_at_epoch(getEpoch());
  }

  public SlotAndBlockRoot toSlotAndBlockRoot() {
    return new SlotAndBlockRoot(getEpochStartSlot(), getRoot());
  }
}
