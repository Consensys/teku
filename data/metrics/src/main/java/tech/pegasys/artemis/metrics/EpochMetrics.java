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

package tech.pegasys.teku.metrics;

import org.hyperledger.besu.plugin.services.MetricsSystem;

public class EpochMetrics {
  private final SettableGauge currentEpochLiveValidators;
  private final SettableGauge previousEpochLiveValidators;
  private final SettableGauge pendingExits;

  public EpochMetrics(final MetricsSystem metricsSystem) {
    currentEpochLiveValidators =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "current_epoch_live_validators",
            "Number of active validators who reported for the current epoch");
    previousEpochLiveValidators =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "previous_epoch_live_validators",
            "Number of active validators who reported for the previous epoch");

    pendingExits =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "pending_exits",
            "Number of pending exits");
  }

  public void onEpoch(
      int prevEpochLiveValidators, int currEpochLiveValidators, long currPendingExits) {
    currentEpochLiveValidators.set(prevEpochLiveValidators);
    previousEpochLiveValidators.set(currEpochLiveValidators);
    pendingExits.set(currPendingExits);
  }
}
