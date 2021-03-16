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

package tech.pegasys.teku.spec.datastructures.blocks;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.containers.Container3;
import tech.pegasys.teku.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class Eth1Data extends Container3<Eth1Data, SszBytes32, SszUInt64, SszBytes32> {

  public static class Eth1DataSchema
      extends ContainerSchema3<Eth1Data, SszBytes32, SszUInt64, SszBytes32> {

    public Eth1DataSchema() {
      super(
          "Eth1Data",
          namedSchema("deposit_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("deposit_count", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public Eth1Data createFromBackingNode(TreeNode node) {
      return new Eth1Data(this, node);
    }
  }

  public static final Eth1DataSchema SSZ_SCHEMA = new Eth1DataSchema();

  private Eth1Data(Eth1DataSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Eth1Data(Bytes32 deposit_root, UInt64 deposit_count, Bytes32 block_hash) {
    super(
        SSZ_SCHEMA,
        SszBytes32.of(deposit_root),
        SszUInt64.of(deposit_count),
        SszBytes32.of(block_hash));
  }

  public Eth1Data() {
    super(SSZ_SCHEMA);
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
}
