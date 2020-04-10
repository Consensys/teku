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

package tech.pegasys.artemis.metrics;

import com.google.common.primitives.UnsignedLong;
import org.hyperledger.besu.plugin.services.MetricsSystem;

public class EpochMetrics {

  private final SettableGauge currentJustifiedEpoch;
  private final SettableGauge currentFinalizedEpoch;
  private final SettableGauge previousJustifiedEpoch;
  private final SettableGauge currentEpochLiveValidators;
  private final SettableGauge previousEpochLiveValidators;
  private final SettableGauge pendingExits;

  public EpochMetrics(final MetricsSystem metricsSystem) {

    currentJustifiedEpoch =
        SettableGauge.create(
            metricsSystem,
            ArtemisMetricCategory.BEACON,
            "current_justified_epoch",
            "Current justified epoch");
    currentFinalizedEpoch =
        SettableGauge.create(
            metricsSystem,
            ArtemisMetricCategory.BEACON,
            "current_finalized_epoch",
            "Current finalized epoch");
    previousJustifiedEpoch =
        SettableGauge.create(
            metricsSystem,
            ArtemisMetricCategory.BEACON,
            "current_prev_justified_epoch",
            "Current previously justified epoch");

    currentEpochLiveValidators =
        SettableGauge.create(
            metricsSystem,
            ArtemisMetricCategory.BEACON,
            "current_epoch_live_validators",
            "Number of active validators who reported for the current epoch");
    previousEpochLiveValidators =
        SettableGauge.create(
            metricsSystem,
            ArtemisMetricCategory.BEACON,
            "previous_epoch_live_validators",
            "Number of active validators who reported for the previous epoch");

    pendingExits =
        SettableGauge.create(
            metricsSystem,
            ArtemisMetricCategory.BEACON,
            "pending_exits",
            "Number of pending exits");
  }

  public void onEpoch(
      UnsignedLong prevJustifiedEpoch,
      UnsignedLong currJustifiedEpoch,
      UnsignedLong currFinalizedEpoch,
      int prevEpochLiveValidators,
      int currEpochLiveValidators,
      long currPendingExits) {
    previousJustifiedEpoch.set(prevJustifiedEpoch.doubleValue());
    currentJustifiedEpoch.set(currJustifiedEpoch.longValue());
    currentFinalizedEpoch.set(currFinalizedEpoch.longValue());
    currentEpochLiveValidators.set(prevEpochLiveValidators);
    previousEpochLiveValidators.set(currEpochLiveValidators);
    pendingExits.set(currPendingExits);
  }
}
