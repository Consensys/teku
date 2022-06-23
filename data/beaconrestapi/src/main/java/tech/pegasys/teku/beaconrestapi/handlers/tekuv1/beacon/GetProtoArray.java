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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.mapOfStrings;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.teku.GetProtoArrayResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetProtoArray extends MigratingEndpointAdapter {
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

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get current fork choice data",
      tags = {TAG_TEKU},
      description =
          "Get the raw data stored in the fork choice protoarray to aid debugging. "
              + "This API is considered unstable and the returned data format may change in the future.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetProtoArrayResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);

    final List<Map<String, String>> data =
        chainDataProvider.getProtoArrayData().stream()
            .map(this::mapObjectValuesToString)
            .collect(Collectors.toList());
    request.respondOk(data);
  }

  // TODO check this correct approach to making mapping type work
  private Map<String, String> mapObjectValuesToString(final Map<String, Object> objectMap) {
    final Map<String, String> output = new HashMap<>();
    for (String key : objectMap.keySet()) {
      final Object value = objectMap.get(key);
      if (value instanceof UInt64) {
        output.put(key, value.toString());
      } else if (value instanceof Bytes32) {
        output.put(key, ((Bytes32) value).toHexString());
      } else if (value instanceof String) {
        output.put(key, (String) value);
      }
    }
    return output;
  }
}
