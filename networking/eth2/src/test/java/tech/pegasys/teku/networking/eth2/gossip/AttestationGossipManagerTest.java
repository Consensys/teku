/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.util.config.Constants.GOSSIP_MAX_SIZE;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubCounter;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationGossipManagerTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @SuppressWarnings("unchecked")
  private final OperationProcessor<ValidateableAttestation> gossipedAttestationProcessor =
      mock(OperationProcessor.class);

  private final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(spec);
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private AttestationGossipManager attestationGossipManager;
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final ForkInfo forkInfo =
      new ForkInfo(spec.fork(UInt64.ZERO), dataStructureUtil.randomBytes32());
  private final AttestationSubnetSubscriptions attestationSubnetSubscriptions =
      new AttestationSubnetSubscriptions(
          asyncRunner,
          gossipNetwork,
          gossipEncoding,
          recentChainData,
          gossipedAttestationProcessor,
          forkInfo,
          GOSSIP_MAX_SIZE);

  @BeforeEach
  public void setup() {
    BeaconChainUtil.create(spec, 0, recentChainData).initializeStorage();
    attestationGossipManager =
        new AttestationGossipManager(metricsSystem, attestationSubnetSubscriptions);
  }

  @Test
  public void onNewAttestation_afterMatchingAssignment() {
    // Create a new DataStructureUtil so that generated attestations are not subject to change
    // when access to the global DataStructureUtil changes
    DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final Attestation attestation = dataStructureUtil.randomAttestation(3);
    final Attestation attestation2 =
        new Attestation(
            dataStructureUtil.randomBitlist(),
            dataStructureUtil.randomAttestationData(UInt64.valueOf(13)),
            dataStructureUtil.randomSignature());
    final int subnetId = computeSubnetId(attestation);
    // Sanity check the attestations are for the same subnet
    assertThat(computeSubnetId(attestation2)).isEqualTo(subnetId);
    // Setup committee assignment
    attestationGossipManager.subscribeToSubnetId(subnetId);

    // Post new attestation
    final Bytes serialized = gossipEncoding.encode(attestation);
    attestationGossipManager.onNewAttestation(ValidateableAttestation.from(spec, attestation));

    verify(gossipNetwork).gossip(getSubnetTopic(subnetId), serialized);

    // We should process attestations for different committees on the same subnet
    final Bytes serialized2 = gossipEncoding.encode(attestation2);
    attestationGossipManager.onNewAttestation(ValidateableAttestation.from(spec, attestation2));

    verify(gossipNetwork).gossip(getSubnetTopic(subnetId), serialized2);
  }

  @Test
  public void onNewAttestation_noMatchingAssignment() {
    final Attestation attestation = dataStructureUtil.randomAttestation(2);
    final int subnetId = computeSubnetId(attestation);
    // Subscribed to different subnet
    attestationGossipManager.subscribeToSubnetId(subnetId + 1);

    // Post new attestation
    attestationGossipManager.onNewAttestation(ValidateableAttestation.from(spec, attestation));

    verify(gossipNetwork).gossip(getSubnetTopic(subnetId), gossipEncoding.encode(attestation));
  }

  @Test
  public void onNewAttestation_afterDismissal() {
    final Attestation attestation = dataStructureUtil.randomAttestation(1);
    final Attestation attestation2 = dataStructureUtil.randomAttestation(1);
    // Setup committee assignment
    final int subnetId = computeSubnetId(attestation2);
    final int dismissedSubnetId = computeSubnetId(attestation);
    attestationGossipManager.subscribeToSubnetId(subnetId);

    // Unassign
    attestationGossipManager.unsubscribeFromSubnetId(dismissedSubnetId);

    // Attestation for dismissed assignment should be ignored
    final Bytes serialized = gossipEncoding.encode(attestation);
    attestationGossipManager.onNewAttestation(ValidateableAttestation.from(spec, attestation));

    verify(gossipNetwork).gossip(getSubnetTopic(dismissedSubnetId), serialized);

    // Attestation for remaining assignment should be processed
    final Bytes serialized2 = gossipEncoding.encode(attestation2);
    attestationGossipManager.onNewAttestation(ValidateableAttestation.from(spec, attestation2));

    verify(gossipNetwork).gossip(getSubnetTopic(subnetId), serialized2);
  }

  @Test
  void onNewAttestation_incrementSuccessCount() {
    final Attestation attestation = dataStructureUtil.randomAttestation(3);
    when(gossipNetwork.gossip(any(), any())).thenReturn(SafeFuture.completedFuture(null));

    // Attestation for dismissed assignment should be ignored
    attestationGossipManager.onNewAttestation(ValidateableAttestation.from(spec, attestation));

    assertThat(getPublishSuccessCounterValue()).isEqualTo(1);
    assertThat(getPublishFailureCounterValue()).isZero();
  }

  @Test
  void onNewAttestation_incrementFailureCount() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(gossipNetwork.gossip(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Ooops")));

    // Attestation for dismissed assignment should be ignored
    attestationGossipManager.onNewAttestation(ValidateableAttestation.from(spec, attestation));

    assertThat(getPublishSuccessCounterValue()).isZero();
    assertThat(getPublishFailureCounterValue()).isEqualTo(1);
  }

  private long getPublishSuccessCounterValue() {
    return getPublishCounter().getValue("success");
  }

  private long getPublishFailureCounterValue() {
    return getPublishCounter().getValue("failure");
  }

  private StubCounter getPublishCounter() {
    return metricsSystem.getCounter(TekuMetricCategory.BEACON, "published_attestation_total");
  }

  private Integer computeSubnetId(final Attestation attestation) {
    return spec.computeSubnetForAttestation(
        recentChainData.getBestState().orElseThrow(), attestation);
  }

  private String getSubnetTopic(final int subnetId) {
    return GossipTopics.getAttestationSubnetTopic(
        forkInfo.getForkDigest(spec), subnetId, gossipEncoding);
  }
}
