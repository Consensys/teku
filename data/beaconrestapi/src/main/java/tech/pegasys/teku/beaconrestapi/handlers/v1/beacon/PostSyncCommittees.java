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
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Throwables;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.beacon.PostSyncCommitteeFailure;
import tech.pegasys.teku.api.response.v1.beacon.PostSyncCommitteeFailureResponse;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeSignature;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.validator.api.SubmitCommitteeSignatureError;
import tech.pegasys.teku.validator.api.SubmitCommitteeSignaturesResult;

public class PostSyncCommittees extends AbstractHandler {
  public static final String ROUTE = "/eth/v1/beacon/pool/sync_committees";
  private final ValidatorDataProvider provider;

  public PostSyncCommittees(final DataProvider provider, final JsonProvider jsonProvider) {
    this(provider.getValidatorDataProvider(), jsonProvider);
  }

  public PostSyncCommittees(final ValidatorDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Submit sync committee signatures to node",
      tags = {TAG_EXPERIMENTAL},
      requestBody =
          @OpenApiRequestBody(
              content = {@OpenApiContent(from = SyncCommitteeSignature.class, isArray = true)}),
      description =
          "Submits sync committee signature objects to the node.\n\n"
              + "Sync committee signatures are not present in phase0, but are required for Altair networks.\n\n"
              + "If a sync committee signature is validated successfully the node MUST publish that sync committee signature on all applicable subnets.\n\n"
              + "If one or more sync committee signatures fail validation the node MUST return a 400 error with details of which sync committee signatures have failed, and why.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description = "The sync committee signatures were accepted, validated, and submitted"),
        @OpenApiResponse(
            status = RES_BAD_REQUEST,
            description = "Errors with one or more sync committee signature",
            content = @OpenApiContent(from = PostSyncCommitteeFailureResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    try {
      final String body = ctx.body();
      final List<SyncCommitteeSignature> signatures =
          Arrays.asList(jsonProvider.jsonToObject(body, SyncCommitteeSignature[].class));
      final SafeFuture<Optional<SubmitCommitteeSignaturesResult>> future =
          provider.submitCommitteeSignatures(signatures);

      handleOptionalResult(
          ctx, future, this::handleResult, this::handleError, SC_SERVICE_UNAVAILABLE);

    } catch (final IllegalArgumentException | JsonMappingException e) {
      ctx.result(BadRequest.badRequest(jsonProvider, e.getMessage()));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private Optional<String> handleResult(Context ctx, final SubmitCommitteeSignaturesResult response)
      throws JsonProcessingException {
    final List<SubmitCommitteeSignatureError> errors = response.getErrors();
    if (errors.isEmpty()) {
      ctx.status(SC_OK);
      return Optional.empty();
    }

    final PostSyncCommitteeFailureResponse data =
        new PostSyncCommitteeFailureResponse(
            SC_BAD_REQUEST,
            "Some sync committee subscriptions failed, refer to errors for details",
            errors.stream()
                .map(e -> new PostSyncCommitteeFailure(e.getIndex(), e.getMessage()))
                .collect(Collectors.toList()));
    ctx.status(SC_BAD_REQUEST);
    return Optional.of(jsonProvider.objectToJSON(data));
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
