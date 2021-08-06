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
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailure;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SubmitDataResult;

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
      final SafeFuture<SubmitDataResult> future = provider.submitAttestations(attestations);

      ctx.result(
          future
              .thenApplyChecked(response -> handleResult(ctx, response))
              .exceptionallyCompose(error -> handleError(ctx, error)));
    } catch (final IllegalArgumentException e) {
      ctx.result(BadRequest.badRequest(jsonProvider, e.getMessage()));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private String handleResult(Context ctx, final SubmitDataResult response)
      throws JsonProcessingException {
    final List<SubmitDataError> errors = response.getErrors();
    if (errors.isEmpty()) {
      ctx.status(HttpStatusCodes.SC_OK);
      return null;
    }

    final PostDataFailureResponse data =
        new PostDataFailureResponse(
            SC_BAD_REQUEST,
            "Some attestations failed to publish, refer to errors for details",
            errors.stream()
                .map(e -> new PostDataFailure(e.getIndex(), e.getMessage()))
                .collect(Collectors.toList()));
    ctx.status(SC_BAD_REQUEST);
    return jsonProvider.objectToJSON(data);
  }

  private SafeFuture<String> handleError(final Context ctx, final Throwable error) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof IllegalArgumentException) {
      ctx.status(SC_BAD_REQUEST);
      return SafeFuture.of(() -> BadRequest.badRequest(jsonProvider, rootCause.getMessage()));
    } else {
      return failedFuture(error);
    }
  }
}
