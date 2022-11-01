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
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class PostAttesterSlashing extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/pool/attester_slashings";
  private final NodeDataProvider nodeDataProvider;

  public PostAttesterSlashing(final DataProvider dataProvider, final Spec spec) {
    this(dataProvider.getNodeDataProvider(), spec);
  }

  public PostAttesterSlashing(final NodeDataProvider provider, final Spec spec) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("postAttesterSlashing")
            .summary("Submit attester slashing object")
            .description(
                "Submits attester slashing object to node's pool and if passes validation node MUST broadcast it to network.")
            .tags(TAG_BEACON)
            .requestBodyType(getRequestType(spec.getGenesisSpecConfig()))
            .response(SC_OK, "Success")
            .build());
    this.nodeDataProvider = provider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
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
                return AsyncApiResponse.respondWithCode(SC_OK);
              }
            }));
  }

  private static DeserializableTypeDefinition<AttesterSlashing> getRequestType(
      SpecConfig specConfig) {
    final IndexedAttestation.IndexedAttestationSchema indexedAttestationSchema =
        new IndexedAttestation.IndexedAttestationSchema(specConfig);
    final AttesterSlashing.AttesterSlashingSchema attesterSlashingSchema =
        new AttesterSlashing.AttesterSlashingSchema(indexedAttestationSchema);

    return attesterSlashingSchema.getJsonTypeDefinition();
  }
}
