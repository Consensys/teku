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

package tech.pegasys.teku.validator.beaconnode.metrics;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
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
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class MetricRecordingValidatorApiChannel implements ValidatorApiChannel {

  public static final String GENESIS_TIME_REQUESTS_COUNTER_NAME =
      "beacon_node_genesis_time_requests_total";
  public static final String GET_VALIDATOR_INDICES_REQUESTS_COUNTER_NAME =
      "beacon_node_get_validator_indices_requests_total";
  public static final String ATTESTATION_DUTIES_REQUESTS_COUNTER_NAME =
      "beacon_node_attestation_duties_requests_total";
  public static final String PROPOSER_DUTIES_REQUESTS_COUNTER_NAME =
      "beacon_node_proposer_duties_requests_total";
  public static final String SYNC_COMMITTEE_DUTIES_REQUESTS_COUNTER_NAME =
      "beacon_node_sync_committee_duties_requests_total";
  public static final String UNSIGNED_BLOCK_REQUESTS_COUNTER_NAME =
      "beacon_node_unsigned_block_requests_total";
  public static final String ATTESTATION_DATA_REQUEST_COUNTER_NAME =
      "beacon_node_attestation_data_requests_total";
  public static final String AGGREGATE_REQUESTS_COUNTER_NAME =
      "beacon_node_aggregate_requests_total";
  public static final String CREATE_SYNC_COMMITTEE_CONTRIBUTION_REQUESTS_COUNTER_NAME =
      "beacon_node_create_sync_committee_contribution_requests_total";
  public static final String AGGREGATION_SUBSCRIPTION_COUNTER_NAME =
      "beacon_node_aggregation_subscription_requests_total";
  public static final String PERSISTENT_SUBSCRIPTION_COUNTER_NAME =
      "beacon_node_persistent_subscription_requests_total";
  public static final String PUBLISHED_ATTESTATION_COUNTER_NAME =
      "beacon_node_published_attestation_total";
  public static final String PUBLISHED_AGGREGATE_COUNTER_NAME =
      "beacon_node_published_aggregate_total";
  public static final String PUBLISHED_BLOCK_COUNTER_NAME = "beacon_node_published_block_total";
  public static final String SYNC_COMMITTEE_SUBNET_SUBSCRIPTION_NAME =
      "beacon_node_subscribe_sync_committee_subnet_total";
  public static final String SYNC_COMMITTEE_SEND_MESSAGES_NAME =
      "beacon_node_send_sync_committee_messages_total";
  public static final String SYNC_COMMITTEE_SEND_CONTRIBUTIONS_NAME =
      "beacon_node_send_sync_committee_contributions_total";
  public static final String PREPARE_BEACON_PROPOSER_NAME =
      "beacon_node_prepare_beacon_proposer_requests_total";
  private final ValidatorApiChannel delegate;
  private final BeaconChainRequestCounter genesisTimeRequestCounter;
  private final BeaconChainRequestCounter attestationDutiesRequestCounter;
  private final BeaconChainRequestCounter syncCommitteeDutiesRequestCounter;
  private final BeaconChainRequestCounter proposerDutiesRequestCounter;
  private final BeaconChainRequestCounter unsignedBlockRequestsCounter;
  private final BeaconChainRequestCounter attestationDataRequestsCounter;
  private final BeaconChainRequestCounter aggregateRequestsCounter;
  private final BeaconChainRequestCounter createSyncCommitteeContributionCounter;
  private final BeaconChainRequestCounter sendAttestationRequestCounter;
  private final BeaconChainRequestCounter sendAggregateRequestCounter;
  private final BeaconChainRequestCounter sendSyncCommitteeMessagesRequestCounter;
  private final Counter getValidatorIndicesRequestCounter;
  private final Counter subscribeAggregationRequestCounter;
  private final Counter subscribePersistentRequestCounter;
  private final Counter subscribeSyncCommitteeRequestCounter;
  private final Counter sendBlockRequestCounter;
  private final Counter sendContributionAndProofsRequestCounter;
  private final Counter prepareBeaconProposerCounter;

  public MetricRecordingValidatorApiChannel(
      final MetricsSystem metricsSystem, final ValidatorApiChannel delegate) {
    this.delegate = delegate;

    genesisTimeRequestCounter =
        BeaconChainRequestCounter.create(
            metricsSystem,
            GENESIS_TIME_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests for genesis time");
    attestationDutiesRequestCounter =
        BeaconChainRequestCounter.create(
            metricsSystem,
            ATTESTATION_DUTIES_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests for validator attestation duties");
    syncCommitteeDutiesRequestCounter =
        BeaconChainRequestCounter.create(
            metricsSystem,
            SYNC_COMMITTEE_DUTIES_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests for validator sync committee duties");
    proposerDutiesRequestCounter =
        BeaconChainRequestCounter.create(
            metricsSystem,
            PROPOSER_DUTIES_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests for validator proposer duties");
    unsignedBlockRequestsCounter =
        BeaconChainRequestCounter.create(
            metricsSystem,
            UNSIGNED_BLOCK_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests for unsigned blocks");
    attestationDataRequestsCounter =
        BeaconChainRequestCounter.create(
            metricsSystem,
            ATTESTATION_DATA_REQUEST_COUNTER_NAME,
            "Counter recording the number of requests for attestation data");
    aggregateRequestsCounter =
        BeaconChainRequestCounter.create(
            metricsSystem,
            AGGREGATE_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests for aggregate attestations");
    createSyncCommitteeContributionCounter =
        BeaconChainRequestCounter.create(
            metricsSystem,
            CREATE_SYNC_COMMITTEE_CONTRIBUTION_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests for aggregate attestations");
    getValidatorIndicesRequestCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.VALIDATOR,
            GET_VALIDATOR_INDICES_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests for validator indices");
    subscribeAggregationRequestCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.VALIDATOR,
            AGGREGATION_SUBSCRIPTION_COUNTER_NAME,
            "Counter recording the number of requests to subscribe to committees for aggregation");
    subscribePersistentRequestCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.VALIDATOR,
            PERSISTENT_SUBSCRIPTION_COUNTER_NAME,
            "Counter recording the number of requests to subscribe to persistent committees");
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
    subscribeSyncCommitteeRequestCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.VALIDATOR,
            SYNC_COMMITTEE_SUBNET_SUBSCRIPTION_NAME,
            "Counter recording the number of subscription requests for sync committee subnets sent to the beacon node");
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
    prepareBeaconProposerCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.VALIDATOR,
            PREPARE_BEACON_PROPOSER_NAME,
            "Counter recording the number of prepare beacon proposer requests sent to the beacon node");
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return countDataRequest(delegate.getGenesisData(), genesisTimeRequestCounter);
  }

  @Override
  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final Collection<BLSPublicKey> publicKeys) {
    getValidatorIndicesRequestCounter.inc();
    return delegate.getValidatorIndices(publicKeys);
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      final Collection<BLSPublicKey> validatorIdentifiers) {
    return delegate.getValidatorStatuses(validatorIdentifiers);
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndexes) {
    return countDataRequest(
        delegate.getAttestationDuties(epoch, validatorIndexes), attestationDutiesRequestCounter);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    return countDataRequest(
        delegate.getSyncCommitteeDuties(epoch, validatorIndices),
        syncCommitteeDutiesRequestCounter);
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(final UInt64 epoch) {
    return countDataRequest(delegate.getProposerDuties(epoch), proposerDutiesRequestCounter);
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      final UInt64 slot, final BLSSignature randaoReveal, Optional<Bytes32> graffiti) {
    return countDataRequest(
        delegate.createUnsignedBlock(slot, randaoReveal, graffiti), unsignedBlockRequestsCounter);
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return countDataRequest(
        delegate.createAttestationData(slot, committeeIndex), attestationDataRequestsCounter);
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    return countDataRequest(
        delegate.createAggregate(slot, attestationHashTreeRoot), aggregateRequestsCounter);
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeContribution>> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    return countDataRequest(
        delegate.createSyncCommitteeContribution(slot, subcommitteeIndex, beaconBlockRoot),
        createSyncCommitteeContributionCounter);
  }

  @Override
  public void subscribeToBeaconCommittee(final List<CommitteeSubscriptionRequest> requests) {
    subscribeAggregationRequestCounter.inc();
    delegate.subscribeToBeaconCommittee(requests);
  }

  @Override
  public void subscribeToSyncCommitteeSubnets(
      final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    subscribeSyncCommitteeRequestCounter.inc();
    delegate.subscribeToSyncCommitteeSubnets(subscriptions);
  }

  @Override
  public void subscribeToPersistentSubnets(final Set<SubnetSubscription> subnetSubscriptions) {
    subscribePersistentRequestCounter.inc();
    delegate.subscribeToPersistentSubnets(subnetSubscriptions);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSignedAttestations(
      final List<Attestation> attestations) {
    return countSendRequest(
        delegate.sendSignedAttestations(attestations), sendAttestationRequestCounter);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return countSendRequest(
        delegate.sendAggregateAndProofs(aggregateAndProofs), sendAggregateRequestCounter);
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(final SignedBeaconBlock block) {
    sendBlockRequestCounter.inc();
    return delegate.sendSignedBlock(block);
  }

  @Override
  public SafeFuture<List<SubmitDataError>> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return countSendRequest(
        delegate.sendSyncCommitteeMessages(syncCommitteeMessages),
        sendSyncCommitteeMessagesRequestCounter);
  }

  @Override
  public SafeFuture<Void> sendSignedContributionAndProofs(
      final Collection<SignedContributionAndProof> signedContributionAndProofs) {
    sendContributionAndProofsRequestCounter.inc();
    return delegate.sendSignedContributionAndProofs(signedContributionAndProofs);
  }

  @Override
  public void prepareBeaconProposer(
      Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    prepareBeaconProposerCounter.inc();
    delegate.prepareBeaconProposer(beaconPreparableProposers);
  }

  private <T> SafeFuture<List<T>> countSendRequest(
      final SafeFuture<List<T>> request, final BeaconChainRequestCounter counter) {
    return request
        .catchAndRethrow(__ -> counter.onError())
        .thenPeek(
            result -> {
              if (result.isEmpty()) {
                counter.onSuccess();
              } else {
                counter.onError();
              }
            });
  }

  private <T> SafeFuture<Optional<T>> countDataRequest(
      final SafeFuture<Optional<T>> request, final BeaconChainRequestCounter counter) {
    return request
        .catchAndRethrow(__ -> counter.onError())
        .thenPeek(
            result ->
                result.ifPresentOrElse(__ -> counter.onSuccess(), counter::onDataUnavailable));
  }
}
