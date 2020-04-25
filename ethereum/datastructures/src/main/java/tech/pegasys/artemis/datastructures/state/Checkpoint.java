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

package tech.pegasys.artemis.datastructures.state;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.ssz.SSZTypes.SSZContainer;
import tech.pegasys.artemis.ssz.backing.tree.TreeNode;
import tech.pegasys.artemis.ssz.backing.type.BasicViewTypes;
import tech.pegasys.artemis.ssz.backing.type.ContainerViewType;
import tech.pegasys.artemis.ssz.backing.view.AbstractImmutableContainer;
import tech.pegasys.artemis.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.artemis.util.hashtree.Merkleizable;

public class Checkpoint extends AbstractImmutableContainer
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  public static final ContainerViewType<Checkpoint> TYPE =
      new ContainerViewType<>(
          List.of(BasicViewTypes.UINT64_TYPE, BasicViewTypes.BYTES32_TYPE), Checkpoint::new);

  @SuppressWarnings("unused")
  private final UnsignedLong epoch = null;

  @SuppressWarnings("unused")
  private final Bytes32 root = null;

  public Checkpoint(ContainerViewType<Checkpoint> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Checkpoint(UnsignedLong epoch, Bytes32 root) {
    super(TYPE, new UInt64View(epoch), new Bytes32View(root));
  }

  public Checkpoint() {
    super(TYPE);
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encodeUInt64(getEpoch().longValue()),
        SSZ.encode(writer -> writer.writeFixedBytes(getRoot())));
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

  /** ****************** * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getEpoch() {
    return ((UInt64View) get(0)).get();
  }

  public Bytes32 getRoot() {
    return ((Bytes32View) get(1)).get();
  }

  public UnsignedLong getEpochStartSlot() {
    return compute_start_slot_at_epoch(getEpoch());
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }
}
