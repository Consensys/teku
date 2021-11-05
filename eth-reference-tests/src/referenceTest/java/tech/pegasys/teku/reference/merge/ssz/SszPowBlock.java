/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.reference.merge.ssz;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ssz.containers.Container4;
import tech.pegasys.teku.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszUInt256;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SszPowBlock
    extends Container4<SszPowBlock, SszBytes32, SszBytes32, SszUInt256, SszUInt256> {
  public static class SszPowBlockSchema
      extends ContainerSchema4<SszPowBlock, SszBytes32, SszBytes32, SszUInt256, SszUInt256> {

    public SszPowBlockSchema() {
      super(
          "PowBlock",
          namedSchema("block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("parent_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("total_difficulty", SszPrimitiveSchemas.UINT256_SCHEMA),
          namedSchema("difficulty", SszPrimitiveSchemas.UINT256_SCHEMA));
    }

    @Override
    public SszPowBlock createFromBackingNode(TreeNode node) {
      return new SszPowBlock(this, node);
    }
  }

  public static final SszPowBlockSchema SSZ_SCHEMA = new SszPowBlockSchema();

  public SszPowBlock() {
    super(SSZ_SCHEMA);
  }

  public SszPowBlock(
      ContainerSchema4<SszPowBlock, SszBytes32, SszBytes32, SszUInt256, SszUInt256> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public SszPowBlock(
      Bytes32 block_hash, Bytes32 parent_hash, UInt256 total_difficulty, UInt256 difficulty) {

    super(
        SSZ_SCHEMA,
        SszBytes32.of(block_hash),
        SszBytes32.of(parent_hash),
        SszUInt256.of(total_difficulty),
        SszUInt256.of(difficulty));
  }

  @Override
  public SszPowBlockSchema getSchema() {
    return SSZ_SCHEMA;
  }

  public Bytes32 getBlockHash() {
    return getField0().get();
  }

  public Bytes32 getParentHash() {
    return getField1().get();
  }

  public UInt256 getTotalDifficulty() {
    return getField2().get();
  }

  public UInt256 getDifficulty() {
    return getField3().get();
  }
}
