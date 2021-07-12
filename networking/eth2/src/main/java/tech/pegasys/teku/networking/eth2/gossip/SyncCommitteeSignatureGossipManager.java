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

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubnetSubscriptions;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeStateUtils;

public class SyncCommitteeSignatureGossipManager implements GossipManager {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final SyncCommitteeStateUtils syncCommitteeStateUtils;
  private final SyncCommitteeSubnetSubscriptions subnetSubscriptions;
  private final GossipPublisher<ValidateableSyncCommitteeSignature> gossipPublisher;

  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  private final Counter publishSuccessCounter;
  private final Counter publishFailureCounter;
  private final long subscriberId;

  public SyncCommitteeSignatureGossipManager(
      final MetricsSystem metricsSystem,
      final Spec spec,
      final SyncCommitteeStateUtils syncCommitteeStateUtils,
      final SyncCommitteeSubnetSubscriptions subnetSubscriptions,
      final GossipPublisher<ValidateableSyncCommitteeSignature> gossipPublisher) {
    this.spec = spec;
    this.syncCommitteeStateUtils = syncCommitteeStateUtils;
    this.subnetSubscriptions = subnetSubscriptions;
    this.gossipPublisher = gossipPublisher;
    final LabelledMetric<Counter> publishedSyncCommitteeCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            "published_sync_committee_signature_total",
            "Total number of sync committee signatures sent to the gossip network",
            "result");
    publishSuccessCounter = publishedSyncCommitteeCounter.labels("success");
    publishFailureCounter = publishedSyncCommitteeCounter.labels("failure");
    this.subscriberId = gossipPublisher.subscribe(this::publish);
  }

  public void publish(final ValidateableSyncCommitteeSignature signature) {
    if (signature.getReceivedSubnetId().isPresent()) {
      // Republish only on the subnet we received it on
      publish(signature.getSignature(), signature.getReceivedSubnetId().getAsInt());
    } else {
      // Publish locally produced signatures to all applicable subnets
      final Optional<SyncSubcommitteeAssignments> subcommitteeAssignments =
          signature.getSubcommitteeAssignments();
      if (subcommitteeAssignments.isPresent()) {
        publish(signature, subcommitteeAssignments.get().getAssignedSubcommittees());
      } else {
        syncCommitteeStateUtils
            .getStateForSyncCommittee(signature.getSlot())
            .finish(
                maybeState ->
                    maybeState.ifPresentOrElse(
                        state ->
                            publish(
                                signature,
                                signature
                                    .calculateAssignments(spec, state)
                                    .getAssignedSubcommittees()),
                        () ->
                            LOG.error(
                                "Failed to publish sync committee signature for slot {} because the applicable subnets could not be calculated",
                                signature.getSlot())),
                error ->
                    LOG.error(
                        "Failed to retrieve state for sync committee signature at slot {}",
                        signature.getSlot(),
                        error));
      }
    }
  }

  private void publish(
      final ValidateableSyncCommitteeSignature signature, final Set<Integer> subnetIds) {
    subnetIds.forEach(subnetId -> publish(signature.getSignature(), subnetId));
  }

  private void publish(final SyncCommitteeSignature signature, final int subnetId) {
    subnetSubscriptions
        .gossip(signature, subnetId)
        .finish(
            __ -> {
              LOG.trace(
                  "Successfully published sync committee signature for slot {}",
                  signature.getSlot());
              publishSuccessCounter.inc();
            },
            error -> {
              LOG.trace(
                  "Failed to publish sync committee signature for slot {}",
                  signature.getSlot(),
                  error);
              publishFailureCounter.inc();
            });
  }

  public void subscribeToSubnetId(final int subnetId) {
    LOG.trace("Subscribing to subnet ID {}", subnetId);
    subnetSubscriptions.subscribeToSubnetId(subnetId);
  }

  public void unsubscribeFromSubnetId(final int subnetId) {
    LOG.trace("Unsubscribing to subnet ID {}", subnetId);
    subnetSubscriptions.unsubscribeFromSubnetId(subnetId);
  }

  @Override
  public void shutdown() {
    if (shutdown.compareAndSet(false, true)) {
      subnetSubscriptions.close();
      gossipPublisher.unsubscribe(subscriberId);
    }
  }
}
