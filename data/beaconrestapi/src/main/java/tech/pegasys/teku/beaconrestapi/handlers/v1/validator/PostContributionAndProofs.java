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

import static java.util.Arrays.asList;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.altair.SignedContributionAndProof;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.provider.JsonProvider;

public class PostContributionAndProofs implements Handler {

  public static final String ROUTE = "/eth/v1/validator/contribution_and_proofs";

  private final ValidatorDataProvider provider;
  private final JsonProvider jsonProvider;

  public PostContributionAndProofs(final DataProvider provider, final JsonProvider jsonProvider) {
    this(provider.getValidatorDataProvider(), jsonProvider);
  }

  public PostContributionAndProofs(
      final ValidatorDataProvider provider, final JsonProvider jsonProvider) {
    this.provider = provider;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Publish contribution and proofs",
      tags = {TAG_EXPERIMENTAL},
      requestBody =
          @OpenApiRequestBody(
              content = {@OpenApiContent(from = SignedContributionAndProof.class, isArray = true)}),
      description =
          "Verifies given sync committee contribution and proofs and publishes on appropriate gossipsub topics.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description = "Successfully published contribution and proofs."),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied."),
        @OpenApiResponse(status = RES_INTERNAL_ERROR, description = "Beacon node internal error.")
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    try {
      final SignedContributionAndProof[] signedContributionAndProofs =
          jsonProvider.jsonToObject(ctx.body(), SignedContributionAndProof[].class);

      provider.sendContributionAndProofs(asList(signedContributionAndProofs));
      ctx.status(SC_OK);
    } catch (final JsonMappingException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }
}
