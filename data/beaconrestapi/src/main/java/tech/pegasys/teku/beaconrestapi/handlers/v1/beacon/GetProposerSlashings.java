/*
 * Copyright 2020 ConsenSys AG.
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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetProposerSlashingsResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;

public class GetProposerSlashings extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/beacon/pool/proposer_slashings";
  private final NodeDataProvider nodeDataProvider;

  private static final SerializableTypeDefinition<List<ProposerSlashing>> RESPONSE_TYPE =
      SerializableTypeDefinition.<List<ProposerSlashing>>object()
          .name("GetPoolProposerSlashingsResponse")
          .withField(
              "data",
              listOf(ProposerSlashing.SSZ_SCHEMA.getJsonTypeDefinition()),
              Function.identity())
          .build();

  public GetProposerSlashings(final DataProvider dataProvider) {
    this(dataProvider.getNodeDataProvider());
  }

  GetProposerSlashings(final NodeDataProvider provider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getProposerSlashings")
            .summary("Get proposer slashings")
            .description(
                "Retrieves proposer slashings known by the node but not necessarily incorporated into any block.")
            .tags(TAG_BEACON)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .build());
    this.nodeDataProvider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get proposer slashings",
      tags = {TAG_BEACON},
      description =
          "Retrieves proposer slashings known by the node but not necessarily incorporated into any block.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetProposerSlashingsResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    List<ProposerSlashing> proposerSlashings = nodeDataProvider.getProposerSlashings();
    request.respondOk(proposerSlashings);
  }
}
