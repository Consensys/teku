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

package tech.pegasys.teku.spec.datastructures.builder.versions.electra;

import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidBuilder;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class BuilderBidSchemaElectra
    extends ContainerSchema5<
        BuilderBidElectraImpl,
        ExecutionPayloadHeader,
        SszList<SszKZGCommitment>,
        ExecutionRequests,
        SszUInt256,
        SszPublicKey>
    implements BuilderBidSchema<BuilderBidElectraImpl> {

  public BuilderBidSchemaElectra(
      final String containerName,
      final ExecutionPayloadHeaderSchema<?> executionPayloadHeaderSchema,
      final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema,
      final ExecutionRequestsSchema executionRequestsSchema) {
    super(
        containerName,
        namedSchema(
            "header", SszSchema.as(ExecutionPayloadHeader.class, executionPayloadHeaderSchema)),
        namedSchema("blob_kzg_commitments", blobKzgCommitmentsSchema),
        namedSchema("execution_requests", executionRequestsSchema),
        namedSchema("value", SszPrimitiveSchemas.UINT256_SCHEMA),
        namedSchema("pubkey", SszPublicKeySchema.INSTANCE));
  }

  @Override
  public BuilderBidElectraImpl createFromBackingNode(final TreeNode node) {
    return new BuilderBidElectraImpl(this, node);
  }

  @Override
  public BuilderBid createBuilderBid(final Consumer<BuilderBidBuilder> builderConsumer) {
    final BuilderBidBuilderElectra builder = new BuilderBidBuilderElectra().schema(this);
    builderConsumer.accept(builder);
    return builder.build();
  }
}
