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

package tech.pegasys.teku.spec.datastructures.blocks;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;

public class BeaconBlockSchema
    extends ContainerSchema5<
        BeaconBlock, SszUInt64, SszUInt64, SszBytes32, SszBytes32, BeaconBlockBody> {

  public BeaconBlockSchema(final BeaconBlockBodySchema<?> blockBodySchema) {
    super(
        "BeaconBlock",
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("proposer_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("parent_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("state_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("body", SszSchema.as(BeaconBlockBody.class, blockBodySchema)));
  }

  @Override
  public BeaconBlock createFromBackingNode(TreeNode node) {
    return new BeaconBlock(this, node);
  }

  public BeaconBlock create(
      final UInt64 slot,
      final UInt64 proposerIndex,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final BeaconBlockBody body) {
    return new BeaconBlock(this, slot, proposerIndex, parentRoot, stateRoot, body);
  }
}
