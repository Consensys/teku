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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipedAttestationConsumer;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.AttestationValidator;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationGossipManagerTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final GossipedAttestationConsumer gossipedAttestationConsumer =
      mock(GossipedAttestationConsumer.class);
  private final AttestationValidator attestationValidator = mock(AttestationValidator.class);
  private final RecentChainData recentChainData =
      MemoryOnlyRecentChainData.create(mock(EventBus.class));
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final TopicChannel topicChannel = mock(TopicChannel.class);
  private AttestationGossipManager attestationGossipManager;
  private final AttestationSubnetSubscriptions attestationSubnetSubscriptions =
      new AttestationSubnetSubscriptions(
          gossipNetwork,
          gossipEncoding,
          attestationValidator,
          recentChainData,
          gossipedAttestationConsumer);

  @BeforeEach
  public void setup() {
    BeaconChainUtil.create(0, recentChainData).initializeStorage();
    doReturn(topicChannel).when(gossipNetwork).subscribe(contains("beacon_attestation"), any());
    attestationGossipManager =
        new AttestationGossipManager(gossipEncoding, attestationSubnetSubscriptions);
  }

  @Test
  public void onNewAttestation_afterMatchingAssignment() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final Attestation attestation2 =
        new Attestation(
            dataStructureUtil.randomBitlist(),
            dataStructureUtil.randomAttestationData(UnsignedLong.valueOf(13)),
            dataStructureUtil.randomSignature());
    final int subnetId = computeSubnetId(attestation);
    // Sanity check the attestations are for the same subnet
    assertThat(computeSubnetId(attestation2)).isEqualTo(subnetId);
    // Setup committee assignment
    attestationGossipManager.subscribeToSubnetId(subnetId);

    // Post new attestation
    final Bytes serialized = gossipEncoding.encode(attestation);
    attestationGossipManager.onNewAttestation(ValidateableAttestation.fromAttestation(attestation));

    verify(topicChannel).gossip(serialized);

    // We should process attestations for different committees on the same subnet
    final Bytes serialized2 = gossipEncoding.encode(attestation2);
    attestationGossipManager.onNewAttestation(
        ValidateableAttestation.fromAttestation(attestation2));

    verify(topicChannel).gossip(serialized2);
  }

  @Test
  public void onNewAttestation_noMatchingAssignment() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final int subnetId = computeSubnetId(attestation);
    // Subscribed to different subnet
    attestationGossipManager.subscribeToSubnetId(subnetId + 1);

    // Post new attestation
    attestationGossipManager.onNewAttestation(ValidateableAttestation.fromAttestation(attestation));

    verifyNoInteractions(topicChannel);
  }

  @Test
  public void onNewAttestation_afterDismissal() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final Attestation attestation2 = dataStructureUtil.randomAttestation();
    // Setup committee assignment
    final int subnetId = computeSubnetId(attestation2);
    final int dismissedSubnetId = computeSubnetId(attestation);
    attestationGossipManager.subscribeToSubnetId(subnetId);

    // Unassign
    attestationGossipManager.unsubscribeFromSubnetId(dismissedSubnetId);

    // Attestation for dismissed assignment should be ignored
    final Bytes serialized = gossipEncoding.encode(attestation);
    attestationGossipManager.onNewAttestation(ValidateableAttestation.fromAttestation(attestation));

    verify(topicChannel, never()).gossip(serialized);

    // Attestation for remaining assignment should be processed
    final Bytes serialized2 = gossipEncoding.encode(attestation2);
    attestationGossipManager.onNewAttestation(
        ValidateableAttestation.fromAttestation(attestation2));

    verify(topicChannel).gossip(serialized2);
  }

  private Integer computeSubnetId(final Attestation attestation) {
    return CommitteeUtil.computeSubnetForAttestation(
        recentChainData.getBestState().orElseThrow(), attestation);
  }
}
