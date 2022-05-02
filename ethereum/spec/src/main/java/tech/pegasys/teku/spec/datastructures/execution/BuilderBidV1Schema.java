/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.execution;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class BuilderBidV1Schema
    extends ContainerSchema3<BuilderBidV1, ExecutionPayloadHeader, SszUInt256, SszPublicKey> {
  public BuilderBidV1Schema(final ExecutionPayloadHeaderSchema executionPayloadHeaderSchema) {
    super(
        "BuilderBidV1",
        namedSchema("header", executionPayloadHeaderSchema),
        namedSchema("value", SszPrimitiveSchemas.UINT256_SCHEMA),
        namedSchema("pubkey", SszPublicKeySchema.INSTANCE));
  }

  public BuilderBidV1 create(
      final ExecutionPayloadHeader executionPayloadHeader,
      final UInt256 value,
      final BLSPublicKey publicKey) {
    return new BuilderBidV1(
        this, executionPayloadHeader, SszUInt256.of(value), new SszPublicKey(publicKey));
  }

  @Override
  public BuilderBidV1 createFromBackingNode(TreeNode node) {
    return new BuilderBidV1(this, node);
  }
}
