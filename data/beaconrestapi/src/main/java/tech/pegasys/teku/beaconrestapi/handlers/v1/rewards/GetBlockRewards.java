/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.rewards;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_REWARDS;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.api.migrated.BlockRewardData;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetBlockRewards extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/rewards/blocks/{block_id}";

  private static final SerializableTypeDefinition<BlockRewardData> DATA_TYPE =
      SerializableTypeDefinition.object(BlockRewardData.class)
          .withField("proposer_index", UINT64_TYPE, BlockRewardData::getProposerIndex)
          .withField("total", UINT64_TYPE, BlockRewardData::getTotal)
          .withField("attestations", UINT64_TYPE, BlockRewardData::getAttestations)
          .withField("sync_aggregate", UINT64_TYPE, BlockRewardData::getSyncAggregate)
          .withField("proposer_slashings", UINT64_TYPE, BlockRewardData::getProposerSlashings)
          .withField("attester_slashings", UINT64_TYPE, BlockRewardData::getAttesterSlashings)
          .build();

  private static final SerializableTypeDefinition<ObjectAndMetaData<BlockRewardData>>
      RESPONSE_TYPE =
          SerializableTypeDefinition.<ObjectAndMetaData<BlockRewardData>>object()
              .name("GetBlockRewards")
              .withField(
                  EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
              .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
              .withField("data", DATA_TYPE, ObjectAndMetaData::getData)
              .build();

  public GetBlockRewards() {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getBlockRewards")
            .summary("Get Block Rewards")
            .description("Retrieve block reward info for a single block.")
            .tags(TAG_BEACON, TAG_REWARDS)
            .pathParam(PARAMETER_BLOCK_ID)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .withInternalErrorResponse()
            .withNotImplementedResponse()
            .build());
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    throw new NotImplementedException();
  }
}
