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

package tech.pegasys.teku.validator.beaconnode.metrics;

import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class MetricRecordingValidatorApiChannel implements ValidatorApiChannel {

  static final String BEACON_NODE_REQUESTS_COUNTER_NAME = "beacon_node_requests_total";

  static final String PUBLISHED_ATTESTATION_COUNTER_NAME =
      "beacon_node_published_attestation_total";
  static final String PUBLISHED_AGGREGATE_COUNTER_NAME = "beacon_node_published_aggregate_total";
  static final String PUBLISHED_BLOCK_COUNTER_NAME = "beacon_node_published_block_total";
  static final String SYNC_COMMITTEE_SEND_MESSAGES_NAME =
      "beacon_node_send_sync_committee_messages_total";
  static final String SYNC_COMMITTEE_SEND_CONTRIBUTIONS_NAME =
      "beacon_node_send_sync_committee_contributions_total";

  private final ValidatorApiChannel delegate;
  private final LabelledMetric<Counter> beaconNodeRequestsCounter;
  private final BeaconChainRequestCounter sendAttestationRequestCounter;
  private final BeaconChainRequestCounter sendAggregateRequestCounter;
  private final Counter sendBlockRequestCounter;
  private final BeaconChainRequestCounter sendSyncCommitteeMessagesRequestCounter;
  private final Counter sendContributionAndProofsRequestCounter;

  public MetricRecordingValidatorApiChannel(
      final MetricsSystem metricsSystem, final ValidatorApiChannel delegate) {
    this.delegate = delegate;
    beaconNodeRequestsCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.VALIDATOR,
            BEACON_NODE_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests sent to the beacon node",
            "method",
            "outcome");
    // legacy metrics
    sendAttestationRequestCounter =
        BeaconChainRequestCounter.create(
            metricsSystem,
            PUBLISHED_ATTESTATION_COUNTER_NAME,
            "Counter recording the number of signed attestations sent to the beacon node");
    sendAggregateRequestCounter =
        BeaconChainRequestCounter.create(
            metricsSystem,
            PUBLISHED_AGGREGATE_COUNTER_NAME,
            "Counter recording the number of signed aggregate attestations sent to the beacon node");
    sendBlockRequestCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.VALIDATOR,
            PUBLISHED_BLOCK_COUNTER_NAME,
            "Counter recording the number of signed blocks sent to the beacon node");
    sendSyncCommitteeMessagesRequestCounter =
        BeaconChainRequestCounter.create(
            metricsSystem,
            SYNC_COMMITTEE_SEND_MESSAGES_NAME,
            "Counter recording the number of sync committee messages sent to the beacon node");
    sendContributionAndProofsRequestCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.VALIDATOR,
            SYNC_COMMITTEE_SEND_CONTRIBUTIONS_NAME,
            "Counter recording the number of signed contributions and proofs sent to the beacon node");
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return countOptionalDataRequest(
        delegate.getGenesisData(), BeaconNodeRequestLabels.GET_GENESIS_METHOD);
  }

  @Override
  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final Collection<BLSPublicKey> publicKeys) {
    return countDataRequest(
        delegate.getValidatorIndices(publicKeys),
        BeaconNodeRequestLabels.GET_VALIDATOR_INDICES_METHOD);
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      final Collection<BLSPublicKey> validatorIdentifiers) {
    return countOptionalDataRequest(
        delegate.getValidatorStatuses(validatorIdentifiers),
        BeaconNodeRequestLabels.GET_VALIDATOR_STATUSES_METHOD);
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return countOptionalDataRequest(
        delegate.getAttestationDuties(epoch, validatorIndices),
        BeaconNodeRequestLabels.GET_ATTESTATION_DUTIES_METHOD);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return countOptionalDataRequest(
        delegate.getSyncCommitteeDuties(epoch, validatorIndices),
        BeaconNodeRequestLabels.GET_SYNC_COMMITTEE_DUTIES_METHOD);
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(final UInt64 epoch) {
    return countOptionalDataRequest(
        delegate.getProposerDuties(epoch),
        BeaconNodeRequestLabels.GET_PROPOSER_DUTIES_REQUESTS_METHOD);
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      Optional<Bytes32> graffiti,
      final boolean blinded) {
    return countOptionalDataRequest(
        delegate.createUnsignedBlock(slot, randaoReveal, graffiti, blinded),
        BeaconNodeRequestLabels.CREATE_UNSIGNED_BLOCK_METHOD);
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return countOptionalDataRequest(
        delegate.createAttestationData(slot, committeeIndex),
        BeaconNodeRequestLabels.CREATE_ATTESTATION_METHOD);
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    return countOptionalDataRequest(
        delegate.createAggregate(slot, attestationHashTreeRoot),
        BeaconNodeRequestLabels.CREATE_AGGREGATE_METHOD);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    return countOptionalDataRequest(
        delegate.createSyncCommitteeContribution(slot, subcommitteeIndex, beaconBlockRoot),
        BeaconNodeRequestLabels.CREATE_SYNC_COMMITTEE_CONTRIBUTION_METHOD);
  }

  @Override
  public SafeFuture<Void> subscribeToBeaconCommittee(
      final List<CommitteeSubscriptionRequest> requests) {
    return countDataRequest(
        delegate.subscribeToBeaconCommittee(requests),
        BeaconNodeRequestLabels.BEACON_COMMITTEE_SUBSCRIPTION_METHOD);
  }

  @Override
  public SafeFuture<Void> subscribeToSyncCommitteeSubnets(
      final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    return countDataRequest(
        delegate.subscribeToSyncCommitteeSubnets(subscriptions),
        BeaconNodeRequestLabels.SYNC_COMMITTEE_SUBNET_SUBSCRIPTION_METHOD);
  }

  @Override
  public SafeFuture<Void> subscribeToPersistentSubnets(
      final Set<SubnetSubscription> subnetSubscriptions) {
    return countDataRequest(
        delegate.subscribeToPersistentSubnets(subnetSubscriptions),
        BeaconNodeRequestLabels.PERSISTENT_SUBNETS_SUBSCRIPTION_METHOD);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(
      final List<Attestation> attestations) {
    return countSendRequest(
        delegate.sendSignedAttestations(attestations),
        BeaconNodeRequestLabels.PUBLISH_ATTESTATION_METHOD,
        sendAttestationRequestCounter);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return countSendRequest(
        delegate.sendAggregateAndProofs(aggregateAndProofs),
        BeaconNodeRequestLabels.PUBLISH_AGGREGATE_AND_PROOFS_METHOD,
        sendAggregateRequestCounter);
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(final SignedBeaconBlock block) {
    sendBlockRequestCounter.inc();
    return countDataRequest(
        delegate.sendSignedBlock(block), BeaconNodeRequestLabels.PUBLISH_BLOCK_METHOD);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return countSendRequest(
        delegate.sendSyncCommitteeMessages(syncCommitteeMessages),
        BeaconNodeRequestLabels.SEND_SYNC_COMMITTEE_MESSAGES_METHOD,
        sendSyncCommitteeMessagesRequestCounter);
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    sendContributionAndProofsRequestCounter.inc();
    return countDataRequest(
        delegate.sendSignedContributionAndProofs(signedContributionAndProofs),
        BeaconNodeRequestLabels.SEND_CONTRIBUTIONS_AND_PROOFS_METHOD);
  }

  @Override
  public SafeFuture<Void> prepareBeaconProposer(
      final Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    return countDataRequest(
        delegate.prepareBeaconProposer(beaconPreparableProposers),
        BeaconNodeRequestLabels.PREPARE_BEACON_PROPOSERS_METHOD);
  }

  @Override
  public SafeFuture<Void> registerValidators(
      SszList<SignedValidatorRegistration> validatorRegistrations) {
    return countDataRequest(
        delegate.registerValidators(validatorRegistrations),
        BeaconNodeRequestLabels.REGISTER_VALIDATORS_METHOD);
  }

  private <T> SafeFuture<T> countDataRequest(
      final SafeFuture<T> request, final String requestName) {
    return request
        .catchAndRethrow(__ -> recordError(requestName))
        .thenPeek(__ -> recordSuccess(requestName));
  }

  private <T> SafeFuture<Optional<T>> countOptionalDataRequest(
      final SafeFuture<Optional<T>> request, final String requestName) {
    return request
        .catchAndRethrow(__ -> recordError(requestName))
        .thenPeek(
            result ->
                result.ifPresentOrElse(
                    __ -> recordSuccess(requestName), () -> recordDataUnavailable(requestName)));
  }

  private <T> SafeFuture<List<T>> countSendRequest(
      final SafeFuture<List<T>> request,
      final String requestName,
      final BeaconChainRequestCounter legacyCounter) {
    return request
        .catchAndRethrow(
            __ -> {
              recordError(requestName);
              legacyCounter.onError();
            })
        .thenPeek(
            result -> {
              if (result.isEmpty()) {
                recordSuccess(requestName);
                legacyCounter.onSuccess();
              } else {
                recordError(requestName);
                legacyCounter.onError();
              }
            });
  }

  private void recordSuccess(final String requestName) {
    recordRequest(requestName, RequestOutcome.SUCCESS);
  }

  private void recordError(final String requestName) {
    recordRequest(requestName, RequestOutcome.ERROR);
  }

  private void recordDataUnavailable(final String requestName) {
    recordRequest(requestName, RequestOutcome.DATA_UNAVAILABLE);
  }

  private void recordRequest(final String name, final RequestOutcome outcome) {
    beaconNodeRequestsCounter.labels(name, outcome.displayName).inc();
  }

  enum RequestOutcome {
    SUCCESS("success"),
    ERROR("error"),
    DATA_UNAVAILABLE("data_unavailable");

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
