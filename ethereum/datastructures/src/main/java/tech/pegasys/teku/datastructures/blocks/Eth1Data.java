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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class Eth1Data extends Container3<Eth1Data, Bytes32View, UInt64View, Bytes32View>
    implements SimpleOffsetSerializable, SSZContainer {

  public static class Eth1DataType
      extends ContainerType3<Eth1Data, Bytes32View, UInt64View, Bytes32View> {

    public Eth1DataType() {
      super(BasicViewTypes.BYTES32_TYPE, BasicViewTypes.UINT64_TYPE, BasicViewTypes.BYTES32_TYPE);
    }

    @Override
    public Eth1Data createFromBackingNode(TreeNode node) {
      return new Eth1Data(this, node);
    }
  }

  @SszTypeDescriptor public static final Eth1DataType TYPE = new Eth1DataType();

  private Eth1Data(Eth1DataType type, TreeNode backingNode) {
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

  public Eth1Data withBlockHash(final Bytes32 blockHash) {
    return new Eth1Data(getDeposit_root(), getDeposit_count(), blockHash);
  }

  /** @return the deposit_root */
  public Bytes32 getDeposit_root() {
    return getField0().get();
  }

  public UInt64 getDeposit_count() {
    return getField1().get();
  }

  /** @return the block_hash */
  public Bytes32 getBlock_hash() {
    return getField2().get();
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
