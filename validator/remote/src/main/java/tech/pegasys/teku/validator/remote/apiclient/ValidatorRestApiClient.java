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

package tech.pegasys.teku.validator.remote.apiclient;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.v1.beacon.GetGenesisResponse;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.validator.GetProposerDutiesResponse;
import tech.pegasys.teku.api.response.v1.validator.PostAttesterDutiesResponse;
import tech.pegasys.teku.api.response.v1.validator.PostSyncDutiesResponse;
import tech.pegasys.teku.api.response.v1.validator.PostValidatorLivenessResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.SignedAggregateAndProof;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.api.schema.SubnetSubscription;
import tech.pegasys.teku.api.schema.altair.SignedContributionAndProof;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeContribution;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeMessage;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.api.schema.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

public interface ValidatorRestApiClient {

  Optional<GetGenesisResponse> getGenesis();

  Optional<List<ValidatorResponse>> getValidators(List<String> validatorIds);

  Optional<PostAttesterDutiesResponse> getAttestationDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices);

  Optional<GetProposerDutiesResponse> getProposerDuties(final UInt64 epoch);

  Optional<BeaconBlock> createUnsignedBlock(
      UInt64 slot, BLSSignature randaoReveal, Optional<Bytes32> graffiti, boolean blinded);

  SendSignedBlockResult sendSignedBlock(SignedBeaconBlock beaconBlock);

  Optional<PostDataFailureResponse> sendSignedAttestations(List<Attestation> attestation);

  Optional<PostDataFailureResponse> sendVoluntaryExit(SignedVoluntaryExit voluntaryExit);

  Optional<Attestation> createAggregate(UInt64 slot, Bytes32 attestationHashTreeRoot);

  Optional<PostDataFailureResponse> sendAggregateAndProofs(
      List<SignedAggregateAndProof> signedAggregateAndProof);

  void subscribeToBeaconCommittee(List<CommitteeSubscriptionRequest> requests);

  void subscribeToPersistentSubnets(Set<SubnetSubscription> subnetSubscriptions);

  Optional<PostDataFailureResponse> sendSyncCommitteeMessages(
      List<SyncCommitteeMessage> syncCommitteeMessages);

  Optional<PostSyncDutiesResponse> getSyncCommitteeDuties(
      UInt64 epoch, Collection<Integer> validatorIndices);

  void subscribeToSyncCommitteeSubnets(List<SyncCommitteeSubnetSubscription> subnetSubscriptions);

  void sendContributionAndProofs(
      final List<SignedContributionAndProof> signedContributionAndProofs);

  Optional<SyncCommitteeContribution> createSyncCommitteeContribution(
      UInt64 slot, int subcommitteeIndex, Bytes32 beaconBlockRoot);

  void prepareBeaconProposer(final List<BeaconPreparableProposer> beaconPreparableProposers);

  Optional<PostValidatorLivenessResponse> sendValidatorsLiveness(
      UInt64 epoch, List<UInt64> validatorsIndices);
}
