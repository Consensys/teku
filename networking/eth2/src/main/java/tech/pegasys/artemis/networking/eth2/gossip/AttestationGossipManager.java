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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.artemis.networking.p2p.gossip.TopicChannel;
import tech.pegasys.artemis.statetransition.events.committee.CommitteeAssignmentEvent;
import tech.pegasys.artemis.statetransition.events.committee.CommitteeDismissalEvent;
import tech.pegasys.artemis.storage.client.RecentChainData;

public class AttestationGossipManager {
  private static final Logger LOG = LogManager.getLogger();

  private final EventBus eventBus;
  private final AttestationSubnetSubscriptions subnetSubscriptions;
  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  public AttestationGossipManager(
      final GossipNetwork gossipNetwork,
      final EventBus eventBus,
      final RecentChainData recentChainData) {
    subnetSubscriptions =
        new AttestationSubnetSubscriptions(gossipNetwork, recentChainData, eventBus);
    this.eventBus = eventBus;
    eventBus.register(this);
  }

  @Subscribe
  public void onNewAttestation(final Attestation attestation) {
    final UnsignedLong committeeIndex = attestation.getData().getIndex();
    final Optional<TopicChannel> channel = subnetSubscriptions.getChannel(committeeIndex);
    if (channel.isEmpty()) {
      // We're not managing attestations for this committee right now
      LOG.trace(
          "Ignoring attestation for committee {}, which does not correspond to any currently assigned committee.",
          committeeIndex);
      return;
    }
    final Bytes data = SimpleOffsetSerializer.serialize(attestation);
    channel.get().gossip(data);
  }

  @Subscribe
  public void onCommitteeAssignment(CommitteeAssignmentEvent assignmentEvent) {
    for (int committeeIndex : assignmentEvent.getCommitteeIndices()) {
      subnetSubscriptions.subscribeToCommitteeTopic(UnsignedLong.valueOf(committeeIndex));
    }
  }

  public void subscribeToCommitteeTopic(final int committeeIndex) {
    subnetSubscriptions.subscribeToCommitteeTopic(UnsignedLong.valueOf(committeeIndex));
  }

  @Subscribe
  public void onCommitteeDismissal(CommitteeDismissalEvent dismissalEvent) {
    List<Integer> committeeIndices = dismissalEvent.getCommitteeIndices();
    for (int committeeIndex : committeeIndices) {
      unsubscribeFromCommitteeTopic(committeeIndex);
    }
  }

  public void unsubscribeFromCommitteeTopic(final int committeeIndex) {
    subnetSubscriptions.unsubscribeFromCommitteeTopic(UnsignedLong.valueOf(committeeIndex));
  }

  public void shutdown() {
    if (shutdown.compareAndSet(false, true)) {
      eventBus.unregister(this);
      subnetSubscriptions.close();
    }
  }
}
