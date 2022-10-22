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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.mapOfStrings;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetProtoArray extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/debug/beacon/protoarray";
  private final ChainDataProvider chainDataProvider;

  public GetProtoArray(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetProtoArray(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getProtoArray")
            .summary("Get current fork choice data")
            .description(
                "Get the raw data stored in the fork choice protoarray to aid debugging. "
                    + "This API is considered unstable and the returned data format may change in the future.")
            .tags(TAG_TEKU)
            .response(SC_OK, "Request successful", listOf(mapOfStrings()))
            .withServiceUnavailableResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    request.respondOk(chainDataProvider.getProtoArrayData());
  }
}
