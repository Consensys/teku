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

package tech.pegasys.artemis.beaconrestapi.handlers.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsInt;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUnsignedLong;

import com.google.common.primitives.UnsignedLong;
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
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.Attestation;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.provider.JsonProvider;

public class GetAttestation implements Handler {
  public static final String ROUTE = "/validator/attestation";

  private final ChainDataProvider provider;
  private final JsonProvider jsonProvider;

  public GetAttestation(final ChainDataProvider provider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get an unsigned attestation for a slot from the current state.",
      tags = {TAG_VALIDATOR},
      queryParams = {
        @OpenApiParam(
            name = SLOT,
            description = "Non-finalized slot for which to create the attestation.",
            required = true),
        @OpenApiParam(
            name = COMMITTEE_INDEX,
            description = "Index of the committee making the attestation.",
            required = true)
      },
      description =
          "Returns an unsigned attestation for the block at the specified non-finalized slot.\n\nThis endpoint is not protected against slashing. Signing the returned attestation can result in a slashable offence.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = Attestation.class),
            description =
                "Returns an attestation object with a blank signature. The `signature` field should be replaced by a valid signature."),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied"),
        @OpenApiResponse(
            status = RES_NOT_FOUND,
            description = "An attestation could not be created for the specified slot.")
      })
  @Override
  public void handle(Context ctx) throws Exception {

    try {
      final Map<String, List<String>> parameters = ctx.queryParamMap();
      if (parameters.size() < 2) {
        throw new IllegalArgumentException(
            String.format("Please specify both %s and %s", SLOT, COMMITTEE_INDEX));
      }
      UnsignedLong slot = getParameterValueAsUnsignedLong(parameters, SLOT);
      int committeeIndex = getParameterValueAsInt(parameters, COMMITTEE_INDEX);
      if (committeeIndex < 0) {
        throw new IllegalArgumentException(
            String.format("'%s' needs to be greater than or equal to 0.", COMMITTEE_INDEX));
      }
      if (!provider.isStoreAvailable()) {
        ctx.status(SC_NO_CONTENT);
        return;
      }

      Optional<Attestation> optionalAttestation =
          provider.getUnsignedAttestationAtSlot(slot, committeeIndex);
      if (optionalAttestation.isPresent()) {
        ctx.result(jsonProvider.objectToJSON(optionalAttestation.get()));
      } else {
        ctx.status(SC_NOT_FOUND);
      }
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }
}
