/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.builder.versions.bellatrix;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_HEADER_SCHEMA;

import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidBuilder;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BuilderBidSchemaBellatrix
    extends ContainerSchema3<BuilderBidBellatrix, ExecutionPayloadHeader, SszUInt256, SszPublicKey>
    implements BuilderBidSchema<BuilderBidBellatrix> {

  public BuilderBidSchemaBellatrix(
      final String containerName, final SchemaRegistry schemaRegistry) {
    super(
        containerName,
        namedSchema(
            "header",
            SszSchema.as(
                ExecutionPayloadHeader.class, schemaRegistry.get(EXECUTION_PAYLOAD_HEADER_SCHEMA))),
        namedSchema("value", SszPrimitiveSchemas.UINT256_SCHEMA),
        namedSchema("pubkey", SszPublicKeySchema.INSTANCE));
  }

  @Override
  public BuilderBidBellatrix createFromBackingNode(final TreeNode node) {
    return new BuilderBidBellatrix(this, node);
  }

  @Override
  public BuilderBid createBuilderBid(final Consumer<BuilderBidBuilder> builderConsumer) {
    final BuilderBidBuilderBellatrix builder = new BuilderBidBuilderBellatrix().schema(this);
    builderConsumer.accept(builder);
    return builder.build();
  }
}
