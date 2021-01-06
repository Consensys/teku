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

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AttestationDutyScheduler extends AbstractDutyScheduler {
  private UInt64 lastAttestationCreationSlot;
  private static final Logger LOG = LogManager.getLogger();
  static final int LOOKAHEAD_EPOCHS = 1;

  public AttestationDutyScheduler(
      final MetricsSystem metricsSystem,
      final DutyLoader epochDutiesScheduler,
      final boolean useDependentRoots) {
    super(epochDutiesScheduler, LOOKAHEAD_EPOCHS, useDependentRoots);

    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "scheduled_attestation_duties_current",
        "Current number of pending attestation duties that have been scheduled",
        () -> dutiesByEpoch.values().stream().mapToInt(EpochDuties::countDuties).sum());
  }

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {
    // Check slot being null for the edge case of genesis slot (i.e. slot 0)
    if (lastAttestationCreationSlot != null && slot.compareTo(lastAttestationCreationSlot) <= 0) {
      LOG.debug(
          "Not performing attestation duties for slot {} because lastAttestationCreationSlot {} is beyond that.",
          slot,
          lastAttestationCreationSlot);
      return;
    }

    if (!isAbleToVerifyEpoch(slot)) {
      LOG.info(
          "Not performing attestation duties for slot {} because it is too far ahead of the current slot {}",
          slot,
          getCurrentEpoch().map(UInt64::toString).orElse("UNDEFINED"));
      return;
    }

    lastAttestationCreationSlot = slot;
    notifyEpochDuties(EpochDuties::onAttestationCreationDue, slot);
  }

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {
    if (!isAbleToVerifyEpoch(slot)) {
      LOG.info(
          "Not performing aggregation duties for slot {} because it is too far ahead of the current slot {}",
          slot,
          getCurrentEpoch().map(UInt64::toString).orElse("UNDEFINED"));
      return;
    }

    notifyEpochDuties(EpochDuties::onAttestationAggregationDue, slot);
  }

  @Override
  protected Bytes32 getExpectedDependentRoot(
      final Bytes32 headBlockRoot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final UInt64 headEpoch,
      final UInt64 dutyEpoch) {
    checkArgument(
        dutyEpoch.isGreaterThanOrEqualTo(headEpoch),
        "Attempting to calculate dependent root for duty epoch %s that is before the updated head epoch %s",
        dutyEpoch,
        headEpoch);
    if (headEpoch.equals(dutyEpoch)) {
      return previousDutyDependentRoot;
    } else if (headEpoch.plus(1).equals(dutyEpoch)) {
      return currentDutyDependentRoot;
    } else {
      return headBlockRoot;
    }
  }
}
