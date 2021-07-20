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
import java.util.stream.Collectors;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.beacon.PostSyncCommitteeFailure;
import tech.pegasys.teku.api.response.v1.beacon.PostSyncCommitteeFailureResponse;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeMessage;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.validator.api.SubmitCommitteeMessageError;
import tech.pegasys.teku.validator.api.SubmitCommitteeMessagesResult;

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
      summary = "Submit sync committee messages to node",
      tags = {TAG_EXPERIMENTAL},
      requestBody =
          @OpenApiRequestBody(
              content = {@OpenApiContent(from = SyncCommitteeMessage.class, isArray = true)}),
      description =
          "Submits sync committee message objects to the node.\n\n"
              + "Sync committee messages are not present in phase0, but are required for Altair networks.\n\n"
              + "If a sync committee message is validated successfully the node MUST publish that sync committee message on all applicable subnets.\n\n"
              + "If one or more sync committee messages fail validation the node MUST return a 400 error with details of which sync committee messages have failed, and why.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description = "The sync committee messages were accepted, validated, and submitted"),
        @OpenApiResponse(
            status = RES_BAD_REQUEST,
            description = "Errors with one or more sync committee messages",
            content = @OpenApiContent(from = PostSyncCommitteeFailureResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    try {
      final String body = ctx.body();
      final List<SyncCommitteeMessage> messages =
          Arrays.asList(jsonProvider.jsonToObject(body, SyncCommitteeMessage[].class));
      final SafeFuture<SubmitCommitteeMessagesResult> future =
          provider.submitCommitteeSignatures(messages);

      ctx.result(
          future
              .thenApplyChecked(response -> handleResult(ctx, response))
              .exceptionallyCompose(error -> handleError(ctx, error)));

    } catch (final IllegalArgumentException | JsonMappingException e) {
      ctx.result(BadRequest.badRequest(jsonProvider, e.getMessage()));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private String handleResult(Context ctx, final SubmitCommitteeMessagesResult response)
      throws JsonProcessingException {
    final List<SubmitCommitteeMessageError> errors = response.getErrors();
    if (errors.isEmpty()) {
      ctx.status(SC_OK);
      return null;
    }

    final PostSyncCommitteeFailureResponse data =
        new PostSyncCommitteeFailureResponse(
            SC_BAD_REQUEST,
            "Some sync committee subscriptions failed, refer to errors for details",
            errors.stream()
                .map(e -> new PostSyncCommitteeFailure(e.getIndex(), e.getMessage()))
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
