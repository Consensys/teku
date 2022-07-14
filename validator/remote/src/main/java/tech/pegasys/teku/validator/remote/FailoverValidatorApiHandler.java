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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import tech.pegasys.teku.validator.api.SyncingStatus;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class FailoverValidatorApiHandler implements ValidatorApiChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final RemoteValidatorApiChannel primaryDelegate;
  private final List<RemoteValidatorApiChannel> failoverDelegates;
  private final ValidatorLogger validatorLogger;

  public FailoverValidatorApiHandler(
      final RemoteValidatorApiChannel primaryDelegate,
      final List<RemoteValidatorApiChannel> failoverDelegates,
      final ValidatorLogger validatorLogger) {
    this.primaryDelegate = primaryDelegate;
    checkArgument(
        !failoverDelegates.isEmpty(),
        "One or more Beacon Nodes should be defined as a failover to use the failover feature.");
    this.failoverDelegates = failoverDelegates;
    this.validatorLogger = validatorLogger;
  }

  @Override
  public SafeFuture<Optional<SyncingStatus>> getSyncingStatus() {
    return tryRequestUntilSuccess(ValidatorApiChannel::getSyncingStatus);
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return tryRequestUntilSuccess(ValidatorApiChannel::getGenesisData);
  }

  @Override
  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final Collection<BLSPublicKey> publicKeys) {
    return tryRequestUntilSuccess(apiChannel -> apiChannel.getValidatorIndices(publicKeys));
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      final Collection<BLSPublicKey> validatorIdentifiers) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getValidatorStatuses(validatorIdentifiers));
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getAttestationDuties(epoch, validatorIndices));
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getSyncCommitteeDuties(epoch, validatorIndices));
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(final UInt64 epoch) {
    return tryRequestUntilSuccess(apiChannel -> apiChannel.getProposerDuties(epoch));
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean blinded) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.createUnsignedBlock(slot, randaoReveal, graffiti, blinded));
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.createAttestationData(slot, committeeIndex));
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.createAggregate(slot, attestationHashTreeRoot));
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    return tryRequestUntilSuccess(
        apiChannel ->
            apiChannel.createSyncCommitteeContribution(slot, subcommitteeIndex, beaconBlockRoot));
  }

  @Override
  public SafeFuture<Void> subscribeToBeaconCommittee(List<CommitteeSubscriptionRequest> requests) {
    return relayRequest(apiChannel -> apiChannel.subscribeToBeaconCommittee(requests));
  }

  @Override
  public SafeFuture<Void> subscribeToSyncCommitteeSubnets(
      Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    return relayRequest(apiChannel -> apiChannel.subscribeToSyncCommitteeSubnets(subscriptions));
  }

  @Override
  public SafeFuture<Void> subscribeToPersistentSubnets(
      Set<SubnetSubscription> subnetSubscriptions) {
    return relayRequest(apiChannel -> apiChannel.subscribeToPersistentSubnets(subnetSubscriptions));
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(List<Attestation> attestations) {
    return relayRequest(apiChannel -> apiChannel.sendSignedAttestations(attestations));
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return relayRequest(apiChannel -> apiChannel.sendAggregateAndProofs(aggregateAndProofs));
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(final SignedBeaconBlock block) {
    return relayRequest(apiChannel -> apiChannel.sendSignedBlock(block));
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return relayRequest(apiChannel -> apiChannel.sendSyncCommitteeMessages(syncCommitteeMessages));
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    return relayRequest(
        apiChannel -> apiChannel.sendSignedContributionAndProofs(signedContributionAndProofs));
  }

  @Override
  public SafeFuture<Void> prepareBeaconProposer(
      final Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    return relayRequest(apiChannel -> apiChannel.prepareBeaconProposer(beaconPreparableProposers));
  }

  @Override
  public SafeFuture<Void> registerValidators(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    return relayRequest(apiChannel -> apiChannel.registerValidators(validatorRegistrations));
  }

  /**
   * Relays the given request to the primary Beacon Node along with all failover Beacon Node
   * endpoints. The returned {@link SafeFuture} will complete with the response from the primary
   * Beacon Node or in case in failure, it will complete with the first successful response from a
   * failover node. The returned {@link SafeFuture} will only complete exceptionally when the
   * request to the primary Beacon Node and all the requests to the failover nodes fail. In this
   * case, the returned error will be the primary Beacon Node error and all the failovers errors
   * will be part of it as suppressed.
   */
  private <T> SafeFuture<T> relayRequest(final ValidatorApiChannelRequest<T> request) {
    final SafeFuture<T> primaryResponse = request.run(primaryDelegate);
    final List<SafeFuture<T>> failoversResponses =
        failoverDelegates.stream().map(request::run).collect(Collectors.toList());
    return primaryResponse.exceptionallyCompose(
        primaryThrowable -> {
          LOG.debug(
              "Request which is sent to all configured Beacon Node endpoints failed on the primary Beacon Node {} . Will try to use a response from a failover.",
              primaryDelegate.getEndpoint());
          return SafeFuture.firstSuccess(failoversResponses)
              .exceptionallyCompose(
                  __ -> {
                    validatorLogger.remoteBeaconNodeRequestFailedOnAllConfiguredEndpoints();
                    SafeFuture.addSuppressedErrors(
                        primaryThrowable, failoversResponses.toArray(new SafeFuture<?>[0]));
                    return SafeFuture.failedFuture(primaryThrowable);
                  });
        });
  }

  /**
   * Tries the given request first with the primary Beacon Node. If it fails, it will retry the
   * request against each failover Beacon Node in order until there is a successful response. In
   * case all the requests fail, the returned {@link SafeFuture} will complete exceptionally with
   * the last error and all previous errors will be part of it as suppressed.
   */
  private <T> SafeFuture<T> tryRequestUntilSuccess(final ValidatorApiChannelRequest<T> request) {
    return makeFailoverRequest(
        primaryDelegate, failoverDelegates.iterator(), request, new HashSet<>());
  }

  private <T> SafeFuture<T> makeFailoverRequest(
      final RemoteValidatorApiChannel currentDelegate,
      final Iterator<RemoteValidatorApiChannel> failoverDelegates,
      final ValidatorApiChannelRequest<T> request,
      final Set<Throwable> capturedExceptions) {
    return request
        .run(currentDelegate)
        .exceptionallyCompose(
            throwable -> {
              final URI failedEndpoint = currentDelegate.getEndpoint();
              if (!failoverDelegates.hasNext()) {
                validatorLogger.remoteBeaconNodeRequestFailedOnAllConfiguredEndpoints();
                capturedExceptions.forEach(throwable::addSuppressed);
                return SafeFuture.failedFuture(throwable);
              }
              capturedExceptions.add(throwable);
              final RemoteValidatorApiChannel nextDelegate = failoverDelegates.next();
              LOG.debug(
                  "Request to Beacon Node {} failed. Will try sending request to failover {}",
                  failedEndpoint,
                  nextDelegate.getEndpoint());
              return makeFailoverRequest(
                  nextDelegate, failoverDelegates, request, capturedExceptions);
            });
  }

  @VisibleForTesting
  @FunctionalInterface
  interface ValidatorApiChannelRequest<T> {
    SafeFuture<T> run(final ValidatorApiChannel apiChannel);
  }
}
