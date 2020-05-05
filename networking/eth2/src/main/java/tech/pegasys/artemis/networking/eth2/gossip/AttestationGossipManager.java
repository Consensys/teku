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
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.networking.eth2.gossip.encoding.GossipEncoding;

public class AttestationGossipManager {
  private static final Logger LOG = LogManager.getLogger();

  private final GossipEncoding gossipEncoding;
  private final AttestationSubnetSubscriptions subnetSubscriptions;
  private final EventBus eventBus;

  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  public AttestationGossipManager(
      final GossipEncoding gossipEncoding,
      final AttestationSubnetSubscriptions attestationSubnetSubscriptions,
      final EventBus eventBus) {
    this.gossipEncoding = gossipEncoding;
    subnetSubscriptions = attestationSubnetSubscriptions;
    this.eventBus = eventBus;
    eventBus.register(this);
  }

  @Subscribe
  public void onNewAttestation(final Attestation attestation) {
    final UnsignedLong committeeIndex = attestation.getData().getIndex();
    subnetSubscriptions
        .getChannel(committeeIndex)
        .ifPresentOrElse(
            channel -> channel.gossip(gossipEncoding.encode(attestation)),
            () ->
                LOG.trace(
                    "Ignoring attestation for committee {}, which does not correspond to any currently assigned committee.",
                    committeeIndex));
  }

  public void subscribeToCommitteeTopic(final int committeeIndex) {
    subnetSubscriptions.subscribeToCommitteeTopic(UnsignedLong.valueOf(committeeIndex));
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
