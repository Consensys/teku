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

import static tech.pegasys.teku.api.ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.beaconrestapi.schema.ErrorListBadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostBlsToExecutionChanges extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/pool/bls_to_execution_changes";
  private final NodeDataProvider nodeDataProvider;
  private final ChainDataProvider chainDataProvider;

  public PostBlsToExecutionChanges(
      final NodeDataProvider provider,
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaCache) {
    super(createEndpointMetadata(schemaCache));
    this.nodeDataProvider = provider;
    this.chainDataProvider = chainDataProvider;
  }

  public PostBlsToExecutionChanges(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaCache) {
    this(dataProvider.getNodeDataProvider(), dataProvider.getChainDataProvider(), schemaCache);
  }

  private static EndpointMetadata createEndpointMetadata(final SchemaDefinitionCache schemaCache) {
    return EndpointMetadata.post(ROUTE)
        .operationId("postBlsToExecutionChange")
        .summary("Submit SignedBLSToExecutionChange object to node's pool")
        .description(
            "Submits SignedBLSToExecutionChange object to node's pool and if passes validation node MUST broadcast it"
                + " to network.")
        .tags(TAG_BEACON)
        .requestBodyType(
            DeserializableTypeDefinition.listOf(
                schemaCache
                    .getSchemaDefinition(SpecMilestone.CAPELLA)
                    .toVersionCapella()
                    .orElseThrow()
                    .getSignedBlsToExecutionChangeSchema()
                    .getJsonTypeDefinition()))
        .response(SC_OK, "BLS to execution change is stored in node and broadcast to network")
        .response(
            SC_BAD_REQUEST,
            "Errors with one or more BLS to execution changes",
            ErrorListBadRequest.getJsonTypeDefinition())
        .build();
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    if (!chainDataProvider.getMilestoneAtHead().isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      request.respondError(
          SC_BAD_REQUEST,
          "The beacon node is not currently ready to accept bls_to_execution_change operations.");
      return;
    }
    final List<SignedBlsToExecutionChange> blsToExecutionChanges = request.getRequestBody();
    final SafeFuture<List<SubmitDataError>> future =
        nodeDataProvider.postBlsToExecutionChanges(blsToExecutionChanges);

    request.respondAsync(
        future.thenApply(
            errors -> {
              if (errors.isEmpty()) {
                return AsyncApiResponse.respondWithCode(SC_OK);
              }
              final ErrorListBadRequest data =
                  ErrorListBadRequest.convert(PARTIAL_PUBLISH_FAILURE_MESSAGE, errors);
              return AsyncApiResponse.respondWithObject(SC_BAD_REQUEST, data);
            }));
  }
}
