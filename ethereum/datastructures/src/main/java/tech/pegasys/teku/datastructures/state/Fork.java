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

import com.google.common.base.MoreObjects;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractImmutableContainer;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes4View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class Fork extends AbstractImmutableContainer
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 3;

  @SuppressWarnings("unused")
  private final Bytes4 previous_version = null; // This is a Version type, aliased as a Bytes4

  @SuppressWarnings("unused")
  private final Bytes4 current_version = null; // This is a Version type, aliased as a Bytes4

  @SuppressWarnings("unused")
  private final UInt64 epoch = null;

  @SszTypeDescriptor
  public static final ContainerViewType<Fork> TYPE =
      ContainerViewType.create(
          List.of(
              BasicViewTypes.BYTES4_TYPE, BasicViewTypes.BYTES4_TYPE, BasicViewTypes.UINT64_TYPE),
          Fork::new);

  private Fork(ContainerViewType<Fork> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Fork(Bytes4 previous_version, Bytes4 current_version, UInt64 epoch) {
    super(
        TYPE,
        new Bytes4View(previous_version),
        new Bytes4View(current_version),
        new UInt64View(epoch));
  }

  public Fork(Fork fork) {
    super(TYPE, fork.getBackingNode());
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encode(writer -> writer.writeFixedBytes(getPrevious_version().getWrappedBytes())),
        SSZ.encode(writer -> writer.writeFixedBytes(getCurrent_version().getWrappedBytes())),
        SSZ.encodeUInt64(getEpoch().longValue()));
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes4 getPrevious_version() {
    return ((Bytes4View) get(0)).get();
  }

  public Bytes4 getCurrent_version() {
    return ((Bytes4View) get(1)).get();
  }

  public UInt64 getEpoch() {
    return ((UInt64View) get(2)).get();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("previous_version", getPrevious_version())
        .add("current_version", getCurrent_version())
        .add("epoch", getEpoch())
        .toString();
  }
}
