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

package tech.pegasys.teku.beaconrestapi.handlers.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.INVALID_BODY_SUPPLIED;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR;

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
import java.util.Optional;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.ValidatorDuties;
import tech.pegasys.teku.api.schema.ValidatorDutiesRequest;
import tech.pegasys.teku.api.schema.ValidatorsRequest;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.util.async.SafeFuture;

public class PostDuties extends AbstractHandler implements Handler {
  public static final String ROUTE = "/validator/duties";

  private final ValidatorDataProvider provider;

  public PostDuties(final ValidatorDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Get the validator duties for the specified epoch.",
      tags = {TAG_VALIDATOR},
      description =
          "Returns the validator duties for validators that match the specified public keys and epoch.\n\n"
              + "Public keys that do not match a validator are returned without validator information.",
      requestBody =
          @OpenApiRequestBody(
              content = @OpenApiContent(from = ValidatorsRequest.class),
              description =
                  "```\n{\n  \"epoch\": (uint64),\n  \"pubkeys\": [(Bytes48 as Hex String)]\n}\n```"),
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = ValidatorDuties.class, isArray = true),
            description =
                "List of validators, including information about a validator's attestation committee index"
                    + " and block proposal slot."),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = INVALID_BODY_SUPPLIED),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    try {
      ValidatorDutiesRequest validatorDutiesRequest =
          jsonProvider.jsonToObject(ctx.body(), ValidatorDutiesRequest.class);

      SafeFuture<Optional<List<ValidatorDuties>>> future =
          provider.getValidatorDutiesByRequest(validatorDutiesRequest);
      ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
      if (provider.isEpochFinalized(validatorDutiesRequest.epoch)) {
        handlePossiblyGoneResult(ctx, future);
      } else {
        handlePossiblyMissingResult(ctx, future);
      }
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    } catch (final JsonMappingException ex) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(ex.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }
}
