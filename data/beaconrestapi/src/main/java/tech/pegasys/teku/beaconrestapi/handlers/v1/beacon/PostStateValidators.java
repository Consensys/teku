/*
 * Copyright Consensys Software Inc., 2022
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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.StatusParameter.getApplicableValidatorStatuses;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.StateValidatorData;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class PostStateValidators extends RestApiEndpoint {
  private static final DeserializableTypeDefinition<RequestBody> REQUEST_TYPE =
      DeserializableTypeDefinition.object(RequestBody.class)
          .name("PostStateValidatorsRequestBody")
          .initializer(RequestBody::new)
          .withOptionalField(
              "ids",
              DeserializableTypeDefinition.listOf(STRING_TYPE),
              RequestBody::getMaybeIds,
              RequestBody::setIds)
          .withOptionalField(
              "statuses",
              DeserializableTypeDefinition.listOf(STRING_TYPE),
              RequestBody::getMaybeStringStatuses,
              RequestBody::setStatuses)
          .build();

  private final ChainDataProvider chainDataProvider;

  public PostStateValidators(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  PostStateValidators(final ChainDataProvider provider) {
    super(
        EndpointMetadata.post(GetStateValidators.ROUTE)
            .operationId("postStateValidators")
            .summary("Get validators from state")
            .description(
                "Returns filterable list of validators with their balance, status and index.")
            .pathParam(PARAMETER_STATE_ID)
            .requestBodyType(REQUEST_TYPE)
            .tags(TAG_BEACON)
            .response(SC_OK, "Request successful", GetStateValidators.RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = provider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final Optional<RequestBody> requestBody = request.getOptionalRequestBody();
    final List<String> validators = requestBody.map(RequestBody::getIds).orElse(List.of());
    final List<StatusParameter> statusParameters =
        requestBody.map(RequestBody::getStatuses).orElse(List.of());

    final Set<ValidatorStatus> statusFilter = getApplicableValidatorStatuses(statusParameters);

    SafeFuture<Optional<ObjectAndMetaData<List<StateValidatorData>>>> future =
        chainDataProvider.getStateValidators(
            request.getPathParameter(PARAMETER_STATE_ID), validators, statusFilter);

    request.respondAsync(
        future.thenApply(
            maybeData ->
                maybeData
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  static class RequestBody {
    private List<String> ids = List.of();
    private List<StatusParameter> statuses = List.of();

    RequestBody() {}

    public RequestBody(final List<String> ids, final List<StatusParameter> statuses) {
      this.ids = ids;
      this.statuses = statuses;
    }

    public List<String> getIds() {
      return ids;
    }

    public Optional<List<String>> getMaybeIds() {
      return ids.isEmpty() ? Optional.empty() : Optional.of(ids);
    }

    public void setIds(final Optional<List<String>> ids) {
      ids.ifPresent(i -> this.ids = i);
    }

    public List<StatusParameter> getStatuses() {
      return statuses;
    }

    public Optional<List<String>> getMaybeStringStatuses() {
      return statuses.isEmpty()
          ? Optional.empty()
          : Optional.of(statuses.stream().map(Enum::name).toList());
    }

    public void setStatuses(final Optional<List<String>> statuses) {
      statuses.ifPresent(s -> this.statuses = s.stream().map(StatusParameter::parse).toList());
    }
  }
}
