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

package tech.pegasys.teku.beaconrestapi.handlers.v2.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import com.fasterxml.jackson.core.JsonProcessingException;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class PostAttesterSlashingV2 extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v2/beacon/pool/attester_slashings";
  private final NodeDataProvider nodeDataProvider;
  private final SchemaDefinitionCache schemaDefinitionCache;

  public PostAttesterSlashingV2(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getNodeDataProvider(), schemaDefinitionCache);
  }

  public PostAttesterSlashingV2(
      final NodeDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("submitPoolAttesterSlashingsV2")
            .summary("Submit AttesterSlashing object to node's pool")
            .description(
                "Submits AttesterSlashing object to node's pool. Upon successful validation the node MUST broadcast it to network.")
            .tags(TAG_BEACON)
            .requestBodyType(getRequestType(schemaDefinitionCache))
            .headerRequired(
                ETH_CONSENSUS_VERSION_TYPE.withDescription(
                    "Version of the attester slashing being submitted."))
            .response(SC_OK, "Success")
            .build());
    this.nodeDataProvider = provider;
    this.schemaDefinitionCache = schemaDefinitionCache;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    handleAttesterSlashingRequest(request, nodeDataProvider, schemaDefinitionCache);
  }

  public static void handleAttesterSlashingRequest(
      final RestApiRequest request, final NodeDataProvider nodeDataProvider, final SchemaDefinitionCache schemaDefinitionCache)
      throws JsonProcessingException {
    final AttesterSlashing attesterSlashing = request.getRequestBody();
    final SafeFuture<InternalValidationResult> future =
        nodeDataProvider.postAttesterSlashing(attesterSlashing);

    request.respondAsync(
        future.thenApply(
            internalValidationResult -> {
              if (internalValidationResult.code().equals(ValidationResultCode.IGNORE)
                  || internalValidationResult.code().equals(ValidationResultCode.REJECT)) {
                return AsyncApiResponse.respondWithError(
                    SC_BAD_REQUEST,
                    internalValidationResult
                        .getDescription()
                        .orElse(
                            "Invalid attester slashing, it will never pass validation so it's rejected."));
              } else {
                request.header(
                        HEADER_CONSENSUS_VERSION,
                        Version.fromMilestone(getMilestoneFromAttesterSlashing(attesterSlashing, schemaDefinitionCache)).name());
                return AsyncApiResponse.respondWithCode(SC_OK);
              }
            }));
  }

  private static SpecMilestone getMilestoneFromAttesterSlashing(final AttesterSlashing attesterSlashing, final SchemaDefinitionCache schemaDefinitionCache) {
    return schemaDefinitionCache.milestoneAtSlot(attesterSlashing.getAttestation1().getData().getSlot());
  }

  private static DeserializableTypeDefinition<AttesterSlashing> getRequestType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final AttesterSlashing.AttesterSlashingSchema attesterSlashingSchema =
        schemaDefinitionCache
            .getSchemaDefinition(SpecMilestone.ELECTRA)
            .getAttesterSlashingSchema();

    return attesterSlashingSchema.getJsonTypeDefinition();
  }
}
