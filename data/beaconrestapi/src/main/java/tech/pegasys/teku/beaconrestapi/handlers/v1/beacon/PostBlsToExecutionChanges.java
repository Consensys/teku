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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class PostBlsToExecutionChanges extends RestApiEndpoint {

  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/beacon/pool/bls_to_execution_changes";
  private final NodeDataProvider nodeDataProvider;

  public PostBlsToExecutionChanges(
      final NodeDataProvider provider, final SchemaDefinitionCache schemaCache) {
    super(createEndpointMetadata(schemaCache));
    this.nodeDataProvider = provider;
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
            schemaCache
                .getSchemaDefinition(SpecMilestone.CAPELLA)
                .toVersionCapella()
                .orElseThrow()
                .getSignedBlsToExecutionChangeSchema()
                .getJsonTypeDefinition())
        .response(SC_OK, "BLS to execution change is stored in node and broadcasted to network")
        .build();
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final SignedBlsToExecutionChange blsToExecutionChange = request.getRequestBody();
    final SafeFuture<InternalValidationResult> future =
        nodeDataProvider.postBlsToExecutionChange(blsToExecutionChange);

    request.respondAsync(
        future.thenApply(
            internalValidationResult -> {
              if (internalValidationResult.code().equals(ValidationResultCode.IGNORE)
                  || internalValidationResult.code().equals(ValidationResultCode.REJECT)) {
                LOG.debug(
                    "BlsToExecutionChange failed status {} {}",
                    internalValidationResult.code(),
                    internalValidationResult.getDescription().orElse(""));
                return AsyncApiResponse.respondWithError(
                    SC_BAD_REQUEST,
                    internalValidationResult
                        .getDescription()
                        .orElse(
                            "Invalid BlsToExecutionChange, it will never pass validation so it's rejected"));
              }
              return AsyncApiResponse.respondWithCode(SC_OK);
            }));
  }
}
