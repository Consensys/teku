/*
 * Copyright 2020 ConsenSys AG.
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
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.validator.GetProposerDutiesResponse;
import tech.pegasys.teku.api.response.v1.validator.PostAttesterDutiesResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.AttestationData;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.SignedAggregateAndProof;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.api.schema.SubnetSubscription;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

public interface ValidatorRestApiClient {

  Optional<Fork> getFork();

  Optional<GetGenesisResponse> getGenesis();

  Optional<List<ValidatorResponse>> getValidators(List<String> validatorIds);

  Optional<PostAttesterDutiesResponse> getAttestationDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndexes);

  Optional<GetProposerDutiesResponse> getProposerDuties(final UInt64 epoch);

  Optional<BeaconBlock> createUnsignedBlock(
      UInt64 slot, BLSSignature randaoReveal, Optional<Bytes32> graffiti);

  SendSignedBlockResult sendSignedBlock(SignedBeaconBlock beaconBlock);

  Optional<Attestation> createUnsignedAttestation(UInt64 slot, int committeeIndex);

  Optional<AttestationData> createAttestationData(UInt64 slot, int committeeIndex);

  void sendSignedAttestation(Attestation attestation);

  void sendVoluntaryExit(SignedVoluntaryExit voluntaryExit);

  Optional<Attestation> createAggregate(UInt64 slot, Bytes32 attestationHashTreeRoot);

  void sendAggregateAndProofs(List<SignedAggregateAndProof> signedAggregateAndProof);

  void subscribeToBeaconCommittee(List<CommitteeSubscriptionRequest> requests);

  void subscribeToPersistentSubnets(Set<SubnetSubscription> subnetSubscriptions);
}
