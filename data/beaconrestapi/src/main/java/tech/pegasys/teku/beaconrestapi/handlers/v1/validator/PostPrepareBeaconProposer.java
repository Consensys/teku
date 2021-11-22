/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.merge.BeaconPreparableProposer;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.provider.JsonProvider;

public class PostPrepareBeaconProposer extends AbstractHandler implements Handler {
  public static final String ROUTE = "/eth/v1/validator/prepare_beacon_proposer";

  private final ValidatorDataProvider validatorDataProvider;

  public PostPrepareBeaconProposer(final DataProvider provider, final JsonProvider jsonProvider) {
    this(provider.getValidatorDataProvider(), jsonProvider);
  }

  public PostPrepareBeaconProposer(
      final ValidatorDataProvider validatorDataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.validatorDataProvider = validatorDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Provide beacon node with proposals for the given validators.",
      tags = {TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      requestBody =
          @OpenApiRequestBody(content = {@OpenApiContent(from = BeaconPreparableProposer[].class)}),
      description =
          "Prepares the beacon node for potential proposers by supplying information required when proposing blocks for the given validators. The information supplied for each validator index is considered persistent until overwritten by new information for the given validator index, or until the beacon node restarts.\n\n"
              + "Note that because the information is not persistent across beacon node restarts it is recommended that either the beacon node is monitored for restarts or this information is refreshed by resending this request periodically (for example, each epoch).\n\n"
              + "Also note that requests containing currently inactive or unknown validator indices will be accepted, as they may become active at a later epoch.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description = "Preparation information has been received."),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied."),
        @OpenApiResponse(status = RES_INTERNAL_ERROR, description = "Beacon node internal error.")
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    try {
      final BeaconPreparableProposer[] request =
          parseRequestBody(ctx.body(), BeaconPreparableProposer[].class);

      validatorDataProvider.prepareBeaconProposer(List.of(request));

      ctx.status(SC_OK);
    } catch (final IllegalArgumentException e) {
      ctx.json(BadRequest.badRequest(jsonProvider, e.getMessage()));
      ctx.status(HttpStatusCodes.SC_BAD_REQUEST);
    }
  }
}
