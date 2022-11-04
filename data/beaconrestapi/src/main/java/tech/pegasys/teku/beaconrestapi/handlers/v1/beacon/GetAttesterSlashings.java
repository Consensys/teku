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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import java.util.List;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;

public class GetAttesterSlashings extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/pool/attester_slashings";
  private final NodeDataProvider nodeDataProvider;

  public GetAttesterSlashings(final DataProvider dataProvider, Spec spec) {
    this(dataProvider.getNodeDataProvider(), spec);
  }

  GetAttesterSlashings(final NodeDataProvider provider, Spec spec) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getAttesterSlashings")
            .summary("Get Attester Slashings")
            .description(
                "Retrieves attester slashings known by the node but not necessarily incorporated into any block.")
            .tags(TAG_BEACON)
            .response(SC_OK, "Request successful", getResponseType(spec))
            .build());
    this.nodeDataProvider = provider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    List<AttesterSlashing> attesterSlashings = nodeDataProvider.getAttesterSlashings();
    request.respondOk(attesterSlashings);
  }

  private static SerializableTypeDefinition<List<AttesterSlashing>> getResponseType(Spec spec) {
    final IndexedAttestation.IndexedAttestationSchema indexedAttestationSchema =
        new IndexedAttestation.IndexedAttestationSchema(spec.getGenesisSpecConfig());
    final AttesterSlashing.AttesterSlashingSchema attesterSlashingSchema =
        new AttesterSlashing.AttesterSlashingSchema(indexedAttestationSchema);

    return SerializableTypeDefinition.<List<AttesterSlashing>>object()
        .name("GetPoolAttesterSlashingsResponse")
        .withField(
            "data", listOf(attesterSlashingSchema.getJsonTypeDefinition()), Function.identity())
        .build();
  }
}
