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

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class Checkpoint extends Container2<Checkpoint, UInt64View, Bytes32View>
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  @SszTypeDescriptor
  public static final ContainerType2<Checkpoint, UInt64View, Bytes32View> TYPE =
      ContainerType2.create(
          BasicViewTypes.UINT64_TYPE, BasicViewTypes.BYTES32_TYPE, Checkpoint::new);

  private Checkpoint(
      ContainerType2<Checkpoint, UInt64View, Bytes32View> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Checkpoint(UInt64 epoch, Bytes32 root) {
    super(TYPE, new UInt64View(epoch), new Bytes32View(root));
  }

  public Checkpoint() {
    super(TYPE);
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(getEpoch().longValue());
          writer.writeFixedBytes(getRoot());
        });
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("epoch", getEpoch())
        .add("root", getRoot())
        .toString();
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

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }
}
