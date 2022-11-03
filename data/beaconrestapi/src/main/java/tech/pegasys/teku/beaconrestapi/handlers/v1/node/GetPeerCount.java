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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;

public class GetPeerCount extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/node/peer_count";

  private static final SerializableTypeDefinition<ResponseData> PEER_COUNT_TYPE =
      SerializableTypeDefinition.object(ResponseData.class)
          .withField("disconnected", UINT64_TYPE, ResponseData::getDisconnected)
          .withField("connecting", UINT64_TYPE, responseData -> UInt64.valueOf(0))
          .withField("connected", UINT64_TYPE, ResponseData::getConnected)
          .withField("disconnecting", UINT64_TYPE, responseData -> UInt64.valueOf(0))
          .build();

  private static final SerializableTypeDefinition<ResponseData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(ResponseData.class)
          .name("GetPeerCountResponse")
          .withField("data", PEER_COUNT_TYPE, Function.identity())
          .build();

  private final NetworkDataProvider network;

  public GetPeerCount(final DataProvider provider) {
    this(provider.getNetworkDataProvider());
  }

  GetPeerCount(final NetworkDataProvider network) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getPeerCount")
            .summary("Get peer count")
            .description("Retrieves number of known peers.")
            .tags(TAG_NODE)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .build());
    this.network = network;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    request.respondOk(new ResponseData(network.getEth2Peers()));
  }

  static class ResponseData {
    final UInt64 disconnected;
    final UInt64 connected;

    ResponseData(List<Eth2Peer> peers) {
      long disconnected = 0;
      long connected = 0;

      for (Eth2Peer peer : peers) {
        if (peer.isConnected()) {
          connected++;
        } else {
          disconnected++;
        }
      }

      this.disconnected = UInt64.valueOf(disconnected);
      this.connected = UInt64.valueOf(connected);
    }

    UInt64 getDisconnected() {
      return disconnected;
    }

    UInt64 getConnected() {
      return connected;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ResponseData that = (ResponseData) o;
      return Objects.equals(disconnected, that.disconnected)
          && Objects.equals(connected, that.connected);
    }

    @Override
    public int hashCode() {
      return Objects.hash(disconnected, connected);
    }
  }
}
