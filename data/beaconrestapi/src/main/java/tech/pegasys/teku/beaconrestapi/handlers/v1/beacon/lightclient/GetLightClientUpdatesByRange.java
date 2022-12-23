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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.lightclient;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.COUNT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.START_PERIOD_PARAMETER;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class GetLightClientUpdatesByRange extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/light_client/updates";

  public GetLightClientUpdatesByRange(final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getLightClientUpdatesByRange")
            .summary("Get `LightClientUpdate` instances in a requested sync committee period range")
            .description(
                "Requests the [`LightClientUpdate`](https://github.com/ethereum/consensus-specs/blob/v1.2.0-rc.3/specs/altair/light-client/sync-protocol.md#lightclientupdate) instances in the sync committee period range `[start_period, start_period + count)`, leading up to the current head sync committee period as selected by fork choice. Depending on the `Accept` header they can be returned either as JSON or SSZ-serialized bytes.")
            .tags(TAG_BEACON, TAG_EXPERIMENTAL)
            .queryParamRequired(START_PERIOD_PARAMETER)
            .queryParamRequired(COUNT_PARAMETER)
            .response(SC_OK, "Request successful", getResponseType(schemaDefinitionCache))
            .withNotAcceptedResponse()
            .withNotImplementedResponse()
            .build());
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    request.respondError(501, "Not implemented");
  }

  private static SerializableTypeDefinition<List<ObjectAndMetaData<LightClientUpdate>>>
      getResponseType(SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableTypeDefinition<LightClientUpdate> lightClientUpdateType =
        SchemaDefinitionsAltair.required(
                schemaDefinitionCache.getSchemaDefinition(SpecMilestone.ALTAIR))
            .getLightClientUpdateSchema()
            .getJsonTypeDefinition();

    final SerializableTypeDefinition<ObjectAndMetaData<LightClientUpdate>>
        lightClientUpdateObjectType =
            SerializableTypeDefinition.<ObjectAndMetaData<LightClientUpdate>>object()
                .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
                .withField("data", lightClientUpdateType, ObjectAndMetaData::getData)
                .build();

    return SerializableTypeDefinition.listOf(lightClientUpdateObjectType);
  }
}
