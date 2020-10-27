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
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.ATTESTATION_DATA_ROOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_FORBIDDEN;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_VALIDATOR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBytes32;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUInt64;

import com.google.common.base.Throwables;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.validator.GetAggregatedAttestationResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.beaconrestapi.schema.ErrorResponse;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class GetAggregateAttestation extends AbstractHandler implements Handler {
  public static final String ROUTE = "/eth/v1/validator/aggregate_attestation";
  private final ValidatorDataProvider provider;

  public GetAggregateAttestation(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getValidatorDataProvider(), jsonProvider);
  }

  public GetAggregateAttestation(
      final ValidatorDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get aggregated attestations",
      description = "Aggregates all attestations matching given attestation data root and slot.",
      tags = {TAG_V1_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      queryParams = {
        @OpenApiParam(
            name = ATTESTATION_DATA_ROOT,
            description =
                "`String` HashTreeRoot of AttestationData that validator wants aggregated.",
            required = true),
        @OpenApiParam(
            name = SLOT,
            description = "`uint64` Non-finalized slot for which to create the aggregation.",
            required = true)
      },
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetAggregatedAttestationResponse.class),
            description =
                "Returns aggregated `Attestation` object with same `AttestationData` root."),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied"),
        @OpenApiResponse(
            status = RES_FORBIDDEN,
            description = "Beacon node was not assigned to aggregate on that subnet"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR, description = "Beacon node internal error.")
      })
  @Override
  public void handle(Context ctx) throws Exception {

    try {
      final Map<String, List<String>> parameters = ctx.queryParamMap();
      if (parameters.size() < 2) {
        throw new IllegalArgumentException(
            String.format("Please specify both %s and %s", ATTESTATION_DATA_ROOT, SLOT));
      }
      Bytes32 beacon_block_root = getParameterValueAsBytes32(parameters, ATTESTATION_DATA_ROOT);
      final UInt64 slot = getParameterValueAsUInt64(parameters, SLOT);

      ctx.result(
          provider
              .createAggregate(slot, beacon_block_root)
              .thenApplyChecked(optionalAttestation -> serializeResult(ctx, optionalAttestation))
              .exceptionallyCompose(error -> handleError(ctx, error)));
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private String serializeResult(final Context ctx, final Optional<Attestation> optionalAttestation)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    if (optionalAttestation.isPresent()) {
      ctx.status(SC_OK);
      return jsonProvider.objectToJSON(
          new GetAggregatedAttestationResponse(optionalAttestation.get()));
    } else {
      ctx.status(SC_NOT_FOUND);
      return "";
    }
  }

  /*
   At the moment we aren't enforcing:
   `Beacon node was not assigned to aggregate on that subnet` that should return a status code 403
  */
  private CompletionStage<String> handleError(final Context ctx, final Throwable error) {
    if (Throwables.getRootCause(error) instanceof IllegalArgumentException) {
      ctx.status(SC_BAD_REQUEST);
      return error(SC_BAD_REQUEST, error);
    } else {
      ctx.status(SC_INTERNAL_SERVER_ERROR);
      return error(SC_INTERNAL_SERVER_ERROR, error);
    }
  }

  private SafeFuture<String> error(final int code, final Throwable error) {
    return SafeFuture.of(
        () -> jsonProvider.objectToJSON(new ErrorResponse(code, error.getMessage())));
  }
}
