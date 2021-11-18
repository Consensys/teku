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

package tech.pegasys.teku.validator.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.merge.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;

public interface ValidatorApiChannel extends ChannelInterface {
  int UKNOWN_VALIDATOR_ID = -1;

  SafeFuture<Optional<GenesisData>> getGenesisData();

  SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final Collection<BLSPublicKey> publicKeys);

  SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      final Collection<BLSPublicKey> validatorIdentifiers);

  SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices);

  SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices);

  SafeFuture<Optional<ProposerDuties>> getProposerDuties(final UInt64 epoch);

  SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      UInt64 slot, BLSSignature randaoReveal, Optional<Bytes32> graffiti);

  SafeFuture<Optional<AttestationData>> createAttestationData(UInt64 slot, int committeeIndex);

  SafeFuture<Optional<Attestation>> createAggregate(UInt64 slot, Bytes32 attestationHashTreeRoot);

  SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      UInt64 slot, int subcommitteeIndex, Bytes32 beaconBlockRoot);

  void subscribeToBeaconCommittee(List<CommitteeSubscriptionRequest> requests);

  void subscribeToSyncCommitteeSubnets(Collection<SyncCommitteeSubnetSubscription> subscriptions);

  void subscribeToPersistentSubnets(Set<SubnetSubscription> subnetSubscriptions);

  SafeFuture<List<SubmitDataError>> sendSignedAttestations(List<Attestation> attestations);

  SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      List<SignedAggregateAndProof> aggregateAndProofs);

  SafeFuture<SendSignedBlockResult> sendSignedBlock(SignedBeaconBlock block);

  SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      List<SyncCommitteeMessage> syncCommitteeMessages);

  SafeFuture<Void> sendSignedContributionAndProofs(
      Collection<SignedContributionAndProof> signedContributionAndProofs);

  void prepareBeaconProposer(Collection<BeaconPreparableProposer> beaconPreparableProposers);
}
