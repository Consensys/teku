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

package tech.pegasys.teku.validator.remote.apiclient;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.response.v1.validator.PostValidatorLivenessResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.SignedAggregateAndProof;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeMessage;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface ValidatorRestApiClient {

  Optional<PostDataFailureResponse> sendSignedAttestations(List<Attestation> attestation);

  Optional<PostDataFailureResponse> sendAggregateAndProofs(
      List<SignedAggregateAndProof> signedAggregateAndProof);

  Optional<PostDataFailureResponse> sendSyncCommitteeMessages(
      List<SyncCommitteeMessage> syncCommitteeMessages);

  //  void prepareBeaconProposer(final List<BeaconPreparableProposer> beaconPreparableProposers);

  Optional<PostValidatorLivenessResponse> sendValidatorsLiveness(
      UInt64 epoch, List<UInt64> validatorsIndices);
}
