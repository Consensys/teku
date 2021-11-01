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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;

public class PostAttestation extends AbstractHandler {
  public static final String ROUTE = "/eth/v1/beacon/pool/attestations";
  private final ValidatorDataProvider provider;

  public PostAttestation(final DataProvider provider, final JsonProvider jsonProvider) {
    this(provider.getValidatorDataProvider(), jsonProvider);
  }

  public PostAttestation(final ValidatorDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Submit signed attestations",
      tags = {TAG_BEACON, TAG_VALIDATOR_REQUIRED},
      requestBody =
          @OpenApiRequestBody(
              content = {@OpenApiContent(from = Attestation.class, isArray = true)}),
      description =
          "Submit signed attestations to the beacon node to be validated and submitted if valid.\n\n"
              + "This endpoint does not protected against slashing.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description = "The Attestation was accepted, validated, and submitted"),
        @OpenApiResponse(
            status = RES_BAD_REQUEST,
            description = "Errors with one or more sync committee messages",
            content = @OpenApiContent(from = PostDataFailureResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    try {
      final List<Attestation> attestations =
          Arrays.asList(parseRequestBody(ctx.body(), Attestation[].class));
      final SafeFuture<Optional<PostDataFailureResponse>> future =
          provider.submitAttestations(attestations);

      handlePostDataResult(ctx, future);
    } catch (final IllegalArgumentException e) {
      ctx.json(BadRequest.badRequest(jsonProvider, e.getMessage()));
      ctx.status(SC_BAD_REQUEST);
    }
  }
}
