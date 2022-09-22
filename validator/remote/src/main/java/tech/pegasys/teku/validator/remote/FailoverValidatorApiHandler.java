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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
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
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.metrics.BeaconNodeRequestLabels;

public class FailoverValidatorApiHandler implements ValidatorApiChannel {

  private static final Logger LOG = LogManager.getLogger();

  static final String FAILOVER_BEACON_NODES_REQUESTS_COUNTER_NAME =
      "failover_beacon_nodes_requests_total";

  private final RemoteValidatorApiChannel primaryDelegate;
  private final List<RemoteValidatorApiChannel> failoverDelegates;
  private final boolean failoversSendSubnetSubscriptions;
  private final LabelledMetric<Counter> failoverBeaconNodesRequestsCounter;

  public FailoverValidatorApiHandler(
      final RemoteValidatorApiChannel primaryDelegate,
      final List<RemoteValidatorApiChannel> failoverDelegates,
      final boolean failoversSendSubnetSubscriptions,
      final MetricsSystem metricsSystem) {
    this.primaryDelegate = primaryDelegate;
    this.failoverDelegates = failoverDelegates;
    this.failoversSendSubnetSubscriptions = failoversSendSubnetSubscriptions;
    failoverBeaconNodesRequestsCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.VALIDATOR,
            FAILOVER_BEACON_NODES_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests sent to the configured Beacon Nodes endpoints",
            "failover_endpoint",
            "method",
            "outcome");
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return tryRequestUntilSuccess(
        ValidatorApiChannel::getGenesisData, BeaconNodeRequestLabels.GET_GENESIS_METHOD);
  }

  @Override
  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final Collection<BLSPublicKey> publicKeys) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getValidatorIndices(publicKeys),
        BeaconNodeRequestLabels.GET_VALIDATOR_INDICES_METHOD);
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      final Collection<BLSPublicKey> validatorIdentifiers) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getValidatorStatuses(validatorIdentifiers),
        BeaconNodeRequestLabels.GET_VALIDATOR_STATUSES_METHOD);
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getAttestationDuties(epoch, validatorIndices),
        BeaconNodeRequestLabels.GET_ATTESTATION_DUTIES_METHOD);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getSyncCommitteeDuties(epoch, validatorIndices),
        BeaconNodeRequestLabels.GET_SYNC_COMMITTEE_DUTIES_METHOD);
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(final UInt64 epoch) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.getProposerDuties(epoch),
        BeaconNodeRequestLabels.GET_PROPOSER_DUTIES_REQUESTS_METHOD);
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean blinded) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.createUnsignedBlock(slot, randaoReveal, graffiti, blinded),
        BeaconNodeRequestLabels.CREATE_UNSIGNED_BLOCK_METHOD);
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.createAttestationData(slot, committeeIndex),
        BeaconNodeRequestLabels.CREATE_ATTESTATION_METHOD);
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    return tryRequestUntilSuccess(
        apiChannel -> apiChannel.createAggregate(slot, attestationHashTreeRoot),
        BeaconNodeRequestLabels.CREATE_AGGREGATE_METHOD);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    return tryRequestUntilSuccess(
        apiChannel ->
            apiChannel.createSyncCommitteeContribution(slot, subcommitteeIndex, beaconBlockRoot),
        BeaconNodeRequestLabels.CREATE_SYNC_COMMITTEE_CONTRIBUTION_METHOD);
  }

  @Override
  public SafeFuture<Void> subscribeToBeaconCommittee(List<CommitteeSubscriptionRequest> requests) {
    return relayRequest(
        apiChannel -> apiChannel.subscribeToBeaconCommittee(requests),
        BeaconNodeRequestLabels.BEACON_COMMITTEE_SUBSCRIPTION_METHOD,
        failoversSendSubnetSubscriptions);
  }

  @Override
  public SafeFuture<Void> subscribeToSyncCommitteeSubnets(
      Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    return relayRequest(
        apiChannel -> apiChannel.subscribeToSyncCommitteeSubnets(subscriptions),
        BeaconNodeRequestLabels.SYNC_COMMITTEE_SUBNET_SUBSCRIPTION_METHOD,
        failoversSendSubnetSubscriptions);
  }

  @Override
  public SafeFuture<Void> subscribeToPersistentSubnets(
      Set<SubnetSubscription> subnetSubscriptions) {
    return relayRequest(
        apiChannel -> apiChannel.subscribeToPersistentSubnets(subnetSubscriptions),
        BeaconNodeRequestLabels.PERSISTENT_SUBNETS_SUBSCRIPTION_METHOD,
        failoversSendSubnetSubscriptions);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(List<Attestation> attestations) {
    return relayRequest(
        apiChannel -> apiChannel.sendSignedAttestations(attestations),
        BeaconNodeRequestLabels.PUBLISH_ATTESTATION_METHOD);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return relayRequest(
        apiChannel -> apiChannel.sendAggregateAndProofs(aggregateAndProofs),
        BeaconNodeRequestLabels.PUBLISH_AGGREGATE_AND_PROOFS_METHOD);
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(final SignedBeaconBlock block) {
    return relayRequest(
        apiChannel -> apiChannel.sendSignedBlock(block),
        BeaconNodeRequestLabels.PUBLISH_BLOCK_METHOD);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return relayRequest(
        apiChannel -> apiChannel.sendSyncCommitteeMessages(syncCommitteeMessages),
        BeaconNodeRequestLabels.SEND_SYNC_COMMITTEE_MESSAGES_METHOD);
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    return relayRequest(
        apiChannel -> apiChannel.sendSignedContributionAndProofs(signedContributionAndProofs),
        BeaconNodeRequestLabels.SEND_CONTRIBUTIONS_AND_PROOFS_METHOD);
  }

  @Override
  public SafeFuture<Void> prepareBeaconProposer(
      final Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    return relayRequest(
        apiChannel -> apiChannel.prepareBeaconProposer(beaconPreparableProposers),
        BeaconNodeRequestLabels.PREPARE_BEACON_PROPOSERS_METHOD);
  }

  @Override
  public SafeFuture<Void> registerValidators(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    return relayRequest(
        apiChannel -> apiChannel.registerValidators(validatorRegistrations),
        BeaconNodeRequestLabels.REGISTER_VALIDATORS_METHOD);
  }

  private <T> SafeFuture<T> relayRequest(
      final ValidatorApiChannelRequest<T> request, final String method) {
    return relayRequest(request, method, true);
  }

  /**
   * Relays the given request to the primary Beacon Node along with all failover Beacon Node
   * endpoints if relayRequestToFailovers flag is true. The returned {@link SafeFuture} will
   * complete with the response from the primary Beacon Node or in case in failure, it will complete
   * with the first successful response from a failover node. The returned {@link SafeFuture} will
   * only complete exceptionally when the request to the primary Beacon Node and all the requests to
   * the failover nodes fail. In this case, the returned {@link SafeFuture} will complete
   * exceptionally with a {@link FailoverRequestException}.
   */
  private <T> SafeFuture<T> relayRequest(
      final ValidatorApiChannelRequest<T> request,
      final String method,
      final boolean relayRequestToFailovers) {
    final SafeFuture<T> primaryResponse = runRequest(primaryDelegate, request, method);
    if (failoverDelegates.isEmpty() || !relayRequestToFailovers) {
      return primaryResponse;
    }
    final Map<HttpUrl, Throwable> capturedExceptions = new ConcurrentHashMap<>();
    final List<SafeFuture<T>> failoversResponses =
        failoverDelegates.stream()
            .map(
                failover ->
                    runRequest(failover, request, method)
                        .catchAndRethrow(
                            throwable -> capturedExceptions.put(failover.getEndpoint(), throwable)))
            .collect(Collectors.toList());
    return primaryResponse.exceptionallyCompose(
        primaryThrowable -> {
          final HttpUrl primaryEndpoint = primaryDelegate.getEndpoint();
          capturedExceptions.put(primaryEndpoint, primaryThrowable);
          LOG.debug(
              "Remote request ({}) which is sent to all configured Beacon Node endpoints failed on the primary Beacon Node {}. Will try to use a response from a failover.",
              method,
              primaryEndpoint);
          return SafeFuture.firstSuccess(failoversResponses)
              .exceptionallyCompose(
                  __ -> {
                    final FailoverRequestException failoverRequestException =
                        new FailoverRequestException(method, capturedExceptions);
                    return SafeFuture.failedFuture(failoverRequestException);
                  });
        });
  }

  /**
   * Tries the given request first with the primary Beacon Node. If it fails, it will retry the
   * request against each failover Beacon Node in order until there is a successful response. In
   * case all the requests fail, the returned {@link SafeFuture} will complete exceptionally with a
   * {@link FailoverRequestException}.
   */
  private <T> SafeFuture<T> tryRequestUntilSuccess(
      final ValidatorApiChannelRequest<T> request, final String method) {
    if (failoverDelegates.isEmpty()) {
      return runRequest(primaryDelegate, request, method);
    }
    return makeFailoverRequest(
        primaryDelegate, failoverDelegates.iterator(), request, method, new HashMap<>());
  }

  private <T> SafeFuture<T> makeFailoverRequest(
      final RemoteValidatorApiChannel currentDelegate,
      final Iterator<RemoteValidatorApiChannel> failoverDelegates,
      final ValidatorApiChannelRequest<T> request,
      final String method,
      final Map<HttpUrl, Throwable> capturedExceptions) {
    return runRequest(currentDelegate, request, method)
        .exceptionallyCompose(
            throwable -> {
              final HttpUrl failedEndpoint = currentDelegate.getEndpoint();
              capturedExceptions.put(failedEndpoint, throwable);
              if (!failoverDelegates.hasNext()) {
                final FailoverRequestException failoverRequestException =
                    new FailoverRequestException(method, capturedExceptions);
                return SafeFuture.failedFuture(failoverRequestException);
              }
              final RemoteValidatorApiChannel nextDelegate = failoverDelegates.next();
              LOG.debug(
                  "Remote request ({}) to Beacon Node {} failed. Will try sending request to failover {}",
                  method,
                  failedEndpoint,
                  nextDelegate.getEndpoint());
              return makeFailoverRequest(
                  nextDelegate, failoverDelegates, request, method, capturedExceptions);
            });
  }

  private <T> SafeFuture<T> runRequest(
      final RemoteValidatorApiChannel delegate,
      final ValidatorApiChannelRequest<T> request,
      final String method) {
    final SafeFuture<T> futureResponse = request.run(delegate);
    return futureResponse.handleComposed(
        (response, throwable) -> {
          if (throwable != null) {
            recordFailedRequest(delegate, method);
            return SafeFuture.failedFuture(throwable);
          }
          recordSuccessfulRequest(delegate, method);
          return SafeFuture.completedFuture(response);
        });
  }

  private void recordSuccessfulRequest(
      final RemoteValidatorApiChannel failover, final String method) {
    recordRequest(failover, method, RequestOutcome.SUCCESS);
  }

  private void recordFailedRequest(final RemoteValidatorApiChannel failover, final String method) {
    recordRequest(failover, method, RequestOutcome.ERROR);
  }

  private void recordRequest(
      final RemoteValidatorApiChannel failover, final String method, final RequestOutcome outcome) {
    failoverBeaconNodesRequestsCounter
        .labels(failover.getEndpoint().toString(), method, outcome.displayName)
        .inc();
  }

  @VisibleForTesting
  @FunctionalInterface
  interface ValidatorApiChannelRequest<T> {
    SafeFuture<T> run(final ValidatorApiChannel apiChannel);
  }

  enum RequestOutcome {
    SUCCESS("success"),
    ERROR("error");

    private final String displayName;

    RequestOutcome(final String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }
}
