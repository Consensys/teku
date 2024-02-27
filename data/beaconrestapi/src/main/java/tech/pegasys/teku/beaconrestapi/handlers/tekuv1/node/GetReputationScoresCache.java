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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.node;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetReputationScoresCache extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/nodes/peer_reputations";
  private final NetworkDataProvider network;

  public GetReputationScoresCache(final DataProvider provider) {
    this(provider.getNetworkDataProvider());
  }

  GetReputationScoresCache(final NetworkDataProvider network) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getReputationCache")
            .summary("Get Peer Reputations")
            .description("Retrieves the peers banned or on cooldown.")
            .tags(TAG_TEKU)
            .response(SC_OK, "Request successful", DeserializableTypeDefinition.mapOfStrings())
            .build());
    this.network = network;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    request.respondOk(network.getPeerReputations());
  }
}
