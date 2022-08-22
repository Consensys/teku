/*
 * Copyright ConsenSys Software Inc., 2022
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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_GATEWAY;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.request.v1.validator.PostRegisterValidatorRequest;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class PostRegisterValidator extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/validator/register_validator";
  private final ValidatorDataProvider validatorDataProvider;

  public PostRegisterValidator(final DataProvider provider) {
    this(provider.getValidatorDataProvider());
  }

  public PostRegisterValidator(final ValidatorDataProvider validatorDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("registerValidator")
            .summary("Register validators with builder")
            .description(
                "Prepares the beacon node for engaging with external builders."
                    + " The information will be sent by the beacon node to the builder network."
                    + " It is expected that the validator client will send this information periodically"
                    + " to ensure the beacon node has correct and timely registration information"
                    + " to provide to builders. The validator client should not sign blinded beacon"
                    + " blocks that do not adhere to their latest fee recipient and gas limit preferences.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(
                SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition(),
                SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA::sszDeserialize)
            .response(SC_OK, "Registration information has been received.")
            .withBadRequestResponse(
                Optional.of(
                    "The request could not be processed, check the response for more information."))
            .build());

    this.validatorDataProvider = validatorDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Register validators with builder",
      tags = {TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      requestBody =
          @OpenApiRequestBody(
              content = {@OpenApiContent(from = PostRegisterValidatorRequest[].class)}),
      description =
          "Prepares the beacon node for potential proposers by supplying information required when proposing blocks for the given validators. The information supplied for each validator index is considered persistent until overwritten by new information for the given validator index, or until the beacon node restarts.\n\n"
              + "Note that because the information is not persistent across beacon node restarts it is recommended that either the beacon node is monitored for restarts or this information is refreshed by resending this request periodically (for example, each epoch).\n\n"
              + "Also note that registrations for exited validators will be filtered out and not sent to the builder network. However, registrations for inactive or unknown validators will be sent, as they may become active at a later epoch.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description = "Preparation information has been received."),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied."),
        @OpenApiResponse(status = RES_INTERNAL_ERROR, description = "Beacon node internal error.")
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondAsync(
        validatorDataProvider
            .registerValidators(request.getRequestBody())
            .handle(
                (__, error) -> {
                  if (error != null) {
                    return AsyncApiResponse.respondWithError(SC_BAD_GATEWAY, error.getMessage());
                  }
                  return AsyncApiResponse.respondOk(null);
                }));
  }
}
