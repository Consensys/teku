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
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PENDING_PAYMENT_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PENDING_WITHDRAWAL_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PAYLOAD_ATTESTATION_DATA_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PAYLOAD_ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_EXECUTION_PAYLOAD_HEADER_SCHEMA;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyBuilderGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingPaymentSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingWithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationDataSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class SchemaDefinitionsGloas extends SchemaDefinitionsFulu {

  private final BuilderPendingPaymentSchema builderPendingPaymentSchema;
  private final BuilderPendingWithdrawalSchema builderPendingWithdrawalSchema;
  private final PayloadAttestationDataSchema payloadAttestationDataSchema;
  private final PayloadAttestationSchema payloadAttestationSchema;
  private final SignedExecutionPayloadHeaderSchema signedExecutionPayloadHeaderSchema;

  public SchemaDefinitionsGloas(final SchemaRegistry schemaRegistry) {
    super(schemaRegistry);
    this.builderPendingPaymentSchema = schemaRegistry.get(BUILDER_PENDING_PAYMENT_SCHEMA);
    this.builderPendingWithdrawalSchema = schemaRegistry.get(BUILDER_PENDING_WITHDRAWAL_SCHEMA);
    this.payloadAttestationDataSchema = schemaRegistry.get(PAYLOAD_ATTESTATION_DATA_SCHEMA);
    this.payloadAttestationSchema = schemaRegistry.get(PAYLOAD_ATTESTATION_SCHEMA);
    this.signedExecutionPayloadHeaderSchema =
        schemaRegistry.get(SIGNED_EXECUTION_PAYLOAD_HEADER_SCHEMA);
  }

  public static SchemaDefinitionsGloas required(final SchemaDefinitions schemaDefinitions) {
    checkArgument(
        schemaDefinitions instanceof SchemaDefinitionsGloas,
        "Expected definitions of type %s but got %s",
        SchemaDefinitionsGloas.class,
        schemaDefinitions.getClass());
    return (SchemaDefinitionsGloas) schemaDefinitions;
  }

  @Override
  public BeaconBlockBodyBuilder createBeaconBlockBodyBuilder() {
    return new BeaconBlockBodyBuilderGloas(
        getBeaconBlockBodySchema().toVersionGloas().orElseThrow(),
        getBlindedBeaconBlockBodySchema().toBlindedVersionGloas().orElseThrow());
  }

  public BuilderPendingPaymentSchema getBuilderPendingPaymentSchema() {
    return builderPendingPaymentSchema;
  }

  public BuilderPendingWithdrawalSchema getBuilderPendingWithdrawalSchema() {
    return builderPendingWithdrawalSchema;
  }

  public PayloadAttestationDataSchema getPayloadAttestationDataSchema() {
    return payloadAttestationDataSchema;
  }

  public PayloadAttestationSchema getPayloadAttestationSchema() {
    return payloadAttestationSchema;
  }

  public SignedExecutionPayloadHeaderSchema getSignedExecutionPayloadHeaderSchema() {
    return signedExecutionPayloadHeaderSchema;
  }

  @Override
  public Optional<SchemaDefinitionsGloas> toVersionGloas() {
    return Optional.of(this);
  }
}
