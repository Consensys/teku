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
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.genesis.GenesisData;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class MetricRecordingValidatorApiChannel implements ValidatorApiChannel {

  public static final String FORK_REQUESTS_COUNTER_NAME = "beacon_node_fork_info_requests_total";
  public static final String GENESIS_TIME_REQUESTS_COUNTER_NAME =
      "beacon_node_genesis_time_requests_total";
  public static final String GET_VALIDATOR_INDICES_REQUESTS_COUNTER_NAME =
      "beacon_node_get_validator_indices_requests_total";
  public static final String DUTIES_REQUESTS_COUNTER_NAME = "beacon_node_duties_requests_total";
  public static final String ATTESTATION_DUTIES_REQUESTS_COUNTER_NAME =
      "beacon_node_attestation_duties_requests_total";
  public static final String PROPOSER_DUTIES_REQUESTS_COUNTER_NAME =
      "beacon_node_proposer_duties_requests_total";
  public static final String UNSIGNED_BLOCK_REQUESTS_COUNTER_NAME =
      "beacon_node_unsigned_block_requests_total";
  public static final String UNSIGNED_ATTESTATION_REQUEST_COUNTER_NAME =
      "beacon_node_unsigned_attestation_requests_total";
  public static final String ATTESTATION_DATA_REQUEST_COUNTER_NAME =
      "beacon_node_attestation_data_requests_total";
  public static final String AGGREGATE_REQUESTS_COUNTER_NAME =
      "beacon_node_aggregate_requests_total";
  public static final String AGGREGATION_SUBSCRIPTION_COUNTER_NAME =
      "beacon_node_aggregation_subscription_requests_total";
  public static final String PERSISTENT_SUBSCRIPTION_COUNTER_NAME =
      "beacon_node_persistent_subscription_requests_total";
  public static final String PUBLISHED_ATTESTATION_COUNTER_NAME =
      "beacon_node_published_attestation_total";
  public static final String PUBLISHED_AGGREGATE_COUNTER_NAME =
      "beacon_node_published_aggregate_total";
  public static final String PUBLISHED_BLOCK_COUNTER_NAME = "beacon_node_published_block_total";
  private final ValidatorApiChannel delegate;
  private final BeaconChainRequestCounter forkInfoRequestCounter;
  private final BeaconChainRequestCounter genesisTimeRequestCounter;
  private final BeaconChainRequestCounter attestationDutiesRequestCounter;
  private final BeaconChainRequestCounter proposerDutiesRequestCounter;
  private final BeaconChainRequestCounter unsignedBlockRequestsCounter;
  private final BeaconChainRequestCounter unsignedAttestationRequestsCounter;
  private final BeaconChainRequestCounter attestationDataRequestsCounter;
  private final BeaconChainRequestCounter aggregateRequestsCounter;
  private final Counter getValidatorIndicesRequestCounter;
  private final Counter subscribeAggregationRequestCounter;
  private final Counter subscribePersistentRequestCounter;
  private final Counter sendAttestationRequestCounter;
  private final Counter sendAggregateRequestCounter;
  private final Counter sendBlockRequestCounter;

  public MetricRecordingValidatorApiChannel(
      final MetricsSystem metricsSystem, final ValidatorApiChannel delegate) {
    this.delegate = delegate;

    forkInfoRequestCounter =
        BeaconChainRequestCounter.create(
            metricsSystem,
            FORK_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests for fork info");

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
    unsignedAttestationRequestsCounter =
        BeaconChainRequestCounter.create(
            metricsSystem,
            UNSIGNED_ATTESTATION_REQUEST_COUNTER_NAME,
            "Counter recording the number of requests for unsigned attestations");
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
        metricsSystem.createCounter(
            TekuMetricCategory.VALIDATOR,
            PUBLISHED_ATTESTATION_COUNTER_NAME,
            "Counter recording the number of signed attestations sent to the beacon node");
    sendAggregateRequestCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.VALIDATOR,
            PUBLISHED_AGGREGATE_COUNTER_NAME,
            "Counter recording the number of signed aggregate attestations sent to the beacon node");
    sendBlockRequestCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.VALIDATOR,
            PUBLISHED_BLOCK_COUNTER_NAME,
            "Counter recording the number of signed blocks sent to the beacon node");
  }

  @Override
  public SafeFuture<Optional<Fork>> getFork() {
    return countRequest(delegate.getFork(), forkInfoRequestCounter);
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return countRequest(delegate.getGenesisData(), genesisTimeRequestCounter);
  }

  @Override
  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final List<BLSPublicKey> publicKeys) {
    getValidatorIndicesRequestCounter.inc();
    return delegate.getValidatorIndices(publicKeys);
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      final List<BLSPublicKey> validatorIdentifiers) {
    return delegate.getValidatorStatuses(validatorIdentifiers);
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndexes) {
    return countRequest(
        delegate.getAttestationDuties(epoch, validatorIndexes), attestationDutiesRequestCounter);
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(final UInt64 epoch) {
    return countRequest(delegate.getProposerDuties(epoch), proposerDutiesRequestCounter);
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      final UInt64 slot, final BLSSignature randaoReveal, Optional<Bytes32> graffiti) {
    return countRequest(
        delegate.createUnsignedBlock(slot, randaoReveal, graffiti), unsignedBlockRequestsCounter);
  }

  @Override
  public SafeFuture<Optional<Attestation>> createUnsignedAttestation(
      final UInt64 slot, final int committeeIndex) {
    return countRequest(
        delegate.createUnsignedAttestation(slot, committeeIndex),
        unsignedAttestationRequestsCounter);
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return countRequest(
        delegate.createAttestationData(slot, committeeIndex), attestationDataRequestsCounter);
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    return countRequest(
        delegate.createAggregate(slot, attestationHashTreeRoot), aggregateRequestsCounter);
  }

  @Override
  public void subscribeToBeaconCommittee(final List<CommitteeSubscriptionRequest> requests) {
    subscribeAggregationRequestCounter.inc();
    delegate.subscribeToBeaconCommittee(requests);
  }

  @Override
  public void subscribeToPersistentSubnets(final Set<SubnetSubscription> subnetSubscriptions) {
    subscribePersistentRequestCounter.inc();
    delegate.subscribeToPersistentSubnets(subnetSubscriptions);
  }

  @Override
  public void sendSignedAttestation(final Attestation attestation) {
    sendAttestationRequestCounter.inc();
    delegate.sendSignedAttestation(attestation);
  }

  @Override
  public void sendSignedAttestation(
      final Attestation attestation, Optional<Integer> validatorIndex) {
    sendAttestationRequestCounter.inc();
    delegate.sendSignedAttestation(attestation, validatorIndex);
  }

  @Override
  public void sendAggregateAndProof(final SignedAggregateAndProof aggregateAndProof) {
    sendAggregateRequestCounter.inc();
    delegate.sendAggregateAndProof(aggregateAndProof);
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(final SignedBeaconBlock block) {
    sendBlockRequestCounter.inc();
    return delegate.sendSignedBlock(block);
  }

  private <T> SafeFuture<Optional<T>> countRequest(
      final SafeFuture<Optional<T>> request, final BeaconChainRequestCounter counter) {
    return request
        .catchAndRethrow(__ -> counter.onError())
        .thenPeek(
            result ->
                result.ifPresentOrElse(__ -> counter.onSuccess(), counter::onDataUnavailable));
  }
}
