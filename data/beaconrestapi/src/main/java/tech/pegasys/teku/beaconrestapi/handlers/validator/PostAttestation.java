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

package tech.pegasys.teku.beaconrestapi.handlers.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.INVALID_BODY_SUPPLIED;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.provider.JsonProvider;

public class PostAttestation implements Handler {
  public static final String ROUTE = "/validator/attestation";

  private final ValidatorDataProvider provider;
  private final JsonProvider jsonProvider;

  public PostAttestation(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getValidatorDataProvider(), jsonProvider);
  }

  public PostAttestation(final ValidatorDataProvider provider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      deprecated = true,
      summary = "Submit a signed attestation",
      tags = {TAG_VALIDATOR},
      requestBody = @OpenApiRequestBody(content = {@OpenApiContent(from = Attestation.class)}),
      description =
          "Submit a signed attestation to the beacon node to be validated and submitted if valid.\n\n"
              + "This endpoint does not protected against slashing. Signing the attestation can result in a slashable offence.\n\n"
              + "Deprecated - use `/eth/v1/beacon/pool/attestations` instead.",
      responses = {
        @OpenApiResponse(
            status = RES_NO_CONTENT,
            description = "The Attestation was accepted, validated, and submitted"),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = INVALID_BODY_SUPPLIED),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {

    try {
      Attestation attestation = jsonProvider.jsonToObject(ctx.body(), Attestation.class);
      provider.submitAttestation(attestation);
      ctx.status(SC_NO_CONTENT);
    } catch (final IllegalArgumentException | JsonMappingException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }
}
