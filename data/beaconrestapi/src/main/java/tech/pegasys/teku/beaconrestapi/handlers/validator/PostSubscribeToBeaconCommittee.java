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
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.request.SubscribeToBeaconCommitteeRequest;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.provider.JsonProvider;

public class PostSubscribeToBeaconCommittee implements Handler {

  public static final String ROUTE = "/validator/beacon_committee_subscription";

  private final ValidatorDataProvider provider;
  private final JsonProvider jsonProvider;

  public PostSubscribeToBeaconCommittee(
      final ValidatorDataProvider provider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.provider = provider;
  }

  @OpenApi(
      deprecated = true,
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Subscribe beacon node to committee attestation subnet.",
      tags = {TAG_VALIDATOR},
      requestBody =
          @OpenApiRequestBody(
              content = {@OpenApiContent(from = SubscribeToBeaconCommitteeRequest.class)}),
      description =
          "After Beacon node receives this request it has to:\n"
              + "- add subnet to ENR\n"
              + "- announce subnet topic subscription on gossipsub\n"
              + "- search using discv5 for peers related to this subnet and replace current peers with those ones if necessary\n"
              + "- aggregate attestations received on that subnet\n"
              + "Deprecated - use `/eth/v1/validator/beacon_committee_subscriptions` instead.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description =
                "Slot signature is valid and beacon node is subscribed to given committee attestation subnet."),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid request syntax."),
        @OpenApiResponse(status = RES_INTERNAL_ERROR, description = "Beacon node internal error.")
      })
  @Override
  public void handle(Context ctx) throws Exception {
    try {
      final SubscribeToBeaconCommitteeRequest request =
          jsonProvider.jsonToObject(ctx.body(), SubscribeToBeaconCommitteeRequest.class);

      provider.subscribeToBeaconCommitteeForAggregation(request);
      ctx.status(SC_OK);
    } catch (final JsonMappingException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    } catch (Exception e) {
      ctx.status(SC_INTERNAL_SERVER_ERROR);
    }
  }
}
