/*
 * Copyright ConsenSys Software Inc., 2023
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

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;

public class ExecutionPayloadAndBlobsBundleSchema
    extends ContainerSchema2<ExecutionPayloadAndBlobsBundle, ExecutionPayload, BlobsBundle>
    implements BuilderPayloadSchema<ExecutionPayloadAndBlobsBundle> {

  public ExecutionPayloadAndBlobsBundleSchema(
      final ExecutionPayloadSchema<? extends ExecutionPayload> executionPayloadSchema,
      final BlobsBundleSchema blobsBundleSchema) {
    super(
        "ExecutionPayloadAndBlobsBundle",
        namedSchema(
            "execution_payload", SszSchema.as(ExecutionPayload.class, executionPayloadSchema)),
        namedSchema("blobs_bundle", blobsBundleSchema));
  }

  @Override
  public ExecutionPayloadAndBlobsBundle createFromBackingNode(final TreeNode node) {
    return new ExecutionPayloadAndBlobsBundle(this, node);
  }

  public ExecutionPayloadAndBlobsBundle create(
      final ExecutionPayload executionPayload, final BlobsBundle blobsBundle) {
    return new ExecutionPayloadAndBlobsBundle(this, executionPayload, blobsBundle);
  }
}
