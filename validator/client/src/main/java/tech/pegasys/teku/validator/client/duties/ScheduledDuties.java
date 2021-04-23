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

package tech.pegasys.teku.validator.client.duties;

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import java.util.NavigableMap;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public abstract class ScheduledDuties {

  protected final ValidatorDutyFactory dutyFactory;
  private final Bytes32 dependentRoot;
  private final LabelledMetric<Counter> dutiesPerformedCounter;

  public ScheduledDuties(
      final ValidatorDutyFactory dutyFactory,
      final Bytes32 dependentRoot,
      final MetricsSystem metricsSystem) {
    this.dutyFactory = dutyFactory;
    this.dependentRoot = dependentRoot;
    dutiesPerformedCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.VALIDATOR,
            "duties_performed",
            "Count of the failed duties, by duty type",
            "type",
            "result");
  }

  public Bytes32 getDependentRoot() {
    return dependentRoot;
  }

  protected void performDutyForSlot(
      final NavigableMap<UInt64, ? extends Duty> duties, final UInt64 slot) {
    discardDutiesBeforeSlot(duties, slot);

    final Duty duty = duties.remove(slot);
    if (duty == null) {
      return;
    }
    duty.performDuty()
        .finish(
            result -> reportDutySuccess(result, duty, slot),
            error -> reportDutyFailure(error, duty, slot));
  }

  private void reportDutyFailure(final Throwable error, final Duty duty, final UInt64 slot) {
    dutiesPerformedCounter.labels(duty.getProducedType(), "failed").inc();
    VALIDATOR_LOGGER.dutyFailed(duty.getProducedType(), slot, duty.getValidatorIdString(), error);
  }

  private void reportDutySuccess(final DutyResult result, final Duty duty, final UInt64 slot) {
    dutiesPerformedCounter.labels(duty.getProducedType(), "success").inc();
    result.report(duty.getProducedType(), slot, duty.getValidatorIdString(), VALIDATOR_LOGGER);
  }

  private void discardDutiesBeforeSlot(
      final NavigableMap<UInt64, ? extends Duty> duties, final UInt64 slot) {
    duties.subMap(UInt64.ZERO, true, slot, false).clear();
  }

  public abstract int countDuties();
}
