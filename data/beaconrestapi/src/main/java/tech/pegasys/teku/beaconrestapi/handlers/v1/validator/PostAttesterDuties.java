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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_VALIDATOR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.validator.PostAttesterDutiesResponse;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class PostAttesterDuties extends AbstractHandler implements Handler {
  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/validator/duties/attester/:epoch";
  private final ValidatorDataProvider validatorDataProvider;
  private final SyncDataProvider syncDataProvider;

  public PostAttesterDuties(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.validatorDataProvider = dataProvider.getValidatorDataProvider();
    this.syncDataProvider = dataProvider.getSyncDataProvider();
  }

  PostAttesterDuties(
      final SyncDataProvider syncDataProvider,
      final ValidatorDataProvider validatorDataProvider,
      final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.validatorDataProvider = validatorDataProvider;
    this.syncDataProvider = syncDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Get attester duties",
      tags = {TAG_V1_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      description =
          "Requests the beacon node to provide a set of attestation duties, "
              + "which should be performed by validators, for a particular epoch. "
              + "Duties should only need to be checked once per epoch, however a chain "
              + "reorganization (of > MIN_SEED_LOOKAHEAD epochs) could occur, "
              + "resulting in a change of duties. "
              + "For full safety, you should monitor head events and confirm the dependent root in "
              + "this response matches:\n"
              + "- event.previous_duty_dependent_root when `compute_epoch_at_slot(event.slot) == epoch`\n"
              + "- event.current_duty_dependent_root when `compute_epoch_at_slot(event.slot) + 1 == epoch`\n"
              + "- event.block otherwise\n\n"
              + "The dependent_root value is "
              + "`get_block_root_at_slot(state, compute_start_slot_at_epoch(epoch - 1) - 1)` "
              + "or the genesis block root in the case of underflow.",
      requestBody =
          @OpenApiRequestBody(
              content = @OpenApiContent(from = String[].class),
              description =
                  "An array of the validator indices for which to obtain the duties.\n\n"
                      + "```\n[\n  \"(uint64)\",\n  ...\n]\n```\n\n"),
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = PostAttesterDutiesResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    if (!validatorDataProvider.isStoreAvailable() || syncDataProvider.isSyncing()) {
      ctx.status(SC_SERVICE_UNAVAILABLE);
      return;
    }
    final Map<String, String> parameters = ctx.pathParamMap();
    try {
      final UInt64 epoch = UInt64.valueOf(parameters.get(EPOCH));
      final UInt64[] indexes = jsonProvider.jsonToObject(ctx.body(), UInt64[].class);

      SafeFuture<Optional<PostAttesterDutiesResponse>> future =
          validatorDataProvider.getAttesterDuties(
              epoch, Arrays.stream(indexes).map(UInt64::intValue).collect(Collectors.toList()));

      handleOptionalResult(
          ctx, future, this::handleResult, this::handleError, SC_SERVICE_UNAVAILABLE);

    } catch (NumberFormatException ex) {
      LOG.trace("Error parsing", ex);
      ctx.status(SC_BAD_REQUEST);
      final String message = "Invalid epoch " + parameters.get(EPOCH) + " or index specified";
      ctx.result(BadRequest.badRequest(jsonProvider, message));
    } catch (IllegalArgumentException ex) {
      LOG.trace("Illegal argument in PostAttesterDuties", ex);
      ctx.status(SC_BAD_REQUEST);
      ctx.result(BadRequest.badRequest(jsonProvider, ex.getMessage()));
    }
  }

  private Optional<String> handleResult(Context ctx, final PostAttesterDutiesResponse response)
      throws JsonProcessingException {
    return Optional.of(jsonProvider.objectToJSON(response));
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
