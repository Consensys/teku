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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.EPOCH_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler.routeWithBracedParameters;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.StateSyncCommitteesData;
import tech.pegasys.teku.api.response.v1.beacon.GetStateSyncCommitteesResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetStateSyncCommittees extends MigratingEndpointAdapter {
  private static final String OAPI_ROUTE = "/eth/v1/beacon/states/:state_id/sync_committees";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);
  private final ChainDataProvider chainDataProvider;

  private static final ObjectAndMetaData<StateSyncCommitteesData> EMPTY_RESPONSE =
      new ObjectAndMetaData<>(
          new StateSyncCommitteesData(List.of(), List.of()), SpecMilestone.PHASE0, false, true);

  private static final SerializableTypeDefinition<StateSyncCommitteesData> DATA_TYPE =
      SerializableTypeDefinition.object(StateSyncCommitteesData.class)
          .withField("validators", listOf(UINT64_TYPE), StateSyncCommitteesData::getValidators)
          .withField(
              "validator_aggregates",
              listOf(listOf(UINT64_TYPE)),
              StateSyncCommitteesData::getValidatorAggregates)
          .build();

  private static final SerializableTypeDefinition<ObjectAndMetaData<StateSyncCommitteesData>>
      RESPONSE_TYPE =
          SerializableTypeDefinition.<ObjectAndMetaData<StateSyncCommitteesData>>object()
              .name("GetEpochSyncCommitteesResponse")
              .withField(
                  EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
              .withField("data", DATA_TYPE, ObjectAndMetaData::getData)
              .build();

  public GetStateSyncCommittees(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetStateSyncCommittees(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStateSyncCommittees")
            .summary("Get sync committees")
            .description("Retrieves the sync committees for the given state.")
            .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
            .pathParam(PARAMETER_STATE_ID)
            .queryParam(EPOCH_PARAMETER)
            .response(HttpStatusCodes.SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      summary = "Get sync committees",
      tags = {TAG_BEACON, TAG_VALIDATOR_REQUIRED},
      description = "Retrieves the sync committees for the given state.",
      pathParams = {
        @OpenApiParam(name = PARAM_STATE_ID, description = PARAM_STATE_ID_DESCRIPTION),
      },
      queryParams = {@OpenApiParam(name = EPOCH, description = EPOCH_QUERY_DESCRIPTION)},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetStateSyncCommitteesResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final Optional<UInt64> epoch = request.getOptionalQueryParameter(EPOCH_PARAMETER);

    if (!chainDataProvider.stateParameterMaySupportAltair(
        request.getPathParameter(PARAMETER_STATE_ID))) {
      request.respondOk(EMPTY_RESPONSE);
      return;
    }

    final SafeFuture<Optional<ObjectAndMetaData<StateSyncCommitteesData>>> future =
        chainDataProvider.getStateSyncCommittees(
            request.getPathParameter(PARAMETER_STATE_ID), epoch);

    request.respondAsync(
        future
            .thenApply(
                maybeStateSyncCommitteesAndMetaData -> {
                  if (maybeStateSyncCommitteesAndMetaData.isEmpty()) {
                    return AsyncApiResponse.respondNotFound();
                  }

                  return AsyncApiResponse.respondOk(maybeStateSyncCommitteesAndMetaData.get());
                })
            .exceptionallyCompose(
                error -> {
                  final Throwable rootCause = Throwables.getRootCause(error);

                  if (rootCause instanceof IllegalArgumentException
                      || rootCause instanceof IllegalStateException) {
                    return SafeFuture.completedFuture(
                        AsyncApiResponse.respondWithError(SC_BAD_REQUEST, error.getMessage()));
                  }

                  return failedFuture(error);
                }));
  }
}
