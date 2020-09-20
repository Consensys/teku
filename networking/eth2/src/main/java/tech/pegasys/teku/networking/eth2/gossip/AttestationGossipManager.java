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

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationSubnetSubscriptions;

public class AttestationGossipManager {
  private static final Logger LOG = LogManager.getLogger();

  private final AttestationSubnetSubscriptions subnetSubscriptions;

  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  private final Counter attestationPublishSuccessCounter;
  private final Counter attestationPublishFailureCounter;

  public AttestationGossipManager(
      final MetricsSystem metricsSystem,
      final AttestationSubnetSubscriptions attestationSubnetSubscriptions) {
    subnetSubscriptions = attestationSubnetSubscriptions;
    final LabelledMetric<Counter> publishedAttestationCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            "published_attestation_total",
            "Total number of attestations sent to the gossip network",
            "result");
    attestationPublishSuccessCounter = publishedAttestationCounter.labels("success");
    attestationPublishFailureCounter = publishedAttestationCounter.labels("failure");
  }

  public void onNewAttestation(final ValidateableAttestation validateableAttestation) {
    if (validateableAttestation.isAggregate() || !validateableAttestation.markGossiped()) {
      return;
    }
    final Attestation attestation = validateableAttestation.getAttestation();
    subnetSubscriptions
        .gossip(attestation)
        .finish(
            __ -> {
              LOG.trace(
                  "Successfully published attestation for slot {}",
                  attestation.getData().getSlot());
              attestationPublishSuccessCounter.inc();
            },
            error -> {
              LOG.trace(
                  "Failed to publish attestation for slot {}",
                  attestation.getData().getSlot(),
                  error);
              attestationPublishFailureCounter.inc();
            });
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
