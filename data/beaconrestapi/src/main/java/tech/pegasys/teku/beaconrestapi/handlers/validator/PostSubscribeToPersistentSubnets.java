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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.SubnetSubscription;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.provider.JsonProvider;

public class PostSubscribeToPersistentSubnets implements Handler {

  public static final String ROUTE = "/validator/persistent_subnets_subscription";

  private final ValidatorDataProvider provider;
  private final JsonProvider jsonProvider;

  public PostSubscribeToPersistentSubnets(
      final ValidatorDataProvider provider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.provider = provider;
  }

  @OpenApi(
      deprecated = true,
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Subscribe beacon node persistently to a list of subnets.",
      description =
          "Deprecated - Persistent subnets are automatically managed based on requests to `/eth/v1/validator/beacon_committee_subscriptions`.",
      tags = {TAG_VALIDATOR},
      requestBody =
          @OpenApiRequestBody(
              content = {@OpenApiContent(from = SubnetSubscription.class, isArray = true)}),
      responses = {
        @OpenApiResponse(status = RES_OK, description = "Subscribed to subnets."),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid request syntax."),
        @OpenApiResponse(status = RES_INTERNAL_ERROR, description = "Beacon node internal error.")
      })
  @Override
  public void handle(Context ctx) throws Exception {
    try {
      final List<SubnetSubscription> request =
          jsonProvider.getObjectMapper().readValue(ctx.body(), new TypeReference<>() {});

      provider.subscribeToPersistentSubnets(request);
      ctx.status(SC_OK);
    } catch (final JsonMappingException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    } catch (Exception e) {
      ctx.status(SC_INTERNAL_SERVER_ERROR);
    }
  }
}
