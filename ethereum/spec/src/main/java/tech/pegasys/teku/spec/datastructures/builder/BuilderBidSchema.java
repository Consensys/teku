/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.builder;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

@SuppressWarnings("unchecked")
public class BuilderBidSchema
    extends ContainerSchema3<BuilderBid, ExecutionPayloadHeader, SszUInt256, SszPublicKey> {

  @SuppressWarnings("rawtypes")
  public BuilderBidSchema(final ExecutionPayloadHeaderSchema executionPayloadHeaderSchema) {
    super(
        "BuilderBid",
        namedSchema("header", executionPayloadHeaderSchema),
        namedSchema("value", SszPrimitiveSchemas.UINT256_SCHEMA),
        namedSchema("pubkey", SszPublicKeySchema.INSTANCE));
  }

  public BuilderBid create(
      final ExecutionPayloadHeader executionPayloadHeader,
      final UInt256 value,
      final BLSPublicKey publicKey) {
    return new BuilderBid(
        this, executionPayloadHeader, SszUInt256.of(value), new SszPublicKey(publicKey));
  }

  @Override
  public BuilderBid createFromBackingNode(TreeNode node) {
    return new BuilderBid(this, node);
  }
}
