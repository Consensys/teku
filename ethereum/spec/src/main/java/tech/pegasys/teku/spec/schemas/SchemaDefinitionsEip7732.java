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

package tech.pegasys.teku.spec.schemas;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_ENVELOPE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.INDEXED_PAYLOAD_ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PAYLOAD_ATTESTATION_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PAYLOAD_ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_EXECUTION_PAYLOAD_HEADER_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732.BeaconBlockBodyBuilderEip7732;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage.ExecutionPayloadEnvelopesByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedPayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SchemaDefinitionsEip7732 extends SchemaDefinitionsElectra {

  private final PayloadAttestationSchema payloadAttestationSchema;
  private final IndexedPayloadAttestationSchema indexedPayloadAttestationSchema;
  private final SignedExecutionPayloadHeaderSchema signedExecutionPayloadHeaderSchema;
  private final ExecutionPayloadEnvelopeSchema executionPayloadEnvelopeSchema;
  private final SignedExecutionPayloadEnvelopeSchema signedExecutionPayloadEnvelopeSchema;
  private final ExecutionPayloadEnvelopesByRootRequestMessageSchema
      executionPayloadEnvelopesByRootRequestMessageSchema;
  private final PayloadAttestationMessageSchema payloadAttestationMessageSchema;

  public SchemaDefinitionsEip7732(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.payloadAttestationSchema = schemaRegistry.get(PAYLOAD_ATTESTATION_SCHEMA);
    this.indexedPayloadAttestationSchema = schemaRegistry.get(INDEXED_PAYLOAD_ATTESTATION_SCHEMA);
    this.signedExecutionPayloadHeaderSchema =
        schemaRegistry.get(SIGNED_EXECUTION_PAYLOAD_HEADER_SCHEMA);
    this.executionPayloadEnvelopeSchema = schemaRegistry.get(EXECUTION_PAYLOAD_ENVELOPE_SCHEMA);
    this.signedExecutionPayloadEnvelopeSchema =
        schemaRegistry.get(SIGNED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA);
    this.executionPayloadEnvelopesByRootRequestMessageSchema =
        schemaRegistry.get(EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT_REQUEST_MESSAGE_SCHEMA);
    this.payloadAttestationMessageSchema = schemaRegistry.get(PAYLOAD_ATTESTATION_MESSAGE_SCHEMA);
  }

  public static SchemaDefinitionsEip7732 required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsEip7732,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsEip7732.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsEip7732) schemaDefinitions;
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderEip7732(
        getBeaconBlockBodySchema().toVersionEip7732().orElseThrow(),
        getBlindedBeaconBlockBodySchema().toBlindedVersionEip7732().orElseThrow());
  }

  public PayloadAttestationSchema getPayloadAttestationSchema() {
    return payloadAttestationSchema;
  }

  public IndexedPayloadAttestationSchema getIndexedPayloadAttestationSchema() {
    return indexedPayloadAttestationSchema;
  }

  public SignedExecutionPayloadHeaderSchema getSignedExecutionPayloadHeaderSchema() {
    return signedExecutionPayloadHeaderSchema;
  }

  public ExecutionPayloadEnvelopeSchema getExecutionPayloadEnvelopeSchema() {
    return executionPayloadEnvelopeSchema;
  }

  public SignedExecutionPayloadEnvelopeSchema getSignedExecutionPayloadEnvelopeSchema() {
    return signedExecutionPayloadEnvelopeSchema;
  }

  public ExecutionPayloadEnvelopesByRootRequestMessageSchema
      getExecutionPayloadEnvelopesByRootRequestMessageSchema() {
    return executionPayloadEnvelopesByRootRequestMessageSchema;
  }

  public PayloadAttestationMessageSchema getPayloadAttestationMessageSchema() {
    return payloadAttestationMessageSchema;
  }

  @Override
  public Optional<SchemaDefinitionsEip7732> toVersionEip7732() {
    return Optional.of(this);
  }
}
