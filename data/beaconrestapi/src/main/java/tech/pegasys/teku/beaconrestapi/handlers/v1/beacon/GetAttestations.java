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

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetAttestationsResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class GetAttestations extends AbstractHandler {
  public static final String ROUTE = "/eth/v1/beacon/pool/attestations";
  private final NodeDataProvider nodeDataProvider;

  public GetAttestations(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.nodeDataProvider = dataProvider.getNodeDataProvider();
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get attestations",
      tags = {TAG_BEACON},
      description =
          "Retrieves attestations known by the node but not necessarily incorporated into any block.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetAttestationsResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    Map<String, List<String>> queryParamMap = ctx.queryParamMap();
    Optional<UInt64> maybeSlot =
        SingleQueryParameterUtils.getParameterValueAsUInt64IfPresent(queryParamMap, SLOT);
    Optional<UInt64> maybeCommitteeIndex =
        SingleQueryParameterUtils.getParameterValueAsUInt64IfPresent(
            ctx.queryParamMap(), COMMITTEE_INDEX);
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    List<Attestation> attestations =
        nodeDataProvider.getAttestations(maybeSlot, maybeCommitteeIndex);
    ctx.json(jsonProvider.objectToJSON(new GetAttestationsResponse(attestations)));
  }
}
