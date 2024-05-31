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

package tech.pegasys.teku.validator.remote.sentry;

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
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
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
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class SentryValidatorApiChannel implements ValidatorApiChannel {

  private final ValidatorApiChannel dutiesProviderChannel;
  private final Optional<ValidatorApiChannel> blockHandlerChannel;
  private final Optional<ValidatorApiChannel> attestationPublisherChannel;

  public SentryValidatorApiChannel(
      final ValidatorApiChannel dutiesProviderChannel,
      final Optional<ValidatorApiChannel> blockHandlerChannel,
      final Optional<ValidatorApiChannel> attestationPublisherChannel) {
    this.dutiesProviderChannel = dutiesProviderChannel;
    this.blockHandlerChannel = blockHandlerChannel;
    this.attestationPublisherChannel = attestationPublisherChannel;
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return dutiesProviderChannel.getGenesisData();
  }

  @Override
  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final Collection<BLSPublicKey> publicKeys) {
    return dutiesProviderChannel.getValidatorIndices(publicKeys);
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      final Collection<BLSPublicKey> validatorIdentifiers) {
    return dutiesProviderChannel.getValidatorStatuses(validatorIdentifiers);
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return dutiesProviderChannel.getAttestationDuties(epoch, validatorIndices);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return dutiesProviderChannel.getSyncCommitteeDuties(epoch, validatorIndices);
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(final UInt64 epoch) {
    return dutiesProviderChannel.getProposerDuties(epoch);
  }

  @Override
  public SafeFuture<Optional<BlockContainerAndMetaData>> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final Optional<Boolean> requestedBlinded,
      final Optional<UInt64> requestedBuilderBoostFactor) {
    return blockHandlerChannel
        .orElse(dutiesProviderChannel)
        .createUnsignedBlock(
            slot, randaoReveal, graffiti, requestedBlinded, requestedBuilderBoostFactor);
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return dutiesProviderChannel.createAttestationData(slot, committeeIndex);
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot,
      final Bytes32 attestationHashTreeRoot,
      final Optional<UInt64> committeeIndex) {
    return attestationPublisherChannel
        .orElse(dutiesProviderChannel)
        .createAggregate(slot, attestationHashTreeRoot, committeeIndex);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    return attestationPublisherChannel
        .orElse(dutiesProviderChannel)
        .createSyncCommitteeContribution(slot, subcommitteeIndex, beaconBlockRoot);
  }

  @Override
  public SafeFuture<Void> subscribeToBeaconCommittee(
      final List<CommitteeSubscriptionRequest> requests) {
    return attestationPublisherChannel
        .orElse(dutiesProviderChannel)
        .subscribeToBeaconCommittee(requests);
  }

  @Override
  public SafeFuture<Void> subscribeToSyncCommitteeSubnets(
      final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    return attestationPublisherChannel
        .orElse(dutiesProviderChannel)
        .subscribeToSyncCommitteeSubnets(subscriptions);
  }

  @Override
  public SafeFuture<Void> subscribeToPersistentSubnets(
      final Set<SubnetSubscription> subnetSubscriptions) {
    return attestationPublisherChannel
        .orElse(dutiesProviderChannel)
        .subscribeToPersistentSubnets(subnetSubscriptions);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(
      final List<Attestation> attestations) {
    return attestationPublisherChannel
        .orElse(dutiesProviderChannel)
        .sendSignedAttestations(attestations);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return attestationPublisherChannel
        .orElse(dutiesProviderChannel)
        .sendAggregateAndProofs(aggregateAndProofs);
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel) {
    return blockHandlerChannel
        .orElse(dutiesProviderChannel)
        .sendSignedBlock(blockContainer, broadcastValidationLevel);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return attestationPublisherChannel
        .orElse(dutiesProviderChannel)
        .sendSyncCommitteeMessages(syncCommitteeMessages);
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    return attestationPublisherChannel
        .orElse(dutiesProviderChannel)
        .sendSignedContributionAndProofs(signedContributionAndProofs);
  }

  @Override
  public SafeFuture<Void> prepareBeaconProposer(
      final Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    return blockHandlerChannel
        .orElse(dutiesProviderChannel)
        .prepareBeaconProposer(beaconPreparableProposers);
  }

  @Override
  public SafeFuture<Void> registerValidators(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    return blockHandlerChannel
        .orElse(dutiesProviderChannel)
        .registerValidators(validatorRegistrations);
  }

  @Override
  public SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> getValidatorsLiveness(
      final List<UInt64> validatorIndices, final UInt64 epoch) {
    return dutiesProviderChannel.getValidatorsLiveness(validatorIndices, epoch);
  }

  @Override
  public SafeFuture<Optional<List<BeaconCommitteeSelectionProof>>> getBeaconCommitteeSelectionProof(
      final List<BeaconCommitteeSelectionProof> requests) {
    return dutiesProviderChannel.getBeaconCommitteeSelectionProof(requests);
  }

  @Override
  public SafeFuture<Optional<List<SyncCommitteeSelectionProof>>> getSyncCommitteeSelectionProof(
      final List<SyncCommitteeSelectionProof> requests) {
    return dutiesProviderChannel.getSyncCommitteeSelectionProof(requests);
  }
}
