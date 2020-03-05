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

package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_BEACON;
import static tech.pegasys.artemis.beaconrestapi.RestApiUtils.validateQueryParameter;

import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.beaconrestapi.schema.CommitteesResponse;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;

public class BeaconCommitteesHandler implements Handler {

  public static final String ROUTE = "/beacon/committees";

  private final CombinedChainDataClient combinedClient;
  private final JsonProvider jsonProvider;

  public BeaconCommitteesHandler(
      CombinedChainDataClient combinedClient, JsonProvider jsonProvider) {
    this.combinedClient = combinedClient;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get the committee assignments for a given epoch.",
      tags = {TAG_BEACON},
      queryParams = {
        @OpenApiParam(
            name = EPOCH,
            description = "The epoch for which committees will be returned.",
            required = true),
      },
      description = "Request a list of the committee assignments for a given epoch.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = CommitteesResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Missing a query parameter"),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    try {
      if (!combinedClient.isStoreAvailable()) {
        ctx.status(SC_NO_CONTENT);
        return;
      }
      SafeFuture<List<CommitteeAssignment>> future =
          getCommitteesAtEpoch(validateQueryParameter(ctx.queryParamMap(), EPOCH));
      ctx.result(
          future.thenApplyChecked(
              result -> jsonProvider.objectToJSON(CommitteesResponse.fromList(result))));
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private SafeFuture<List<CommitteeAssignment>> getCommitteesAtEpoch(final String epochString) {
    final UnsignedLong epoch = UnsignedLong.valueOf(epochString);

    return combinedClient.getCommitteeAssignmentAtEpoch(epoch);
  }
}
