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

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_CONFIG;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.config.GetSpecResponse;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.SpecProvider;

public class GetSpec implements Handler {
  public static final String ROUTE = "/eth/v1/config/spec";
  private final JsonProvider jsonProvider;
  private final SpecProvider specProvider;

  public GetSpec(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this.specProvider = dataProvider.getSpecProvider();
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get spec params",
      tags = {TAG_CONFIG, TAG_VALIDATOR_REQUIRED},
      description = "Retrieve specification configuration used on this node.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = GetSpecResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    try {

      final Map<String, String> configAttributes = new HashMap<>();
      specProvider
          // Display genesis spec, for now
          .get(UInt64.ZERO)
          .getConstants()
          .getRawConstants()
          .forEach(
              (k, v) -> {
                configAttributes.put(k, "" + v);
              });
      ctx.result(jsonProvider.objectToJSON(new GetSpecResponse(configAttributes)));
    } catch (JsonProcessingException e) {
      ctx.result(BadRequest.badRequest(jsonProvider, e.getMessage()));
      ctx.status(SC_INTERNAL_SERVER_ERROR);
    }
  }
}
