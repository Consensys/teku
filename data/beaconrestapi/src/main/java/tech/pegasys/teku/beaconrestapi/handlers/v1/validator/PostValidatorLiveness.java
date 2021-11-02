/*
 * Copyright 2021 ConsenSys AG.
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
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.api.request.v1.validator.ValidatorLivenessRequest;
import tech.pegasys.teku.api.response.v1.validator.PostValidatorLivenessResponse;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;

public class PostValidatorLiveness extends AbstractHandler {
  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/validator/liveness";

  public final ChainDataProvider chainDataProvider;
  public final NodeDataProvider nodeDataProvider;
  public final SyncDataProvider syncDataProvider;

  public PostValidatorLiveness(
      final ChainDataProvider chainDataProvider,
      final NodeDataProvider nodeDataProvider,
      final SyncDataProvider syncDataProvider,
      final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = chainDataProvider;
    this.nodeDataProvider = nodeDataProvider;
    this.syncDataProvider = syncDataProvider;
  }

  public PostValidatorLiveness(final DataProvider provider, final JsonProvider jsonProvider) {
    this(
        provider.getChainDataProvider(),
        provider.getNodeDataProvider(),
        provider.getSyncDataProvider(),
        jsonProvider);
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get Validator Liveness",
      tags = {TAG_EXPERIMENTAL},
      requestBody =
          @OpenApiRequestBody(content = {@OpenApiContent(from = ValidatorLivenessRequest.class)}),
      description =
          "Requests the beacon node to indicate if a validator has been"
              + "    observed to be live in a given epoch. The beacon node might detect liveness by"
              + "    observing messages from the validator on the network, in the beacon chain,"
              + "    from its API or from any other source. It is important to note that the"
              + "    values returned by the beacon node are not canonical; they are best-effort"
              + "    and based upon a subjective view of the network.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = PostValidatorLivenessResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    if (!chainDataProvider.isStoreAvailable() || syncDataProvider.isSyncing()) {
      throw new ServiceUnavailableException();
    }
    try {
      final ValidatorLivenessRequest request =
          parseRequestBody(ctx.body(), ValidatorLivenessRequest.class);
      SafeFuture<Optional<PostValidatorLivenessResponse>> future =
          nodeDataProvider.getValidatorLiveness(request, chainDataProvider.getCurrentEpoch());
      handleOptionalResult(
          ctx, future, this::handleResult, this::handleError, SC_SERVICE_UNAVAILABLE);
    } catch (IllegalArgumentException ex) {
      LOG.trace("Illegal argument in PostValidatorLiveness", ex);
      ctx.status(SC_BAD_REQUEST);
      ctx.json(BadRequest.badRequest(jsonProvider, ex.getMessage()));
    }
  }

  private Optional<String> handleResult(Context ctx, final PostValidatorLivenessResponse response)
      throws JsonProcessingException {
    return Optional.of(jsonProvider.objectToJSON(response));
  }

  private SafeFuture<String> handleError(final Context ctx, final Throwable error) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof IllegalArgumentException) {
      ctx.status(SC_BAD_REQUEST);
      return SafeFuture.of(() -> BadRequest.badRequest(jsonProvider, rootCause.getMessage()));
    } else {
      return failedFuture(error);
    }
  }
}
