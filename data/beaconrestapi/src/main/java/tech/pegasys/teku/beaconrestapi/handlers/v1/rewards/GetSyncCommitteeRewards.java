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
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.LONG_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetSyncCommitteeRewards extends RestApiEndpoint {
  private static final String ROUTE = "/eth/v1/beacon/rewards/sync_committee/{block_id}";

  private static final SerializableTypeDefinition<Map.Entry<Integer, Long>> DATA_TYPE =
      SerializableTypeDefinition.<Map.Entry<Integer, Long>>object()
          .withField("validator_index", INTEGER_TYPE, Map.Entry::getKey)
          .withField("reward", LONG_TYPE, Map.Entry::getValue)
          .build();

  private static final SerializableTypeDefinition<SyncCommitteeRewardData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(SyncCommitteeRewardData.class)
          .name("GetSyncCommitteeRewards")
          .withField(
              EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, SyncCommitteeRewardData::isExecutionOptimistic)
          .withField(FINALIZED, BOOLEAN_TYPE, SyncCommitteeRewardData::isFinalized)
          .withField(
              "data",
              SerializableTypeDefinition.listOf(DATA_TYPE),
              SyncCommitteeRewardData::getRewardData)
          .build();

  public GetSyncCommitteeRewards() {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("getSyncCommitteeRewards")
            .summary("Get Sync Committee Rewards")
            .description(
                "Retrieves rewards info for sync committee members specified by array of public keys "
                    + "or validator index. If no array is provided, return reward info for every committee member.")
            .tags(TAG_BEACON, TAG_REWARDS)
            .pathParam(PARAMETER_BLOCK_ID)
            .requestBodyType(DeserializableTypeDefinition.listOf(STRING_TYPE))
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
