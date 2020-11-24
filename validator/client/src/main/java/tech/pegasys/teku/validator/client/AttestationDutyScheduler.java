/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.validator.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AttestationDutyScheduler extends AbstractDutyScheduler {
  private UInt64 lastAttestationCreationSlot;
  private static final Logger LOG = LogManager.getLogger();
  static final int LOOKAHEAD_EPOCHS = 1;

  public AttestationDutyScheduler(
      final MetricsSystem metricsSystem, final DutyLoader epochDutiesScheduler) {
    super(epochDutiesScheduler, LOOKAHEAD_EPOCHS);

    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "scheduled_attestation_duties_current",
        "Current number of pending attestation duties that have been scheduled",
        () -> dutiesByEpoch.values().stream().mapToInt(DutyQueue::countDuties).sum());
  }

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {
    // Check slot being null for the edge case of genesis slot (i.e. slot 0)
    if (lastAttestationCreationSlot != null && slot.compareTo(lastAttestationCreationSlot) <= 0) {
      LOG.debug(
          "lastAttestationCreationSlot {} is ahead of current slot {}, not processing attestation duty.",
          lastAttestationCreationSlot,
          slot);
      return;
    }

    if (!isAbleToVerifyEpoch(slot)) {
      LOG.info(
          "current epoch {} could not be verified against the current slot {}, not processing attestation duty",
          getCurrentEpoch().map(UInt64::toString).orElse("UNDEFINED"),
          slot);
      return;
    }

    lastAttestationCreationSlot = slot;
    notifyDutyQueue(DutyQueue::onAttestationCreationDue, slot);
  }

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {
    if (!isAbleToVerifyEpoch(slot)) {
      LOG.info(
          "current epoch {} could not be verified against the current slot {}, not processing aggregation duty",
          getCurrentEpoch().map(UInt64::toString).orElse("UNDEFINED"),
          slot);
      return;
    }

    notifyDutyQueue(DutyQueue::onAttestationAggregationDue, slot);
  }
}
