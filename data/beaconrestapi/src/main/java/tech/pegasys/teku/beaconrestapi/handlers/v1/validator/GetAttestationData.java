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
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsInt;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUInt64;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.validator.GetAttestationDataResponse;
import tech.pegasys.teku.api.schema.AttestationData;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class GetAttestationData extends AbstractHandler {
  public static final String ROUTE = "/eth/v1/validator/attestation_data";

  private final ValidatorDataProvider provider;

  public GetAttestationData(final DataProvider provider, final JsonProvider jsonProvider) {
    this(provider.getValidatorDataProvider(), jsonProvider);
  }

  public GetAttestationData(final ValidatorDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Produce an AttestationData",
      tags = {TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      queryParams = {
        @OpenApiParam(
            name = SLOT,
            description = "`uint64` The slot for which an attestation data should be created.",
            required = true),
        @OpenApiParam(
            name = COMMITTEE_INDEX,
            type = Integer.class,
            description =
                "`Integer` The committee index for which an attestation data should be created.",
            required = true)
      },
      description =
          "Returns attestation data for the block at the specified non-finalized slot.\n\n"
              + "This endpoint is not protected against slashing. Signing the returned attestation data can result in a slashable offence.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetAttestationDataResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    try {
      final Map<String, List<String>> parameters = ctx.queryParamMap();
      if (parameters.size() < 2) {
        throw new IllegalArgumentException(
            String.format("Please specify both %s and %s", SLOT, COMMITTEE_INDEX));
      }
      UInt64 slot = getParameterValueAsUInt64(parameters, SLOT);
      int committeeIndex = getParameterValueAsInt(parameters, COMMITTEE_INDEX);
      if (committeeIndex < 0) {
        throw new IllegalArgumentException(
            String.format("'%s' needs to be greater than or equal to 0.", COMMITTEE_INDEX));
      }

      final SafeFuture<Optional<AttestationData>> future =
          provider.createAttestationDataAtSlot(slot, committeeIndex);
      handleOptionalResult(ctx, future, this::processResult, SC_BAD_REQUEST);
    } catch (final IllegalArgumentException e) {
      ctx.json(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private Optional<String> processResult(final Context ctx, final AttestationData attestationData)
      throws JsonProcessingException {
    return Optional.of(jsonProvider.objectToJSON(new GetAttestationDataResponse(attestationData)));
  }
}
