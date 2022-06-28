/*
 * Copyright ConsenSys Software Inc., 2022
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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class PostVoluntaryExit extends MigratingEndpointAdapter {
  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/beacon/pool/voluntary_exits";
  private final NodeDataProvider nodeDataProvider;

  public PostVoluntaryExit(final DataProvider dataProvider) {
    this(dataProvider.getNodeDataProvider());
  }

  public PostVoluntaryExit(final NodeDataProvider provider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("postVoluntaryExit")
            .summary("Submit signed voluntary exit")
            .description(
                "Submits signed voluntary exit object to node's pool and if it passes validation node MUST broadcast it to network.")
            .tags(TAG_BEACON)
            .requestBodyType(SignedVoluntaryExit.SSZ_SCHEMA.getJsonTypeDefinition())
            .response(
                SC_OK,
                "Signed voluntary exit has been successfully validated, added to the pool, and broadcast.")
            .build());
    this.nodeDataProvider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Submit signed voluntary exit",
      tags = {TAG_BEACON},
      description =
          "Submits signed voluntary exit object to node's pool and if it passes validation node MUST broadcast it to network.",
      requestBody =
          @OpenApiRequestBody(
              content = {
                @OpenApiContent(from = tech.pegasys.teku.api.schema.SignedVoluntaryExit.class)
              }),
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description =
                "Signed voluntary exit has been successfully validated, added to the pool, and broadcast."),
        @OpenApiResponse(
            status = RES_BAD_REQUEST,
            description = "Invalid voluntary exit, it will never pass validation so it's rejected"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final SignedVoluntaryExit exit = request.getRequestBody();
    final SafeFuture<InternalValidationResult> future = nodeDataProvider.postVoluntaryExit(exit);

    request.respondAsync(
        future.thenApply(
            internalValidationResult -> {
              if (internalValidationResult.code().equals(ValidationResultCode.IGNORE)
                  || internalValidationResult.code().equals(ValidationResultCode.REJECT)) {
                LOG.debug(
                    "Voluntary exit failed status {} {}",
                    internalValidationResult.code(),
                    internalValidationResult.getDescription().orElse(""));
                return AsyncApiResponse.respondWithError(
                    SC_BAD_REQUEST,
                    internalValidationResult
                        .getDescription()
                        .orElse(
                            "Invalid voluntary exit, it will never pass validation so it's rejected"));
              }
              return AsyncApiResponse.respondWithCode(SC_OK);
            }));
  }
}
