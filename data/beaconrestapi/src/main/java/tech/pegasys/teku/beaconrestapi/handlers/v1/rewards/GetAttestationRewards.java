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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.EPOCH_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_REWARDS;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.LONG_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.api.migrated.AttestationRewardsData;
import tech.pegasys.teku.api.migrated.GetAttestationRewardsResponse;
import tech.pegasys.teku.api.migrated.IdealAttestationReward;
import tech.pegasys.teku.api.migrated.TotalAttestationReward;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetAttestationRewards extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/beacon/rewards/attestations/{epoch}";

  private static final SerializableTypeDefinition<IdealAttestationReward> IDEAL_REWARDS_TYPE =
      SerializableTypeDefinition.object(IdealAttestationReward.class)
          .withField("effective_balance", UINT64_TYPE, IdealAttestationReward::getEffectiveBalance)
          .withField("head", LONG_TYPE, IdealAttestationReward::getHead)
          .withField("target", LONG_TYPE, IdealAttestationReward::getTarget)
          .withField("source", LONG_TYPE, IdealAttestationReward::getSource)
          .build();

  private static final SerializableTypeDefinition<TotalAttestationReward> TOTAL_REWARDS_TYPE =
      SerializableTypeDefinition.object(TotalAttestationReward.class)
          .withField("validator_index", LONG_TYPE, TotalAttestationReward::getValidatorIndex)
          .withField("head", LONG_TYPE, TotalAttestationReward::getHead)
          .withField("target", LONG_TYPE, TotalAttestationReward::getTarget)
          .withField("source", LONG_TYPE, TotalAttestationReward::getSource)
          .withOptionalField(
              "inclusion_delay", UINT64_TYPE, TotalAttestationReward::getInclusionDelay)
          .build();

  private static final SerializableTypeDefinition<AttestationRewardsData> DATA_TYPE =
      SerializableTypeDefinition.object(AttestationRewardsData.class)
          .withField(
              "ideal_rewards",
              SerializableTypeDefinition.listOf(IDEAL_REWARDS_TYPE),
              AttestationRewardsData::getIdealAttestationRewards)
          .withField(
              "total_rewards",
              SerializableTypeDefinition.listOf(TOTAL_REWARDS_TYPE),
              AttestationRewardsData::getTotalAttestationRewards)
          .build();

  public static final SerializableTypeDefinition<GetAttestationRewardsResponse> RESPONSE_TYPE =
      SerializableTypeDefinition.object(GetAttestationRewardsResponse.class)
          .name("GetAttestationRewards")
          .withField(
              EXECUTION_OPTIMISTIC,
              BOOLEAN_TYPE,
              GetAttestationRewardsResponse::isExecutionOptimistic)
          .withField(FINALIZED, BOOLEAN_TYPE, GetAttestationRewardsResponse::isFinalized)
          .withField("data", DATA_TYPE, GetAttestationRewardsResponse::getAttestationRewardsData)
          .build();

  public GetAttestationRewards() {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("getAttestationsRewards")
            .summary("Get Attestations Rewards")
            .description(
                "Retrieve attestation reward info for validators specified by array of public keys or validator index. If no array is provided, return reward info for every validator.")
            .tags(TAG_BEACON, TAG_REWARDS, TAG_EXPERIMENTAL)
            .pathParam(EPOCH_PARAMETER)
            .requestBodyType(DeserializableTypeDefinition.listOf(STRING_TYPE))
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotImplementedResponse()
            .withNotFoundResponse()
            .withInternalErrorResponse()
            .build());
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    throw new NotImplementedException();
  }
}
