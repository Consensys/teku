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

package tech.pegasys.teku.beaconrestapi.handlers.v1.debug;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_DEBUG;

import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;

public class GetChainHeadsV1 extends GetChainHeads {
  public static final String ROUTE = "/eth/v1/debug/beacon/heads";

  private static final SerializableTypeDefinition<ProtoNodeData> CHAIN_HEAD_TYPE_V1 =
      SerializableTypeDefinition.object(ProtoNodeData.class)
          .name("ChainHead")
          .withField("slot", CoreTypes.UINT64_TYPE, ProtoNodeData::getSlot)
          .withField("root", CoreTypes.BYTES32_TYPE, ProtoNodeData::getRoot)
          .build();

  public GetChainHeadsV1(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetChainHeadsV1(final ChainDataProvider chainDataProvider) {
    super(
        chainDataProvider,
        EndpointMetadata.get(ROUTE)
            .operationId("getDebugChainHeads")
            .summary("Get fork choice leaves")
            .description(
                "Retrieves all possible chain heads (leaves of fork choice tree).\n\n"
                    + "Deprecated - use `/eth/v2/debug/beacon/heads")
            .tags(TAG_DEBUG)
            .deprecated(true)
            .response(SC_OK, "Success", responseType(CHAIN_HEAD_TYPE_V1))
            .build());
  }
}
