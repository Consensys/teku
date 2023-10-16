/*
 * Copyright Consensys Software Inc., 2023
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

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class ValidatorDutyMetrics {
  private final LabelledMetric<OperationTimer> dutyMetric;

  ValidatorDutyMetrics(final LabelledMetric<OperationTimer> dutyMetric) {
    this.dutyMetric = dutyMetric;
  }

  public static ValidatorDutyMetrics create(final MetricsSystem metricsSystem) {
    final LabelledMetric<OperationTimer> dutyMetric =
        metricsSystem.createLabelledTimer(
            TekuMetricCategory.VALIDATOR,
            "duty_timer",
            "Timer recording the time taken to perform a duty",
            "type",
            "step");
    return new ValidatorDutyMetrics(dutyMetric);
  }

  public SafeFuture<DutyResult> performDutyWithMetrics(final Duty duty) {
    final String dutyType = duty.getType().getType();
    final OperationTimer timer = dutyMetric.labels(dutyType, "total");
    final OperationTimer.TimingContext context = timer.startTimer();
    return duty.performDuty().alwaysRun(context::stopTimer);
  }
}
