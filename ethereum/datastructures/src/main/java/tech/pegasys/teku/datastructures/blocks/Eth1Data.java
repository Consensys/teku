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
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractImmutableContainer;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class Eth1Data extends AbstractImmutableContainer
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  private static final int SSZ_FIELD_COUNT = 3;

  @SszTypeDescriptor
  public static final ContainerViewType<Eth1Data> TYPE =
      ContainerViewType.create(
          List.of(
              BasicViewTypes.BYTES32_TYPE, BasicViewTypes.UINT64_TYPE, BasicViewTypes.BYTES32_TYPE),
          Eth1Data::new);

  @SuppressWarnings("unused")
  private final Bytes32 deposit_root = null;

  @SuppressWarnings("unused")
  private final UInt64 deposit_count = null;

  @SuppressWarnings("unused")
  private final Bytes32 block_hash = null;

  private Eth1Data(ContainerViewType<Eth1Data> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Eth1Data(Bytes32 deposit_root, UInt64 deposit_count, Bytes32 block_hash) {
    super(
        TYPE,
        new Bytes32View(deposit_root),
        new UInt64View(deposit_count),
        new Bytes32View(block_hash));
  }

  public Eth1Data() {
    super(TYPE);
  }

  public Eth1Data(Eth1Data eth1Data) {
    super(TYPE, eth1Data.getBackingNode());
  }

  public Eth1Data withBlockHash(final Bytes32 blockHash) {
    return new Eth1Data(getDeposit_root(), getDeposit_count(), blockHash);
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encode(writer -> writer.writeFixedBytes(getDeposit_root())),
        SSZ.encodeUInt64(getDeposit_count().longValue()),
        SSZ.encode(writer -> writer.writeFixedBytes(getBlock_hash())));
  }

  /** @return the deposit_root */
  public Bytes32 getDeposit_root() {
    return ((Bytes32View) get(0)).get();
  }

  public UInt64 getDeposit_count() {
    return ((UInt64View) get(1)).get();
  }

  /** @return the block_hash */
  public Bytes32 getBlock_hash() {
    return ((Bytes32View) get(2)).get();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("deposit_root", getDeposit_root())
        .add("deposit_count", getDeposit_count())
        .add("block_hash", getBlock_hash())
        .toString();
  }
}
