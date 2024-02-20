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
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;

public interface ValidatorApiChannel extends ChannelInterface {
  ValidatorApiChannel NO_OP =
      new ValidatorApiChannel() {
        @Override
        public SafeFuture<Optional<GenesisData>> getGenesisData() {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
            Collection<BLSPublicKey> publicKeys) {
          return SafeFuture.completedFuture(Map.of());
        }

        @Override
        public SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
            Collection<BLSPublicKey> validatorIdentifiers) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
            UInt64 epoch, IntCollection validatorIndices) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
            UInt64 epoch, IntCollection validatorIndices) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<ProposerDuties>> getProposerDuties(UInt64 epoch) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<BlockContainerAndMetaData>> createUnsignedBlock(
            UInt64 slot,
            BLSSignature randaoReveal,
            Optional<Bytes32> graffiti,
            Optional<Boolean> requestedBlinded,
            Optional<UInt64> requestedBuilderBoostFactor) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<AttestationData>> createAttestationData(
            UInt64 slot, int committeeIndex) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<Attestation>> createAggregate(
            UInt64 slot, Bytes32 attestationHashTreeRoot) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
            UInt64 slot, int subcommitteeIndex, Bytes32 beaconBlockRoot) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Void> subscribeToBeaconCommittee(
            List<CommitteeSubscriptionRequest> requests) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<Void> subscribeToSyncCommitteeSubnets(
            Collection<SyncCommitteeSubnetSubscription> subscriptions) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<Void> subscribeToPersistentSubnets(
            Set<SubnetSubscription> subnetSubscriptions) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<List<SubmitDataError>> sendSignedAttestations(
            List<Attestation> attestations) {
          return SafeFuture.completedFuture(List.of());
        }

        @Override
        public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
            List<SignedAggregateAndProof> aggregateAndProofs) {
          return SafeFuture.completedFuture(List.of());
        }

        @Override
        public SafeFuture<SendSignedBlockResult> sendSignedBlock(
            SignedBlockContainer blockContainer,
            BroadcastValidationLevel broadcastValidationLevel) {
          return SafeFuture.completedFuture(SendSignedBlockResult.rejected("NO OP Implementation"));
        }

        @Override
        public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
            List<SyncCommitteeMessage> syncCommitteeMessages) {
          return SafeFuture.completedFuture(List.of());
        }

        @Override
        public SafeFuture<Void> sendSignedContributionAndProofs(
            Collection<SignedContributionAndProof> signedContributionAndProofs) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<Void> prepareBeaconProposer(
            Collection<BeaconPreparableProposer> beaconPreparableProposers) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<Void> registerValidators(
            SszList<SignedValidatorRegistration> validatorRegistrations) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> getValidatorsLiveness(
            List<UInt64> validatorIndices, UInt64 epoch) {
          return SafeFuture.completedFuture(Optional.empty());
        }
      };

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

  /**
   * @param requestedBlinded can be removed once block creation V2 APIs are removed in favour of V3
   *     only
   */
  SafeFuture<Optional<BlockContainerAndMetaData>> createUnsignedBlock(
      UInt64 slot,
      BLSSignature randaoReveal,
      Optional<Bytes32> graffiti,
      Optional<Boolean> requestedBlinded,
      Optional<UInt64> requestedBuilderBoostFactor);

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

  SafeFuture<SendSignedBlockResult> sendSignedBlock(
      SignedBlockContainer blockContainer, BroadcastValidationLevel broadcastValidationLevel);

  SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      List<SyncCommitteeMessage> syncCommitteeMessages);

  SafeFuture<Void> sendSignedContributionAndProofs(
      Collection<SignedContributionAndProof> signedContributionAndProofs);

  SafeFuture<Void> prepareBeaconProposer(
      Collection<BeaconPreparableProposer> beaconPreparableProposers);

  /**
   * Note that only registrations for active or pending validators must be sent to the builder
   * network. Registrations for unknown or exited validators must be filtered out and not sent to
   * the builder network.
   *
   * <p>Method expects already filtered input.
   *
   * <p>See <a
   * href="https://github.com/ethereum/beacon-APIs/blob/master/apis/validator/register_validator.yaml">validator
   * registration endpoint spec</a>
   */
  SafeFuture<Void> registerValidators(SszList<SignedValidatorRegistration> validatorRegistrations);

  SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> getValidatorsLiveness(
      List<UInt64> validatorIndices, UInt64 epoch);
}
