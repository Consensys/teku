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

package tech.pegasys.teku.validator.api;

import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlindedBlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;

public interface ValidatorApiChannel extends ChannelInterface {
  int UNKNOWN_VALIDATOR_ID = -1;

  SafeFuture<Optional<GenesisData>> getGenesisData();

  SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(Collection<BLSPublicKey> publicKeys);

  SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      Collection<BLSPublicKey> validatorIdentifiers);

  SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      UInt64 epoch, IntCollection validatorIndices);

  SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      UInt64 epoch, IntCollection validatorIndices);

  SafeFuture<Optional<ProposerDuties>> getProposerDuties(UInt64 epoch);

  SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      UInt64 slot, BLSSignature randaoReveal, Optional<Bytes32> graffiti, boolean blinded);

  SafeFuture<Optional<BlindedBlockContents>> createUnsignedBlindedBlockContents(
      UInt64 slot, BLSSignature randaoReveal, Optional<Bytes32> graffiti);

  SafeFuture<Optional<BlockContents>> createUnsignedBlockContents(
      UInt64 slot, BLSSignature randaoReveal, Optional<Bytes32> graffiti);

  SafeFuture<Optional<AttestationData>> createAttestationData(UInt64 slot, int committeeIndex);

  SafeFuture<Optional<Attestation>> createAggregate(UInt64 slot, Bytes32 attestationHashTreeRoot);

  SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      UInt64 slot, int subcommitteeIndex, Bytes32 beaconBlockRoot);

  SafeFuture<Void> subscribeToBeaconCommittee(List<CommitteeSubscriptionRequest> requests);

  SafeFuture<Void> subscribeToSyncCommitteeSubnets(
      Collection<SyncCommitteeSubnetSubscription> subscriptions);

  SafeFuture<Void> subscribeToPersistentSubnets(Set<SubnetSubscription> subnetSubscriptions);

  SafeFuture<List<SubmitDataError>> sendSignedAttestations(List<Attestation> attestations);

  SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      List<SignedAggregateAndProof> aggregateAndProofs);

  SafeFuture<SendSignedBlockResult> sendSignedBlock(SignedBeaconBlock block);

  SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      List<SyncCommitteeMessage> syncCommitteeMessages);

  SafeFuture<Void> sendSignedContributionAndProofs(
      Collection<SignedContributionAndProof> signedContributionAndProofs);

  SafeFuture<Void> prepareBeaconProposer(
      Collection<BeaconPreparableProposer> beaconPreparableProposers);

  SafeFuture<Void> registerValidators(SszList<SignedValidatorRegistration> validatorRegistrations);

  SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> getValidatorsLiveness(
      List<UInt64> validatorIndices, UInt64 epoch);
}
