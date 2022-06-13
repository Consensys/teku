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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static com.google.common.net.HttpHeaders.CACHE_CONTROL;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.response.v1.teku.GetProposersDataResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.beaconrestapi.schema.ProposersData;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetProposersData extends MigratingEndpointAdapter {
  public static final String ROUTE = "/teku/v1/beacon/proposers_data";

  private final NodeDataProvider nodeDataProvider;

  public GetProposersData(final DataProvider provider) {
    this(provider.getNodeDataProvider());
  }

  GetProposersData(final NodeDataProvider nodeDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getProposersData")
            .summary("Get current prepared beacon proposers and registered validators")
            .description(
                "Get the current proposers information held by beacon node as result of "
                    + "prepare_beacon_proposer and register_validator validator API calls. This API is "
                    + "considered unstable and the returned data format may change in the future.")
            .tags(TAG_EXPERIMENTAL)
            .response(SC_OK, "Request successful", ProposersData.getJsonTypeDefinition())
            .withServiceUnavailableResponse() // TODO check if needed
            .build());
    this.nodeDataProvider = nodeDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get current prepared beacon proposers and registered validators",
      tags = {TAG_EXPERIMENTAL},
      description =
          "Get the current proposers information held by beacon node as result of prepare_beacon_proposer and register_validator validator API calls. This API is considered unstable and the returned data format may change in the future.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetProposersDataResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    request.header(CACHE_CONTROL, CACHE_NONE);
    request.respondOk(
        new ProposersData(
            nodeDataProvider.getPreparedProposerInfo(),
            nodeDataProvider.getValidatorRegistrationInfo()));
  }
}
