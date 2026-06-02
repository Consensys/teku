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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.EnumSet;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.EnumTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationDataSchema;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetPayloadAttestationData extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/payload_attestation_data/{slot}";

  private final ValidatorDataProvider provider;

  private static final ParameterMetadata<UInt64> SLOT_PARAM =
      SLOT_PARAMETER.withDescription(
          "The slot for which payload attestation data should be created.");

  private static final EnumTypeDefinition<SpecMilestone> GLOAS_MILESTONE_TYPE =
      new EnumTypeDefinition.EnumTypeBuilder<>(SpecMilestone.class, SpecMilestone::lowerCaseName)
          .excludedEnumerations(EnumSet.complementOf(EnumSet.of(SpecMilestone.GLOAS)))
          .example("gloas")
          .build();

  public GetPayloadAttestationData(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public GetPayloadAttestationData(
      final ValidatorDataProvider validatorDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("producePayloadAttestationData")
            .summary("Produce payload attestation data")
            .description(
                """
                Requests that the beacon node produce a `PayloadAttestationData`.

                This endpoint is used by PTC validators to obtain the data structure they need \
                to attest to regarding payload presence and blob data availability for a specific \
                slot.

                A 503 error must be returned if the beacon node is currently syncing.
                """)
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .pathParam(SLOT_PARAM)
            .response(
                SC_OK,
                "Success response",
                getResponseType(schemaDefinitionCache),
                sszResponseType(),
                ETH_CONSENSUS_HEADER_TYPE)
            .withBadRequestResponse(Optional.of("Invalid request - the slot is invalid"))
            .response(
                SC_NOT_FOUND,
                "No canonical block has been seen for the requested slot.",
                HTTP_ERROR_RESPONSE_TYPE)
            .withNotAcceptedResponse()
            .withInternalErrorResponse()
            .withChainDataResponses()
            .build());
    this.provider = validatorDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot = request.getPathParameter(SLOT_PARAM);
    final SafeFuture<Optional<PayloadAttestationData>> future =
        provider.createPayloadAttestationData(slot);

    request.respondAsync(
        future.thenApply(
            maybePayloadAttestationData ->
                maybePayloadAttestationData
                    .map(
                        payloadAttestationData ->
                            AsyncApiResponse.respondOk(
                                new ObjectAndMetaData<>(
                                    payloadAttestationData,
                                    SpecMilestone.GLOAS,
                                    false,
                                    true,
                                    false)))
                    .orElseGet(
                        () ->
                            AsyncApiResponse.respondWithError(
                                SC_NOT_FOUND,
                                "No canonical block found at slot=" + slot.toString()))));
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<PayloadAttestationData>>
      getResponseType(final SchemaDefinitionCache schemaDefinitionCache) {
    final PayloadAttestationDataSchema payloadAttestationDataSchema =
        SchemaDefinitionsGloas.required(
                schemaDefinitionCache.getSchemaDefinition(SpecMilestone.GLOAS))
            .getPayloadAttestationDataSchema();

    return SerializableTypeDefinition.<ObjectAndMetaData<PayloadAttestationData>>object()
        .name("ProducePayloadAttestationDataResponse")
        .withField("version", GLOAS_MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField(
            "data",
            payloadAttestationDataSchema.getJsonTypeDefinition(),
            ObjectAndMetaData::getData)
        .build();
  }
}
