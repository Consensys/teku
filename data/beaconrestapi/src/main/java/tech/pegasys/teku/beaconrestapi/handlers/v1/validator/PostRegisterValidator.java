/*
 * Copyright 2022 ConsenSys AG.
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

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_EXECUTION_ADDRESS;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_EXECUTION_ADDRESS;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_PUBKEY;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_EXECUTION_ADDRESS;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_PUBKEY;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class PostRegisterValidator extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/validator/register_validator";

  private final ValidatorDataProvider validatorDataProvider;

  public PostRegisterValidator(
      final DataProvider provider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    this(provider.getValidatorDataProvider(), spec, schemaDefinitionCache);
  }

  public PostRegisterValidator(
      final ValidatorDataProvider validatorDataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("registerValidator")
            .summary(
                "Provide beacon node with registrations for the given validators to the external builder network.")
            .description(
                "Prepares the beacon node for engaging with external builders."
                    + " The information will be sent by the beacon node to the builder network."
                    + " It is expected that the validator client will send this information periodically"
                    + " to ensure the beacon node has correct and timely registration information"
                    + " to provide to builders. The validator client should not sign blinded beacon"
                    + " blocks that do not adhere to their latest fee recipient and gas limit preferences.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(
                getRequestType(schemaDefinitionCache),
                spec::deserializeSignedValidatorRegistrations)
            .response(SC_OK, "Registration information has been received.")
            .build());
    this.validatorDataProvider = validatorDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary =
          "Provide beacon node with registrations for the given validators to the external builder network.",
      tags = {TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      requestBody =
          @OpenApiRequestBody(
              content = {@OpenApiContent(from = SignedValidatorRegistrationOpenApiSchema[].class)}),
      description =
          "Prepares the beacon node for potential proposers by supplying information required when proposing blocks for the given validators. The information supplied for each validator index is considered persistent until overwritten by new information for the given validator index, or until the beacon node restarts.\n\n"
              + "Note that because the information is not persistent across beacon node restarts it is recommended that either the beacon node is monitored for restarts or this information is refreshed by resending this request periodically (for example, each epoch).\n\n"
              + "Also note that requests containing currently inactive or unknown validator indices will be accepted, as they may become active at a later epoch.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description = "Preparation information has been received."),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied."),
        @OpenApiResponse(status = RES_INTERNAL_ERROR, description = "Beacon node internal error.")
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondAsync(
        validatorDataProvider
            .registerValidators(request.getRequestBody())
            .thenApply(AsyncApiResponse::respondOk));
  }

  private static DeserializableTypeDefinition<SszList<SignedValidatorRegistration>> getRequestType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return schemaDefinitionCache
        .getSchemaDefinition(SpecMilestone.BELLATRIX)
        .toVersionBellatrix()
        .orElseThrow()
        .getSignedValidatorRegistrationsSchema()
        .getJsonTypeDefinition();
  }

  @SuppressWarnings("JavaCase")
  public static class SignedValidatorRegistrationOpenApiSchema {
    @JsonProperty("message")
    ValidatorRegistration message;

    @JsonProperty("signature")
    @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
    BLSSignature signature;

    public static class ValidatorRegistration {

      @JsonProperty("fee_recipient")
      @Schema(
          type = "string",
          pattern = PATTERN_EXECUTION_ADDRESS,
          example = EXAMPLE_EXECUTION_ADDRESS,
          description = DESCRIPTION_EXECUTION_ADDRESS)
      Eth1Address fee_recipient;

      @JsonProperty("gas_limit")
      @Schema(type = "string", format = "uint64", example = EXAMPLE_UINT64)
      UInt64 gas_limit;

      @JsonProperty("timestamp")
      @Schema(type = "string", format = "uint64", example = EXAMPLE_UINT64)
      UInt64 timestamp;

      @JsonProperty("pubkey")
      @Schema(
          type = "string",
          pattern = PATTERN_PUBKEY,
          example = EXAMPLE_PUBKEY,
          description =
              "The validator's BLS public key, uniquely identifying them. "
                  + "48-bytes, hex encoded with 0x prefix, case insensitive.")
      BLSPubKey pubkey;
    }
  }
}
