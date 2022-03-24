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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.response.v1.node.VersionResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.version.VersionProvider;

public class GetVersion extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/node/version";

  private static final SerializableTypeDefinition<String> DATA_TYPE =
      SerializableTypeDefinition.object(String.class)
          .withField("version", STRING_TYPE, Function.identity())
          .build();

  private static final SerializableTypeDefinition<String> RESPONSE_TYPE =
      SerializableTypeDefinition.object(String.class)
          .name("GetVersionResponse")
          .withField("data", DATA_TYPE, Function.identity())
          .build();

  public GetVersion() {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getNodeVersion")
            .summary("Get node version")
            .description(
                "similar to [HTTP User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3).")
            .tags(TAG_NODE)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .build());
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get node version",
      description =
          "similar to [HTTP User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3).",
      tags = {TAG_NODE},
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = VersionResponse.class))
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    request.respondOk(VersionProvider.VERSION);
  }
}
