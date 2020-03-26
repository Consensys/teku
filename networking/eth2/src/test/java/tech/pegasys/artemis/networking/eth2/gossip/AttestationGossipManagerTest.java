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

package tech.pegasys.artemis.networking.eth2.gossip;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.artemis.networking.p2p.gossip.TopicChannel;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.statetransition.events.committee.CommitteeAssignmentEvent;
import tech.pegasys.artemis.statetransition.events.committee.CommitteeDismissalEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.api.DiskUpdateChannel;

public class AttestationGossipManagerTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final String topicRegex = "/eth2/index\\d+_beacon_attestation/ssz";
  private final EventBus eventBus = new EventBus();
  private final DiskUpdateChannel diskUpdateChannel = mock(DiskUpdateChannel.class);
  private final ChainStorageClient storageClient =
      ChainStorageClient.memoryOnlyClient(eventBus, diskUpdateChannel);
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final TopicChannel topicChannel = mock(TopicChannel.class);

  @BeforeEach
  public void setup() {
    BeaconChainUtil.initializeStorage(storageClient, Collections.emptyList());
    doReturn(topicChannel)
        .when(gossipNetwork)
        .subscribe(argThat((val) -> val.matches(topicRegex)), any());
    new AttestationGossipManager(gossipNetwork, eventBus, storageClient);
  }

  @Test
  public void onNewAttestation_afterMatchingAssignment() {
    // Setup committee assignment
    final int committeeIndex = 2;
    final CommitteeAssignmentEvent assignment =
        new CommitteeAssignmentEvent(List.of(committeeIndex));
    eventBus.post(assignment);

    // Post new attestation
    final Attestation attestation = dataStructureUtil.randomAttestation();
    attestation.setData(attestation.getData().withIndex(UnsignedLong.valueOf(committeeIndex)));
    final Bytes serialized = SimpleOffsetSerializer.serialize(attestation);
    eventBus.post(attestation);

    verify(topicChannel).gossip(serialized);
  }

  @Test
  public void onNewAttestation_noMatchingAssignment() {
    // Setup committee assignment
    final int committeeIndex = 2;
    final CommitteeAssignmentEvent assignment =
        new CommitteeAssignmentEvent(List.of(committeeIndex));
    eventBus.post(assignment);

    // Post new attestation
    final Attestation attestation = dataStructureUtil.randomAttestation();
    attestation.setData(attestation.getData().withIndex(UnsignedLong.valueOf(committeeIndex + 1)));
    eventBus.post(attestation);

    verifyNoInteractions(topicChannel);
  }

  @Test
  public void onNewAttestation_afterDismissal() {
    // Setup committee assignment
    final int committeeIndex = 2;
    final int dismissedIndex = 3;
    final CommitteeAssignmentEvent assignment =
        new CommitteeAssignmentEvent(List.of(committeeIndex, dismissedIndex));
    eventBus.post(assignment);

    // Unassign
    final CommitteeDismissalEvent dismissalEvent =
        new CommitteeDismissalEvent(List.of(dismissedIndex));
    eventBus.post(dismissalEvent);

    // Attestation for dismissed assignment should be ignored
    final Attestation attestation = dataStructureUtil.randomAttestation();
    attestation.setData(attestation.getData().withIndex(UnsignedLong.valueOf(dismissedIndex)));
    final Bytes serialized = SimpleOffsetSerializer.serialize(attestation);
    eventBus.post(attestation);
    verify(topicChannel, never()).gossip(serialized);

    // Attestation for remaining assignment should be processed
    final Attestation attestation2 = dataStructureUtil.randomAttestation();
    attestation2.setData(attestation.getData().withIndex(UnsignedLong.valueOf(committeeIndex)));
    final Bytes serialized2 = SimpleOffsetSerializer.serialize(attestation2);
    eventBus.post(attestation2);
    verify(topicChannel).gossip(serialized2);
  }
}
