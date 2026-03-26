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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BROADCAST_VALIDATION;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BroadcastValidationParameter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;

public class PostExecutionPayloadEnvelope extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/beacon/execution_payload_envelope";
  private final ValidatorDataProvider validatorDataProvider;

  public PostExecutionPayloadEnvelope(
      final ValidatorDataProvider validatorDataProvider,
      final SchemaDefinitionCache schemaCache) {
    super(createEndpointMetadata(schemaCache));
    this.validatorDataProvider = validatorDataProvider;
  }

  private static EndpointMetadata createEndpointMetadata(final SchemaDefinitionCache schemaCache) {
    return EndpointMetadata.post(ROUTE)
        .operationId("publishExecutionPayloadEnvelope")
        .summary("Publish signed execution payload envelope")
        .description(
            "Instructs the beacon node to broadcast a signed execution payload envelope to the network, to be gossiped for payload validation.")
        .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
        .queryParam(PARAMETER_BROADCAST_VALIDATION)
        .headerRequired(
            ETH_CONSENSUS_VERSION_TYPE.withDescription(
                "Version of the block being submitted, if using SSZ encoding."))
        .requestBodyType(
            schemaCache
                .getSchemaDefinition(SpecMilestone.GLOAS)
                .toVersionGloas()
                .orElseThrow()
                .getSignedExecutionPayloadEnvelopeSchema()
                .getJsonTypeDefinition(),
            schemaCache
                .getSchemaDefinition(SpecMilestone.GLOAS)
                .toVersionGloas()
                .orElseThrow()
                .getSignedExecutionPayloadEnvelopeSchema()::sszDeserialize)
        .response(
            SC_OK,
            "The envelope was validated successfully and has been broadcast. It has also been integrated into the beacon node's database.")
        .response(
            SC_ACCEPTED,
            "The envelope could not be integrated into the beacon node's database as it failed validation, but was successfully broadcast.")
        .response(
            400,
            "The SignedExecutionPayloadEnvelope object is invalid or broadcast validation failed",
            HTTP_ERROR_RESPONSE_TYPE)
        .response(415, "Unsupported media type", HTTP_ERROR_RESPONSE_TYPE)
        .withInternalErrorResponse()
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope = request.getRequestBody();
    final Optional<BroadcastValidationLevel> broadcastValidationLevel = request
        .getOptionalQueryParameter(PARAMETER_BROADCAST_VALIDATION)
        .map(BroadcastValidationParameter::toInternal);
    final SafeFuture<PublishSignedExecutionPayloadResult> future = validatorDataProvider.publishSignedExecutionPayload(
        signedExecutionPayloadEnvelope, broadcastValidationLevel);
    request.respondAsync(future.thenApply(this::processPublishSignedExecutionPayloadResult));
  }

  private AsyncApiResponse processPublishSignedExecutionPayloadResult(
      final PublishSignedExecutionPayloadResult result) {
    return result
        .getRejectionReason()
        .map(
            rejectionReason -> {
              if (result.isPublished()) {
                return AsyncApiResponse.respondWithCode(SC_ACCEPTED);
              }
              return AsyncApiResponse.respondWithError(SC_INTERNAL_SERVER_ERROR, rejectionReason);
            })
        .orElse(AsyncApiResponse.respondWithCode(SC_OK));
  }
}
