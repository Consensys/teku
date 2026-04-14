/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_HEADER_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_REQUESTS_SCHEMA;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BlindedExecutionPayloadEnvelopeSchema
    extends ContainerSchema6<
        BlindedExecutionPayloadEnvelope,
        ExecutionPayloadHeader,
        ExecutionRequests,
        SszUInt64,
        SszBytes32,
        SszUInt64,
        SszBytes32> {

  public BlindedExecutionPayloadEnvelopeSchema(final SchemaRegistry schemaRegistry) {
    super(
        "BlindedExecutionPayloadEnvelope",
        namedSchema(
            "payload_header",
            SszSchema.as(
                ExecutionPayloadHeader.class, schemaRegistry.get(EXECUTION_PAYLOAD_HEADER_SCHEMA))),
        namedSchema("execution_requests", schemaRegistry.get(EXECUTION_REQUESTS_SCHEMA)),
        namedSchema("builder_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("state_root", SszPrimitiveSchemas.BYTES32_SCHEMA));
  }

  public BlindedExecutionPayloadEnvelope create(
      final ExecutionPayloadHeader payloadHeader,
      final ExecutionRequests executionRequests,
      final UInt64 builderIndex,
      final Bytes32 beaconBlockRoot,
      final UInt64 slot,
      final Bytes32 stateRoot) {
    return new BlindedExecutionPayloadEnvelope(
        this, payloadHeader, executionRequests, builderIndex, beaconBlockRoot, slot, stateRoot);
  }

  @Override
  public BlindedExecutionPayloadEnvelope createFromBackingNode(final TreeNode node) {
    return new BlindedExecutionPayloadEnvelope(this, node);
  }
}
