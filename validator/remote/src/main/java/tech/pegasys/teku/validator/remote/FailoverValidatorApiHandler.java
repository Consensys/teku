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

package tech.pegasys.teku.validator.remote;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.RemoteValidatorApiChannel;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class FailoverValidatorApiHandler implements ValidatorApiChannel {

  private final List<RemoteValidatorApiChannel> delegates;
  private final ValidatorLogger validatorLogger;

  public FailoverValidatorApiHandler(
      final List<RemoteValidatorApiChannel> delegates, final ValidatorLogger validatorLogger) {
    this.delegates = delegates;
    this.validatorLogger = validatorLogger;
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return makeFailoverRequest(ValidatorApiChannel::getGenesisData);
  }

  @Override
  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final Collection<BLSPublicKey> publicKeys) {
    return makeFailoverRequest(apiChannel -> apiChannel.getValidatorIndices(publicKeys));
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      final Collection<BLSPublicKey> validatorIdentifiers) {
    return makeFailoverRequest(apiChannel -> apiChannel.getValidatorStatuses(validatorIdentifiers));
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return makeFailoverRequest(
        apiChannel -> apiChannel.getAttestationDuties(epoch, validatorIndices));
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return makeFailoverRequest(
        apiChannel -> apiChannel.getSyncCommitteeDuties(epoch, validatorIndices));
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(final UInt64 epoch) {
    return makeFailoverRequest(apiChannel -> apiChannel.getProposerDuties(epoch));
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean blinded) {
    return makeFailoverRequest(
        apiChannel -> apiChannel.createUnsignedBlock(slot, randaoReveal, graffiti, blinded));
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return makeFailoverRequest(
        apiChannel -> apiChannel.createAttestationData(slot, committeeIndex));
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    return makeFailoverRequest(
        apiChannel -> apiChannel.createAggregate(slot, attestationHashTreeRoot));
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    return makeFailoverRequest(
        apiChannel ->
            apiChannel.createSyncCommitteeContribution(slot, subcommitteeIndex, beaconBlockRoot));
  }

  @Override
  public void subscribeToBeaconCommittee(List<CommitteeSubscriptionRequest> requests) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public void subscribeToSyncCommitteeSubnets(
      Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public void subscribeToPersistentSubnets(Set<SubnetSubscription> subnetSubscriptions) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(List<Attestation> attestations) {
    return makeFailoverRequest(apiChannel -> apiChannel.sendSignedAttestations(attestations));
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return makeFailoverRequest(apiChannel -> apiChannel.sendAggregateAndProofs(aggregateAndProofs));
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(final SignedBeaconBlock block) {
    return makeFailoverRequest(apiChannel -> apiChannel.sendSignedBlock(block));
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return makeFailoverRequest(
        apiChannel -> apiChannel.sendSyncCommitteeMessages(syncCommitteeMessages));
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    return makeFailoverRequest(
        apiChannel -> apiChannel.sendSignedContributionAndProofs(signedContributionAndProofs));
  }

  @Override
  public void prepareBeaconProposer(
      final Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public SafeFuture<Void> registerValidators(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    return makeFailoverRequest(apiChannel -> apiChannel.registerValidators(validatorRegistrations));
  }

  private <T> SafeFuture<T> makeFailoverRequest(final ValidatorApiChannelRequest<T> request) {
    final Iterator<RemoteValidatorApiChannel> delegatesIterator = delegates.iterator();
    return makeFailoverRequest(delegatesIterator, delegatesIterator.next(), request);
  }

  private <T> SafeFuture<T> makeFailoverRequest(
      final Iterator<RemoteValidatorApiChannel> delegatesIterator,
      final RemoteValidatorApiChannel currentDelegate,
      final ValidatorApiChannelRequest<T> request) {
    return request
        .run(currentDelegate)
        .exceptionallyCompose(
            throwable -> {
              if (!delegatesIterator.hasNext()) {
                validatorLogger.remoteBeaconNodeFailoverRequestFailed(
                    currentDelegate.getEndpoint(), throwable, Optional.empty());
                return SafeFuture.failedFuture(throwable);
              }
              final RemoteValidatorApiChannel nextDelegate = delegatesIterator.next();
              validatorLogger.remoteBeaconNodeFailoverRequestFailed(
                  currentDelegate.getEndpoint(),
                  throwable,
                  Optional.of(nextDelegate.getEndpoint()));
              return makeFailoverRequest(delegatesIterator, nextDelegate, request);
            });
  }

  @VisibleForTesting
  @FunctionalInterface
  interface ValidatorApiChannelRequest<T> {
    SafeFuture<T> run(final ValidatorApiChannel apiChannel);
  }
}
