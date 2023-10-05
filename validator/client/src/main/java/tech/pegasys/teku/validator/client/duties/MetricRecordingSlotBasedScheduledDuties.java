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

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MetricRecordingSlotBasedScheduledDuties implements ScheduledDuties {

  private final ScheduledDuties delegate;
  private final OperationTimer timer;

  public MetricRecordingSlotBasedScheduledDuties(
      final MetricsSystem metricsSystem, final ScheduledDuties delegate) {
    this.delegate = delegate;
    this.timer =
        metricsSystem.createTimer(
            TekuMetricCategory.VALIDATOR, "", "Timer recording the time taken to perform a duty");
    // todo potentially 3 timers - create attestation, block, aggregation (3 different names)
  }

  @Override
  public boolean requiresRecalculation(Bytes32 newHeadDependentRoot) {
    return delegate.requiresRecalculation(newHeadDependentRoot);
  }

  @Override
  public SafeFuture<DutyResult> performProductionDuty(UInt64 slot) {
    return recordMetric(delegate.performProductionDuty(slot));
  }

  @Override
  public String getProductionType() {
    return delegate.getProductionType();
  }

  @Override
  public SafeFuture<DutyResult> performAggregationDuty(UInt64 slot) {
    return recordMetric(delegate.performAggregationDuty(slot));
  }

  @Override
  public String getAggregationType() {
    return delegate.getAggregationType();
  }

  @Override
  public int countDuties() {
    return delegate.countDuties();
  }

  private SafeFuture<DutyResult> recordMetric(final SafeFuture<DutyResult> request) {
    OperationTimer.TimingContext context = timer.startTimer();
    return request.thenApply(
        result -> {
          context.stopTimer();
          return result;
        });
    // todo if error stop the time (maybe thenPeek)
    // todo figure out type of duty to record
  }
}
