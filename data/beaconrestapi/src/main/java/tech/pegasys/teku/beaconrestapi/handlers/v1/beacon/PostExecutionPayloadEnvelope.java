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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_EXECUTION_PAYLOAD_BLINDED_TYPE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BROADCAST_VALIDATION;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.headerBasedSelector;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BroadcastValidationParameter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.HeaderBasedOctetStreamRequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeContents;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;

public class PostExecutionPayloadEnvelope extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/beacon/execution_payload_envelopes";
  private final ValidatorDataProvider validatorDataProvider;

  public PostExecutionPayloadEnvelope(
      final ValidatorDataProvider validatorDataProvider, final SchemaDefinitionCache schemaCache) {
    super(createEndpointMetadata(schemaCache));
    this.validatorDataProvider = validatorDataProvider;
  }

  private static EndpointMetadata createEndpointMetadata(final SchemaDefinitionCache schemaCache) {
    final SchemaDefinitionsGloas schemaDefinitionsGloas =
        schemaCache.getSchemaDefinition(SpecMilestone.GLOAS).toVersionGloas().orElseThrow();
    final DeserializableTypeDefinition<SignedExecutionPayloadEnvelopeContents>
        signedExecutionPayloadEnvelopeContentsType =
            schemaDefinitionsGloas
                .getSignedExecutionPayloadEnvelopeContentsSchema()
                .getJsonTypeDefinition();
    final DeserializableTypeDefinition<SignedBlindedExecutionPayloadEnvelope>
        signedBlindedExecutionPayloadEnvelopeType =
            schemaDefinitionsGloas
                .getSignedBlindedExecutionPayloadEnvelopeSchema()
                .getJsonTypeDefinition();
    final SerializableOneOfTypeDefinition<SszData> requestType =
        new SerializableOneOfTypeDefinitionBuilder<SszData>()
            .title("SignedExecutionPayloadEnvelopeRequest")
            .withType(
                value -> value instanceof SignedExecutionPayloadEnvelopeContents,
                signedExecutionPayloadEnvelopeContentsType)
            .withType(
                value -> value instanceof SignedBlindedExecutionPayloadEnvelope,
                signedBlindedExecutionPayloadEnvelopeType)
            .build();

    return EndpointMetadata.post(ROUTE)
        .operationId("publishExecutionPayloadEnvelope")
        .summary("Publish signed execution payload envelope")
        .description(
            """
            Instructs the beacon node to broadcast a signed execution payload envelope to the network. \
            The request body is selected by the Eth-Execution-Payload-Blinded header: false submits \
            SignedExecutionPayloadEnvelopeContents and true submits SignedBlindedExecutionPayloadEnvelope.""")
        .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
        .queryParam(PARAMETER_BROADCAST_VALIDATION)
        .headerRequired(
            ETH_CONSENSUS_VERSION_TYPE.withDescription(
                "The active consensus version to which the execution payload envelope being submitted belongs."))
        .headerRequired(
            ETH_EXECUTION_PAYLOAD_BLINDED_TYPE.withDescription(
                "When false, the body is SignedExecutionPayloadEnvelopeContents. When true, the body is SignedBlindedExecutionPayloadEnvelope."))
        .requestBodyType(
            requestType,
            context ->
                isExecutionPayloadBlinded(context.getHeaders())
                    ? signedBlindedExecutionPayloadEnvelopeType
                    : signedExecutionPayloadEnvelopeContentsType,
            HeaderBasedOctetStreamRequestContentTypeDefinition.parseBytes(
                (bytes, headers) -> deserializeRequestBody(bytes, headers, schemaCache)))
        .response(
            SC_OK,
            "The envelope was validated successfully and has been broadcast. It has also been integrated into the beacon node's database.")
        .response(
            SC_ACCEPTED,
            "The envelope could not be integrated into the beacon node's database as it failed validation, but was successfully broadcast.")
        .response(
            SC_BAD_REQUEST,
            """
            The signed envelope object is invalid, broadcast validation failed, or a blinded \
            envelope could not be reconstructed from cache.""",
            HTTP_ERROR_RESPONSE_TYPE)
        .response(415, "Unsupported media type", HTTP_ERROR_RESPONSE_TYPE)
        .withInternalErrorResponse()
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SszData requestBody = request.getRequestBody();
    final Optional<BroadcastValidationLevel> broadcastValidationLevel;
    try {
      broadcastValidationLevel =
          request
              .getOptionalQueryParameter(PARAMETER_BROADCAST_VALIDATION)
              .map(BroadcastValidationParameter::toInternal);
    } catch (final IllegalArgumentException e) {
      request.respondError(
          SC_BAD_REQUEST, "Invalid value for broadcast_validation: " + e.getMessage());
      return;
    }

    final SafeFuture<PublishSignedExecutionPayloadResult> future =
        publishExecutionPayloadEnvelope(requestBody, broadcastValidationLevel);
    request.respondAsync(future.thenApply(this::processPublishSignedExecutionPayloadResult));
  }

  private SafeFuture<PublishSignedExecutionPayloadResult> publishExecutionPayloadEnvelope(
      final SszData requestBody,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    if (requestBody
        instanceof SignedExecutionPayloadEnvelopeContents signedExecutionPayloadEnvelopeContents) {
      return validatorDataProvider.publishSignedExecutionPayload(
          signedExecutionPayloadEnvelopeContents, broadcastValidationLevel);
    }
    if (requestBody
        instanceof SignedBlindedExecutionPayloadEnvelope signedBlindedExecutionPayloadEnvelope) {
      return validatorDataProvider.publishSignedExecutionPayload(
          signedBlindedExecutionPayloadEnvelope, broadcastValidationLevel);
    }
    throw new BadRequestException("Invalid signed execution payload envelope request body");
  }

  private static SszData deserializeRequestBody(
      final Bytes bytes,
      final Map<String, String> headers,
      final SchemaDefinitionCache schemaCache) {
    final SchemaDefinitionsGloas schemaDefinitionsGloas =
        headerBasedSelector(
            schemaCache,
            Optional.ofNullable(headers.get(HEADER_CONSENSUS_VERSION)),
            (__, schemaDefinitions) -> SchemaDefinitionsGloas.required(schemaDefinitions));
    if (isExecutionPayloadBlinded(headers)) {
      return schemaDefinitionsGloas
          .getSignedBlindedExecutionPayloadEnvelopeSchema()
          .sszDeserialize(bytes);
    }
    return schemaDefinitionsGloas
        .getSignedExecutionPayloadEnvelopeContentsSchema()
        .sszDeserialize(bytes);
  }

  private static boolean isExecutionPayloadBlinded(final Map<String, String> headers) {
    final String headerValue = headers.get(HEADER_EXECUTION_PAYLOAD_BLINDED);
    if (headerValue == null) {
      throw new BadRequestException(
          String.format(
              "Missing required header value for (%s)", HEADER_EXECUTION_PAYLOAD_BLINDED));
    }
    if (!headerValue.equalsIgnoreCase("true") && !headerValue.equalsIgnoreCase("false")) {
      throw new BadRequestException(
          String.format(
              "Invalid value for (%s) header: %s", HEADER_EXECUTION_PAYLOAD_BLINDED, headerValue));
    }
    return Boolean.parseBoolean(headerValue);
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
              return AsyncApiResponse.respondWithError(SC_BAD_REQUEST, rejectionReason);
            })
        .orElse(AsyncApiResponse.respondWithCode(SC_OK));
  }
}
