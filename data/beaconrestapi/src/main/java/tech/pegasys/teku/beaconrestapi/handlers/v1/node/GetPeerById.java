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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PEER_ID_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler.routeWithBracedParameters;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeers.PEER_DATA_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_PEER_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_PEER_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.response.v1.node.PeerResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;

public class GetPeerById extends MigratingEndpointAdapter {
  private static final String OAPI_ROUTE = "/eth/v1/node/peers/:peer_id";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);

  private static final SerializableTypeDefinition<Eth2Peer> PEERS_BY_ID_RESPONSE_TYPE =
      SerializableTypeDefinition.object(Eth2Peer.class)
          .name("GetPeerResponse")
          .withField("data", PEER_DATA_TYPE, Function.identity())
          .build();

  private final NetworkDataProvider network;

  public GetPeerById(final DataProvider provider) {
    this(provider.getNetworkDataProvider());
  }

  GetPeerById(final NetworkDataProvider network) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getNodePeer")
            .summary("Get node peer")
            .description("Retrieves data about the given peer.")
            .pathParam(PEER_ID_PARAMETER)
            .tags(TAG_NODE)
            .response(SC_OK, "Request successful", PEERS_BY_ID_RESPONSE_TYPE)
            .response(SC_NOT_FOUND, "Peer not found")
            .build());
    this.network = network;
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      summary = "Get node peer",
      tags = {TAG_NODE},
      description = "Retrieves data about the given peer.",
      pathParams = {@OpenApiParam(name = PARAM_PEER_ID, description = PARAM_PEER_ID_DESCRIPTION)},
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = PeerResponse.class)),
        @OpenApiResponse(status = RES_NOT_FOUND, description = "Peer not found"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    Optional<Eth2Peer> peer = network.getEth2PeerById(request.getPathParameter(PEER_ID_PARAMETER));
    if (peer.isEmpty()) {
      request.respondError(SC_NOT_FOUND, "Peer not found");
    } else {
      request.respondOk(peer.get());
    }
  }
}
