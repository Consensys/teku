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

package tech.pegasys.artemis.beaconrestapi.handlers.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_BEACON;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.BeaconValidators;
import tech.pegasys.artemis.api.schema.ValidatorWithIndex;
import tech.pegasys.artemis.api.schema.ValidatorsRequest;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.provider.JsonProvider;

public class PostValidators implements Handler {
  private final ChainDataProvider chainDataProvider;

  public PostValidators(
      final ChainDataProvider chainDataProvider, final JsonProvider jsonProvider) {
    this.chainDataProvider = chainDataProvider;
    this.jsonProvider = jsonProvider;
  }

  public static final String ROUTE = "/beacon/validators";
  private final JsonProvider jsonProvider;

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Get validators that match specified public keys.",
      tags = {TAG_BEACON},
      description =
          "Returns information about validators that match the list of validator public keys and optional epoch.\n\n"
              + "If no epoch is specified, the validators are queried from the current state.\n\n"
              + "Public keys that do not match a validator are returned without validator information.",
      requestBody =
          @OpenApiRequestBody(
              content = {@OpenApiContent(from = ValidatorsRequest.class)},
              description =
                  "```\n{\n  \"epoch\": (uint64),\n  \"pubkeys\": [(Bytes48 as Hex String)]\n}\n```"),
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = ValidatorWithIndex.class, isArray = true),
            description = "List of validator objects."),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid body supplied"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    try {
      ValidatorsRequest request = jsonProvider.jsonToObject(ctx.body(), ValidatorsRequest.class);
      ctx.result(
          chainDataProvider
              .getValidatorsByValidatorsRequest(request)
              .thenApplyChecked(validators -> handleResponseContext(ctx, validators)));

      ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    } catch (JsonMappingException ex) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(ex.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private String handleResponseContext(Context ctx, Optional<BeaconValidators> response)
      throws JsonProcessingException {
    if (response.isEmpty()) {
      ctx.status(SC_NO_CONTENT);
      return null;
    }
    return jsonProvider.objectToJSON(response.get().validators);
  }
}
