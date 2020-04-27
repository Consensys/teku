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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationGossipManagerTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final String topicRegex = "/eth2/committee_index\\d+_beacon_attestation/ssz";
  private final EventBus eventBus = new EventBus();
  private final RecentChainData storageClient = MemoryOnlyRecentChainData.create(eventBus);
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final TopicChannel topicChannel = mock(TopicChannel.class);
  private AttestationSubnetSubscriptions attestationSubnetSubscriptions;
  private AttestationGossipManager attestationGossipManager;

  @BeforeEach
  public void setup() {
    BeaconChainUtil.initializeStorage(storageClient, Collections.emptyList());
    doReturn(topicChannel)
        .when(gossipNetwork)
        .subscribe(argThat((val) -> val.matches(topicRegex)), any());
    attestationSubnetSubscriptions =
        new AttestationSubnetSubscriptions(gossipNetwork, storageClient, eventBus);
    attestationGossipManager =
        new AttestationGossipManager(eventBus, attestationSubnetSubscriptions);
  }

  @Test
  public void onNewAttestation_afterMatchingAssignment() {
    // Setup committee assignment
    final int committeeIndex = 2;
    attestationGossipManager.subscribeToCommitteeTopic(committeeIndex);

    // Post new attestation
    final Attestation attestation = dataStructureUtil.randomAttestation();
    setCommitteeIndex(attestation, committeeIndex);
    final Bytes serialized = SimpleOffsetSerializer.serialize(attestation);
    eventBus.post(attestation);

    verify(topicChannel).gossip(serialized);

    // We should process attestations for different committees on the same subnet
    final Attestation attestation2 = dataStructureUtil.randomAttestation();
    setCommitteeIndex(attestation2, committeeIndex + ATTESTATION_SUBNET_COUNT);
    final Bytes serialized2 = SimpleOffsetSerializer.serialize(attestation2);
    eventBus.post(attestation2);

    verify(topicChannel).gossip(serialized2);
  }

  @Test
  public void onNewAttestation_noMatchingAssignment() {
    // Setup committee assignment
    final int committeeIndex = 2;
    attestationGossipManager.subscribeToCommitteeTopic(committeeIndex);

    // Post new attestation
    final Attestation attestation = dataStructureUtil.randomAttestation();
    setCommitteeIndex(attestation, committeeIndex + 1);
    eventBus.post(attestation);

    verifyNoInteractions(topicChannel);
  }

  @Test
  public void onNewAttestation_afterDismissal() {
    // Setup committee assignment
    final int committeeIndex = 2;
    final int dismissedIndex = 3;
    attestationGossipManager.subscribeToCommitteeTopic(committeeIndex);

    // Unassign
    attestationGossipManager.unsubscribeFromCommitteeTopic(dismissedIndex);

    // Attestation for dismissed assignment should be ignored
    final Attestation attestation = dataStructureUtil.randomAttestation();
    setCommitteeIndex(attestation, dismissedIndex);
    final Bytes serialized = SimpleOffsetSerializer.serialize(attestation);
    eventBus.post(attestation);
    verify(topicChannel, never()).gossip(serialized);

    // Attestation for remaining assignment should be processed
    final Attestation attestation2 = dataStructureUtil.randomAttestation();
    setCommitteeIndex(attestation2, committeeIndex);
    final Bytes serialized2 = SimpleOffsetSerializer.serialize(attestation2);
    eventBus.post(attestation2);
    verify(topicChannel).gossip(serialized2);
  }

  public void setCommitteeIndex(final Attestation attestation, final int committeeIndex) {
    final AttestationData data = attestation.getData();
    attestation.setData(
        new AttestationData(
            data.getSlot(),
            UnsignedLong.valueOf(committeeIndex),
            data.getBeacon_block_root(),
            data.getSource(),
            data.getTarget()));
  }
}
