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

package tech.pegasys.artemis.beaconrestapi.handlers.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_VALIDATOR;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.ValidatorDuties;
import tech.pegasys.artemis.api.schema.ValidatorDutiesRequest;
import tech.pegasys.artemis.api.schema.ValidatorsRequest;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;

public class PostDuties implements Handler {
  private final ChainDataProvider provider;

  public PostDuties(final ChainDataProvider provider, final JsonProvider jsonProvider) {
    this.provider = provider;
    this.jsonProvider = jsonProvider;
  }

  public static final String ROUTE = "/validator/duties";
  private final JsonProvider jsonProvider;

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Returns validator duties that match the specified query.",
      tags = {TAG_VALIDATOR},
      description =
          "Takes a list of validator public keys and an epoch, and returns validator duties for them.\n\n"
              + "Any pubkeys that were not found in the list of validators will be returned without any associated duties.",
      requestBody =
          @OpenApiRequestBody(
              content = @OpenApiContent(from = ValidatorsRequest.class),
              description =
                  "```\n{\n  \"epoch\": (uint64),\n  \"pubkeys\": [(Bytes48 as Hex String)]\n}\n```"),
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = ValidatorDuties.class, isArray = true)),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid body supplied"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    try {
      if (!provider.isStoreAvailable()) {
        ctx.status(SC_NO_CONTENT);
        return;
      }
      ValidatorDutiesRequest validatorDutiesRequest =
          jsonProvider.jsonToObject(ctx.body(), ValidatorDutiesRequest.class);

      SafeFuture<List<ValidatorDuties>> future =
          provider.getValidatorDutiesByRequest(validatorDutiesRequest);
      ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
      ctx.result(future.thenApplyChecked(duties -> jsonProvider.objectToJSON(duties)));

    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    } catch (final JsonMappingException ex) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(ex.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }
}
