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

import static java.util.Arrays.asList;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_VALIDATOR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.request.v1.validator.BeaconCommitteeSubscriptionRequest;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.provider.JsonProvider;

public class PostSubscribeToBeaconCommitteeSubnet extends AbstractHandler {

  public static final String ROUTE = "/eth/v1/validator/beacon_committee_subscriptions";

  private final ValidatorDataProvider provider;

  public PostSubscribeToBeaconCommitteeSubnet(
      final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getValidatorDataProvider(), jsonProvider);
  }

  public PostSubscribeToBeaconCommitteeSubnet(
      final ValidatorDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Subscribe to a committee subnet",
      tags = {TAG_V1_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      requestBody =
          @OpenApiRequestBody(
              content = {@OpenApiContent(from = BeaconCommitteeSubscriptionRequest[].class)}),
      description =
          "After Beacon node receives this request, search using discv5 for peers related to this subnet and replace current peers with those ones if necessary If validator is_aggregator, beacon node must:\n"
              + "- announce subnet topic subscription on gossipsub\n"
              + "- aggregate attestations received on that subnet\n",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description =
                "Slot signature is valid and beacon node has prepared the attestation subnet. Note that, there is no guarantee the node will find peers for the subnet"),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid request syntax."),
        @OpenApiResponse(status = RES_INTERNAL_ERROR, description = "Beacon node internal error."),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    try {
      final BeaconCommitteeSubscriptionRequest[] request =
          jsonProvider.jsonToObject(ctx.body(), BeaconCommitteeSubscriptionRequest[].class);

      provider.subscribeToBeaconCommittee(asList(request));
      ctx.status(SC_OK);
    } catch (final JsonMappingException e) {
      ctx.status(SC_BAD_REQUEST);
      ctx.result(BadRequest.badRequest(jsonProvider, e.getMessage()));
    }
  }
}
