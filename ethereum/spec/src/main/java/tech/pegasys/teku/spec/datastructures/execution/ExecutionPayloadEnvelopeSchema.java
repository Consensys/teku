/*
 * Copyright Consensys Software Inc., 2024
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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public class ExecutionPayloadEnvelopeSchema
    extends ContainerSchema6<
        ExecutionPayloadEnvelope,
        ExecutionPayload,
        SszUInt64,
        SszBytes32,
        SszList<SszKZGCommitment>,
        SszBit,
        SszBytes32> {

  public ExecutionPayloadEnvelopeSchema(
      final ExecutionPayloadSchema<?> executionPayloadSchema,
      final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema) {
    super(
        "ExecutionPayloadEnvelope",
        namedSchema("payload", SszSchema.as(ExecutionPayload.class, executionPayloadSchema)),
        namedSchema("builder_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("blob_kzg_commitments", blobKzgCommitmentsSchema),
        namedSchema("payload_withheld", SszPrimitiveSchemas.BIT_SCHEMA),
        namedSchema("root", SszPrimitiveSchemas.BYTES32_SCHEMA));
  }

  public ExecutionPayloadEnvelope create(
      final ExecutionPayload payload,
      final UInt64 builderIndex,
      final Bytes32 beaconBlockRoot,
      final SszList<SszKZGCommitment> blobKzgCommitments,
      final boolean payloadWithheld,
      final Bytes32 stateRoot) {
    return new ExecutionPayloadEnvelope(
        this,
        payload,
        builderIndex,
        beaconBlockRoot,
        blobKzgCommitments,
        payloadWithheld,
        stateRoot);
  }

  @Override
  public ExecutionPayloadEnvelope createFromBackingNode(final TreeNode node) {
    return new ExecutionPayloadEnvelope(this, node);
  }
}
