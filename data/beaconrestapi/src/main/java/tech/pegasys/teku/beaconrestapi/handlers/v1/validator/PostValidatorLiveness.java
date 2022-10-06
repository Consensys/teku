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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.api.migrated.ValidatorLivenessRequest;
import tech.pegasys.teku.api.response.v1.validator.PostValidatorLivenessResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class PostValidatorLiveness extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/validator/liveness";

  private static final SerializableTypeDefinition<List<ValidatorLivenessAtEpoch>> RESPONSE_TYPE =
      SerializableTypeDefinition.<List<ValidatorLivenessAtEpoch>>object()
          .name("PostValidatorLivenessResponse")
          .withField(
              "data", listOf(ValidatorLivenessAtEpoch.getJsonTypeDefinition()), Function.identity())
          .build();

  public final ChainDataProvider chainDataProvider;
  public final NodeDataProvider nodeDataProvider;
  public final SyncDataProvider syncDataProvider;

  public PostValidatorLiveness(final DataProvider provider) {
    this(
        provider.getChainDataProvider(),
        provider.getNodeDataProvider(),
        provider.getSyncDataProvider());
  }

  public PostValidatorLiveness(
      final ChainDataProvider chainDataProvider,
      final NodeDataProvider nodeDataProvider,
      final SyncDataProvider syncDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("postValidatorLiveness")
            .summary("Get Validator Liveness")
            .description(
                "Requests the beacon node to indicate if a validator has been"
                    + "observed to be live in a given epoch. The beacon node might detect liveness by"
                    + "observing messages from the validator on the network, in the beacon chain,"
                    + "from its API or from any other source. It is important to note that the"
                    + "values returned by the beacon node are not canonical; they are best-effort"
                    + "and based upon a subjective view of the network.")
            .tags(TAG_EXPERIMENTAL)
            .requestBodyType(ValidatorLivenessRequest.getJsonTypeDefinition())
            .response(SC_OK, "Successful Response", RESPONSE_TYPE)
            .withServiceUnavailableResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
    this.nodeDataProvider = nodeDataProvider;
    this.syncDataProvider = syncDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get Validator Liveness",
      tags = {TAG_EXPERIMENTAL},
      requestBody =
          @OpenApiRequestBody(
              content = {
                @OpenApiContent(
                    from =
                        tech.pegasys.teku.api.request.v1.validator.ValidatorLivenessRequest.class)
              }),
      description =
          "Requests the beacon node to indicate if a validator has been"
              + "    observed to be live in a given epoch. The beacon node might detect liveness by"
              + "    observing messages from the validator on the network, in the beacon chain,"
              + "    from its API or from any other source. It is important to note that the"
              + "    values returned by the beacon node are not canonical; they are best-effort"
              + "    and based upon a subjective view of the network.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = PostValidatorLivenessResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    if (!chainDataProvider.isStoreAvailable() || syncDataProvider.isSyncing()) {
      throw new ServiceUnavailableException();
    }

    final ValidatorLivenessRequest requestBody = request.getRequestBody();
    SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> future =
        nodeDataProvider.getValidatorLiveness(
            requestBody.getIndices(), requestBody.getEpoch(), chainDataProvider.getCurrentEpoch());

    request.respondAsync(
        future.thenApply(
            validatorLivenessAtEpoches ->
                validatorLivenessAtEpoches
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondServiceUnavailable())));
  }
}
