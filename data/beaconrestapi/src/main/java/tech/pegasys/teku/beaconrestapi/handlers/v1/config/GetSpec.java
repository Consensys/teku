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

package tech.pegasys.teku.beaconrestapi.handlers.v1.config;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_CONFIG;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.BadRequest.BAD_REQUEST_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.GetSpecResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.BadRequest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetSpec extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/config/spec";
  private final ConfigProvider configProvider;

  private static final SerializableTypeDefinition<Map<String, String>> GET_SPEC_RESPONSE_TYPE =
      SerializableTypeDefinition.<Map<String, String>>object()
          .name("GetSpecResponse")
          .withField("data", DeserializableTypeDefinition.mapOfStrings(), Function.identity())
          .build();

  public GetSpec(final DataProvider dataProvider) {
    this(dataProvider.getConfigProvider());
  }

  GetSpec(final ConfigProvider configProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getSpec")
            .summary("Get spec params")
            .description("Retrieve specification configuration used on this node.")
            .tags(TAG_CONFIG, TAG_VALIDATOR_REQUIRED)
            .response(SC_OK, "Success", GET_SPEC_RESPONSE_TYPE)
            .build());
    this.configProvider = configProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get spec params",
      tags = {TAG_CONFIG, TAG_VALIDATOR_REQUIRED},
      description = "Retrieve specification configuration used on this node.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content =
                @OpenApiContent(
                    from = tech.pegasys.teku.api.response.v1.config.GetSpecResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    try {
      final GetSpecResponse responseContext = new GetSpecResponse(configProvider.getGenesisSpec());
      request.respondOk(responseContext.getConfigMap());
    } catch (JsonProcessingException e) {
      String message =
          JsonUtil.serialize(new BadRequest(SC_BAD_REQUEST, "Not found"), BAD_REQUEST_TYPE);
      request.respondError(SC_INTERNAL_SERVER_ERROR, message);
    }
  }
}
