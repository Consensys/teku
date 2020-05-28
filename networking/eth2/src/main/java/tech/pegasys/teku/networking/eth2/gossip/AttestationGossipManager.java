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

import static tech.pegasys.teku.datastructures.util.CommitteeUtil.committeeIndexToSubnetId;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;

public class AttestationGossipManager {
  private static final Logger LOG = LogManager.getLogger();

  private final GossipEncoding gossipEncoding;
  private final AttestationSubnetSubscriptions subnetSubscriptions;

  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  public AttestationGossipManager(
      final GossipEncoding gossipEncoding,
      final AttestationSubnetSubscriptions attestationSubnetSubscriptions) {
    this.gossipEncoding = gossipEncoding;
    subnetSubscriptions = attestationSubnetSubscriptions;
  }

  public void onNewAttestation(final ValidateableAttestation validateableAttestation) {
    if (validateableAttestation.isAggregate() || !validateableAttestation.markGossiped()) {
      return;
    }
    final Attestation attestation = validateableAttestation.getAttestation();
    final int subnetId = committeeIndexToSubnetId(attestation.getData().getIndex());
    subnetSubscriptions
        .getChannel(subnetId)
        .ifPresentOrElse(
            channel -> channel.gossip(gossipEncoding.encode(attestation)),
            () ->
                LOG.trace(
                    "Ignoring attestation for subnet id {}, which does not correspond to any currently assigned subnet ids.",
                    subnetId));
  }

  public void subscribeToSubnetId(final int subnetId) {
    subnetSubscriptions.subscribeToSubnetId(subnetId);
  }

  public void unsubscribeFromSubnetId(final int subnetId) {
    subnetSubscriptions.unsubscribeFromSubnetId(subnetId);
  }

  public void shutdown() {
    if (shutdown.compareAndSet(false, true)) {
      subnetSubscriptions.close();
    }
  }
}
