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
import io.javalin.http.Header;
import java.util.List;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class GetBlsToExecutionChanges extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/beacon/pool/bls_to_execution_changes";

  private final NodeDataProvider nodeDataProvider;

  public GetBlsToExecutionChanges(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaCache) {
    this(dataProvider.getNodeDataProvider(), schemaCache);
  }

  public GetBlsToExecutionChanges(
      final NodeDataProvider nodeDataProvider, final SchemaDefinitionCache schemaCache) {
    super(createEndpointMetadata(schemaCache));
    this.nodeDataProvider = nodeDataProvider;
  }

  private static EndpointMetadata createEndpointMetadata(final SchemaDefinitionCache schemaCache) {
    final SerializableTypeDefinition<List<SignedBlsToExecutionChange>> responseType =
        SerializableTypeDefinition.<List<SignedBlsToExecutionChange>>object()
            .name("GetBlsToExecutionChangeResponse")
            .withField(
                "data",
                listOf(
                    schemaCache
                        .getSchemaDefinition(SpecMilestone.CAPELLA)
                        .toVersionCapella()
                        .orElseThrow()
                        .getSignedBlsToExecutionChangeSchema()
                        .getJsonTypeDefinition()),
                Function.identity())
            .build();

    return EndpointMetadata.get(ROUTE)
        .operationId("getBlsToExecutionChanges")
        .summary("Get SignedBLSToExecutionChange from operations pool")
        .description(
            "Retrieves BLS to execution changes known by the node but not necessarily incorporated into any block")
        .tags(TAG_BEACON)
        .response(SC_OK, "Successful response", responseType)
        .build();
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    request.respondOk(nodeDataProvider.getBlsToExecutionChanges());
  }
}
