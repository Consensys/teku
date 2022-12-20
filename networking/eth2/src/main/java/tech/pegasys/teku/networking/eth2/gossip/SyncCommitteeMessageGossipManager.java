/*
 * Copyright ConsenSys Software Inc., 2022
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubnetSubscriptions;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeStateUtils;

public class SyncCommitteeMessageGossipManager implements GossipManager {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final SyncCommitteeStateUtils syncCommitteeStateUtils;
  private final SyncCommitteeSubnetSubscriptions subnetSubscriptions;

  private final Counter publishSuccessCounter;
  private final Counter publishFailureCounter;

  private final GossipFailureLogger gossipFailureLogger =
      new GossipFailureLogger("sync committee message");

  public SyncCommitteeMessageGossipManager(
      final MetricsSystem metricsSystem,
      final Spec spec,
      final SyncCommitteeStateUtils syncCommitteeStateUtils,
      final SyncCommitteeSubnetSubscriptions subnetSubscriptions) {
    this.spec = spec;
    this.syncCommitteeStateUtils = syncCommitteeStateUtils;
    this.subnetSubscriptions = subnetSubscriptions;
    final LabelledMetric<Counter> publishedSyncCommitteeCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            "published_sync_committee_message_total",
            "Total number of sync committee messages sent to the gossip network",
            "result");
    publishSuccessCounter = publishedSyncCommitteeCounter.labels("success");
    publishFailureCounter = publishedSyncCommitteeCounter.labels("failure");
  }

  public void publish(final ValidateableSyncCommitteeMessage message) {
    if (message.getReceivedSubnetId().isPresent()) {
      // Republish only on the subnet we received it on
      publish(message.getMessage(), message.getReceivedSubnetId().getAsInt());
    } else {
      // Publish locally produced messages to all applicable subnets
      final Optional<SyncSubcommitteeAssignments> subcommitteeAssignments =
          message.getSubcommitteeAssignments();
      if (subcommitteeAssignments.isPresent()) {
        publish(message, subcommitteeAssignments.get().getAssignedSubcommittees());
      } else {
        syncCommitteeStateUtils
            .getStateForSyncCommittee(message.getSlot())
            .finish(
                maybeState ->
                    maybeState.ifPresentOrElse(
                        state ->
                            publish(
                                message,
                                message
                                    .calculateAssignments(spec, state)
                                    .getAssignedSubcommittees()),
                        () ->
                            LOG.error(
                                "Failed to publish sync committee message for slot {} because the applicable subnets could not be calculated",
                                message.getSlot())),
                error ->
                    LOG.error(
                        "Failed to retrieve state for sync committee message at slot {}",
                        message.getSlot(),
                        error));
      }
    }
  }

  private void publish(
      final ValidateableSyncCommitteeMessage message, final Set<Integer> subnetIds) {
    subnetIds.forEach(subnetId -> publish(message.getMessage(), subnetId));
  }

  private void publish(final SyncCommitteeMessage message, final int subnetId) {
    subnetSubscriptions
        .gossip(message, subnetId)
        .finish(
            __ -> {
              LOG.trace(
                  "Successfully published sync committee message for slot {}", message.getSlot());
              publishSuccessCounter.inc();
            },
            error -> {
              gossipFailureLogger.logWithSuppression(error, message.getSlot());
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
  public void subscribe() {
    subnetSubscriptions.subscribe();
  }

  @Override
  public void unsubscribe() {
    subnetSubscriptions.unsubscribe();
  }
}
