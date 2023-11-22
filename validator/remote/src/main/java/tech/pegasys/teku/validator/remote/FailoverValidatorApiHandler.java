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

package tech.pegasys.teku.validator.remote;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class FailoverValidatorApiHandler implements ValidatorApiChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final Map<UInt64, ValidatorApiChannel> blindedBlockCreatorCache =
      LimitedMap.createSynchronizedLRU(2);

  private final BeaconNodeReadinessManager beaconNodeReadinessManager;
  private final RemoteValidatorApiChannel primaryDelegate;
  private final List<? extends RemoteValidatorApiChannel> failoverDelegates;
  private final boolean failoversSendSubnetSubscriptions;
  private final boolean failoversPublishSignedDuties;

  public FailoverValidatorApiHandler(
      final BeaconNodeReadinessManager beaconNodeReadinessManager,
      final RemoteValidatorApiChannel primaryDelegate,
      final List<? extends RemoteValidatorApiChannel> failoverDelegates,
      final boolean failoversSendSubnetSubscriptions,
      final boolean failoversPublishSignedDuties) {
    Preconditions.checkState(!failoverDelegates.isEmpty(), "No failovers are configured");
    this.beaconNodeReadinessManager = beaconNodeReadinessManager;
    this.primaryDelegate = primaryDelegate;
    this.failoverDelegates = failoverDelegates;
    this.failoversSendSubnetSubscriptions = failoversSendSubnetSubscriptions;
    this.failoversPublishSignedDuties = failoversPublishSignedDuties;
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

  @Deprecated
  @Override
  public SafeFuture<Optional<BlockContainer>> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean blinded) {
    final ValidatorApiChannelRequest<Optional<BlockContainer>> request =
        apiChannel ->
            apiChannel
                .createUnsignedBlock(slot, randaoReveal, graffiti, blinded)
                .thenPeek(
                    blockContainer ->
                        blockContainer
                            .filter(BlockContainer::isBlinded)
                            .ifPresent(__ -> blindedBlockCreatorCache.put(slot, apiChannel)));
    return tryRequestUntilSuccess(request);
  }

  @Override
  public SafeFuture<Optional<BlockContainer>> createUnsignedBlock(
      final UInt64 slot, final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    final ValidatorApiChannelRequest<Optional<BlockContainer>> request =
        apiChannel ->
            apiChannel
                .createUnsignedBlock(slot, randaoReveal, graffiti)
                .thenPeek(
                    blockContainer ->
                        blockContainer
                            .filter(BlockContainer::isBlinded)
                            .ifPresent(__ -> blindedBlockCreatorCache.put(slot, apiChannel)));
    return tryRequestUntilSuccess(request);
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
    return relayRequest(
        apiChannel -> apiChannel.subscribeToBeaconCommittee(requests),
        failoversSendSubnetSubscriptions);
  }

  @Override
  public SafeFuture<Void> subscribeToSyncCommitteeSubnets(
      Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    return relayRequest(
        apiChannel -> apiChannel.subscribeToSyncCommitteeSubnets(subscriptions),
        failoversSendSubnetSubscriptions);
  }

  @Override
  public SafeFuture<Void> subscribeToPersistentSubnets(
      Set<SubnetSubscription> subnetSubscriptions) {
    return relayRequest(
        apiChannel -> apiChannel.subscribeToPersistentSubnets(subnetSubscriptions),
        failoversSendSubnetSubscriptions);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(List<Attestation> attestations) {
    return relayRequest(
        apiChannel -> apiChannel.sendSignedAttestations(attestations),
        failoversPublishSignedDuties);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return relayRequest(
        apiChannel -> apiChannel.sendAggregateAndProofs(aggregateAndProofs),
        failoversPublishSignedDuties);
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel) {
    final UInt64 slot = blockContainer.getSlot();
    if (blockContainer.isBlinded() && blindedBlockCreatorCache.containsKey(slot)) {
      final ValidatorApiChannel blockCreatorApiChannel = blindedBlockCreatorCache.remove(slot);
      LOG.info(
          "Block for slot {} was blinded and will only be sent to the beacon node which created it.",
          slot);
      return blockCreatorApiChannel.sendSignedBlock(blockContainer, broadcastValidationLevel);
    }
    return relayRequest(
        apiChannel -> apiChannel.sendSignedBlock(blockContainer, broadcastValidationLevel),
        failoversPublishSignedDuties);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return relayRequest(
        apiChannel -> apiChannel.sendSyncCommitteeMessages(syncCommitteeMessages),
        failoversPublishSignedDuties);
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    return relayRequest(
        apiChannel -> apiChannel.sendSignedContributionAndProofs(signedContributionAndProofs),
        failoversPublishSignedDuties);
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

  @Override
  public SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> getValidatorsLiveness(
      final List<UInt64> validatorIndices, final UInt64 epoch) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getValidatorsLiveness(validatorIndices, epoch));
  }

  private <T> SafeFuture<T> relayRequest(final ValidatorApiChannelRequest<T> request) {
    return relayRequest(request, true);
  }

  /**
   * Relays the given request to the primary Beacon Node along with all failover Beacon Node
   * endpoints if relayRequestToFailovers flag is true. The request to the primary Beacon Node will
   * be skipped if the {@link BeaconNodeReadinessManager} marked it as NOT ready.The returned {@link
   * SafeFuture} will complete with the response from the primary Beacon Node or in case in failure
   * or if the primary node is NOT ready, it will complete with the first successful response from a
   * failover node. The returned {@link SafeFuture} will only complete exceptionally when the
   * request to the primary Beacon Node and all the requests to the failover nodes fail. In this
   * case, the returned {@link SafeFuture} will complete exceptionally with a {@link
   * FailoverRequestException}.
   */
  private <T> SafeFuture<T> relayRequest(
      final ValidatorApiChannelRequest<T> request, final boolean relayRequestToFailovers) {
    if (!relayRequestToFailovers) {
      return runPrimaryRequest(request);
    }
    final Map<RemoteValidatorApiChannel, Throwable> capturedExceptions = new ConcurrentHashMap<>();
    final List<SafeFuture<T>> failoverResponses =
        failoverDelegates.stream()
            .map(
                failover ->
                    runRequest(failover, request)
                        .catchAndRethrow(throwable -> capturedExceptions.put(failover, throwable)))
            .toList();
    if (!beaconNodeReadinessManager.isReady(primaryDelegate)) {
      LOG.debug(
          "Remote request will NOT be sent to the primary Beacon Node {} because it is NOT ready. Will try to use a response from a failover.",
          primaryDelegate.getEndpoint());
      return getFirstSuccessfulResponseFromFailovers(failoverResponses, capturedExceptions);
    }
    return runPrimaryRequest(request)
        .exceptionallyCompose(
            primaryThrowable -> {
              capturedExceptions.put(primaryDelegate, primaryThrowable);
              LOG.debug(
                  "Remote request which is sent to all configured Beacon Node endpoints failed on the primary Beacon Node {}. Will try to use a response from a failover.",
                  primaryDelegate.getEndpoint());
              return getFirstSuccessfulResponseFromFailovers(failoverResponses, capturedExceptions);
            });
  }

  private <T> SafeFuture<T> getFirstSuccessfulResponseFromFailovers(
      final List<SafeFuture<T>> failoverResponses,
      final Map<RemoteValidatorApiChannel, Throwable> capturedExceptions) {
    return SafeFuture.firstSuccess(failoverResponses)
        .exceptionallyCompose(
            __ -> {
              final FailoverRequestException failoverRequestException =
                  new FailoverRequestException(capturedExceptions);
              return SafeFuture.failedFuture(failoverRequestException);
            });
  }

  /**
   * Tries the given request first with the primary Beacon Node. The request to the primary Beacon
   * Node will be skipped if the {@link BeaconNodeReadinessManager} marked it as NOT ready. If the
   * request to the primary Beacon Node fails, the request will be retried against each failover
   * Beacon Node in order of readiness determined by the {@link BeaconNodeReadinessManager} until
   * there is a successful response. In case all the requests fail, the returned {@link SafeFuture}
   * will complete exceptionally with a {@link FailoverRequestException}.
   */
  private <T> SafeFuture<T> tryRequestUntilSuccess(final ValidatorApiChannelRequest<T> request) {
    if (!beaconNodeReadinessManager.isReady(primaryDelegate)) {
      LOG.debug(
          "Remote request will NOT be sent to the primary Beacon Node {} because it is NOT ready. Will try sending the request to one of the configured failovers.",
          primaryDelegate.getEndpoint());
      return makeRequestToFailoversUntilSuccess(request, new HashMap<>());
    }
    return runPrimaryRequest(request)
        .exceptionallyCompose(
            throwable -> {
              LOG.debug(
                  "Remote request to the primary Beacon Node {} failed. Will try sending the request to one of the configured failovers.",
                  primaryDelegate.getEndpoint());
              final Map<RemoteValidatorApiChannel, Throwable> capturedExceptions = new HashMap<>();
              capturedExceptions.put(primaryDelegate, throwable);
              return makeRequestToFailoversUntilSuccess(request, capturedExceptions);
            });
  }

  private <T> SafeFuture<T> makeRequestToFailoversUntilSuccess(
      final ValidatorApiChannelRequest<T> request,
      final Map<RemoteValidatorApiChannel, Throwable> capturedExceptions) {
    final Iterator<? extends RemoteValidatorApiChannel> failoverDelegates =
        beaconNodeReadinessManager.getFailoversInOrderOfReadiness();
    return makeRequestToFailoversUntilSuccess(
        failoverDelegates.next(), failoverDelegates, request, capturedExceptions);
  }

  private <T> SafeFuture<T> makeRequestToFailoversUntilSuccess(
      final RemoteValidatorApiChannel currentFailoverDelegate,
      final Iterator<? extends RemoteValidatorApiChannel> failoverDelegates,
      final ValidatorApiChannelRequest<T> request,
      final Map<RemoteValidatorApiChannel, Throwable> capturedExceptions) {
    return runRequest(currentFailoverDelegate, request)
        .exceptionallyCompose(
            throwable -> {
              capturedExceptions.put(currentFailoverDelegate, throwable);
              if (!failoverDelegates.hasNext()) {
                final FailoverRequestException failoverRequestException =
                    new FailoverRequestException(capturedExceptions);
                return SafeFuture.failedFuture(failoverRequestException);
              }
              final RemoteValidatorApiChannel nextFailoverDelegate = failoverDelegates.next();
              LOG.debug(
                  "Remote request to a failover Beacon Node {} failed. Will try sending the request to another failover {}",
                  currentFailoverDelegate.getEndpoint(),
                  nextFailoverDelegate.getEndpoint());
              return makeRequestToFailoversUntilSuccess(
                  nextFailoverDelegate, failoverDelegates, request, capturedExceptions);
            })
        .thenPeek(
            __ ->
                LOG.debug(
                    "Remote request succeeded using a failover Beacon Node {}",
                    currentFailoverDelegate.getEndpoint()));
  }

  private <T> SafeFuture<T> runPrimaryRequest(final ValidatorApiChannelRequest<T> request) {
    return runRequest(primaryDelegate, request);
  }

  private <T> SafeFuture<T> runRequest(
      final RemoteValidatorApiChannel delegate, final ValidatorApiChannelRequest<T> request) {
    return request.run(delegate);
  }

  @VisibleForTesting
  @FunctionalInterface
  interface ValidatorApiChannelRequest<T> {
    SafeFuture<T> run(final ValidatorApiChannel apiChannel);
  }
}
