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

package tech.pegasys.teku.datastructures.blocks;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractImmutableContainer;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.util.hashtree.Merkleizable;

public class BeaconBlockHeader extends AbstractImmutableContainer
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 4;

  public static final ContainerViewType<BeaconBlockHeader> TYPE =
      new ContainerViewType<>(
          List.of(
              BasicViewTypes.UINT64_TYPE,
              BasicViewTypes.UINT64_TYPE,
              BasicViewTypes.BYTES32_TYPE,
              BasicViewTypes.BYTES32_TYPE,
              BasicViewTypes.BYTES32_TYPE),
          BeaconBlockHeader::new);

  @SuppressWarnings("unused")
  private final UnsignedLong slot = null;

  @SuppressWarnings("unused")
  private final UnsignedLong proposer_index = null;

  @SuppressWarnings("unused")
  private final Bytes32 parent_root = null;

  @SuppressWarnings("unused")
  private final Bytes32 state_root = null;

  @SuppressWarnings("unused")
  private final Bytes32 body_root = null;

  private BeaconBlockHeader(ContainerViewType<BeaconBlockHeader> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public BeaconBlockHeader(
      UnsignedLong slot,
      UnsignedLong proposer_index,
      Bytes32 parent_root,
      Bytes32 state_root,
      Bytes32 body_root) {
    super(
        TYPE,
        new UInt64View(slot),
        new UInt64View(proposer_index),
        new Bytes32View(parent_root),
        new Bytes32View(state_root),
        new Bytes32View(body_root));
  }

  public BeaconBlockHeader(BeaconBlockHeader header) {
    super(TYPE, header.getBackingNode());
  }

  public BeaconBlockHeader() {
    super(TYPE);
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encodeUInt64(getSlot().longValue()),
        SSZ.encodeUInt64(getProposer_index().longValue()),
        SSZ.encode(writer -> writer.writeFixedBytes(getParent_root())),
        SSZ.encode(writer -> writer.writeFixedBytes(getState_root())),
        SSZ.encode(writer -> writer.writeFixedBytes(getBody_root())));
  }

  /** *************** * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSlot() {
    return ((UInt64View) get(0)).get();
  }

  public UnsignedLong getProposer_index() {
    return ((UInt64View) get(1)).get();
  }

  public Bytes32 getParent_root() {
    return ((Bytes32View) get(2)).get();
  }

  public Bytes32 getState_root() {
    return ((Bytes32View) get(3)).get();
  }

  public Bytes32 getBody_root() {
    return ((Bytes32View) get(4)).get();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", getSlot())
        .add("proposer_index", getProposer_index())
        .add("parent_root", getParent_root())
        .add("state_root", getState_root())
        .add("body_root", getBody_root())
        .toString();
  }
}
