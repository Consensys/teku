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

package tech.pegasys.teku.beaconrestapi.handlers.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_BEACON;

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
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.schema.BeaconValidators;
import tech.pegasys.teku.api.schema.PublicKeyException;
import tech.pegasys.teku.api.schema.ValidatorWithIndex;
import tech.pegasys.teku.api.schema.ValidatorsRequest;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;

public class PostValidators extends AbstractHandler implements Handler {
  public static final String ROUTE = "/beacon/validators";

  private final ChainDataProvider chainDataProvider;

  @OpenApi(
      deprecated = true,
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Get validators matching specified public keys.",
      tags = {TAG_BEACON},
      description =
          "Returns information about validators that match the list of validator public keys and optional epoch.\n\n"
              + "If no epoch is specified, the validators are queried from the current state.\n\n"
              + "Public keys that do not match a validator are returned without validator information.\n"
              + "Deprecated - use `/eth/v1/beacon/states/{state_id}/validators/{validator_id}` instead.",
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
      final SafeFuture<Optional<BeaconValidators>> validatorsFuture =
          chainDataProvider.getValidatorsByValidatorsRequest(request);

      if (request.epoch != null && chainDataProvider.isFinalizedEpoch(request.epoch)) {
        handlePossiblyGoneResult(ctx, validatorsFuture, this::processResult);
      } else {
        handlePossiblyMissingResult(ctx, validatorsFuture, this::processResult);
      }
      ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    } catch (JsonMappingException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof PublicKeyException) {
        ctx.result(
            jsonProvider.objectToJSON(
                new BadRequest("Public key is not valid: " + ex.getMessage())));
      }
      ctx.status(SC_BAD_REQUEST);
    }
  }

  public PostValidators(
      final ChainDataProvider chainDataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = chainDataProvider;
  }

  private Optional<String> processResult(
      final Context context, final BeaconValidators beaconValidators)
      throws JsonProcessingException {
    return Optional.of(jsonProvider.objectToJSON(beaconValidators.validators));
  }
}
