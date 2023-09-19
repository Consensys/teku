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

package tech.pegasys.teku.beaconrestapi.handlers.v2.debug;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_DEBUG;

import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class GetState extends tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetState {
  public static final String ROUTE = "/eth/v2/debug/beacon/states/{state_id}";

  public GetState(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetState(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(chainDataProvider, getEndpointMetaData(schemaDefinitionCache));
  }

  private static EndpointMetadata getEndpointMetaData(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.get(ROUTE)
        .operationId("getStateDebugV2")
        .summary("Get full BeaconState object")
        .description(
            "Returns full BeaconState object for given state_id.\n\n"
                + "Use Accept header to select `application/octet-stream` if SSZ response type is required.")
        .tags(TAG_DEBUG)
        .deprecated(true)
        .pathParam(PARAMETER_STATE_ID)
        .response(
            SC_OK,
            "Request successful",
            getResponseType(schemaDefinitionCache, TAG_DEBUG),
            sszResponseType())
        .withNotFoundResponse()
        .build();
  }
}
