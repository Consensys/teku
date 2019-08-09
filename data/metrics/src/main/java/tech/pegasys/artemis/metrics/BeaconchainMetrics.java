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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.pantheon.metrics.MetricsSystem;

public class BeaconchainMetrics {

  private final SettableGauge currentSlot;
  private final SettableGauge currentJustifiedEpoch;
  private final SettableGauge currentFinalizedEpoch;
  private final SettableGauge previousJustifiedEpoch;

  public BeaconchainMetrics(EventBus eventBus, final MetricsSystem metricsSystem) {
    currentSlot =
        SettableGauge.create(
            metricsSystem,
            ArtemisMetricCategory.BEACONCHAIN,
            "current_slot",
            "Latest slot recorded by the beacon chain");
    currentJustifiedEpoch =
        SettableGauge.create(
            metricsSystem,
            ArtemisMetricCategory.BEACONCHAIN,
            "current_justified_epoch",
            "Current justified epoch");
    currentFinalizedEpoch =
        SettableGauge.create(
            metricsSystem,
            ArtemisMetricCategory.BEACONCHAIN,
            "current_finalized_epoch",
            "Current finalized epoch");
    previousJustifiedEpoch =
        SettableGauge.create(
            metricsSystem,
            ArtemisMetricCategory.BEACONCHAIN,
            "current_prev_justified_epoch",
            "Current previously justified epoch");
    eventBus.register(this);
  }

  @Subscribe
  public void updateStats(RawRecord record) {
    currentSlot.set(record.getIndex() + Constants.GENESIS_SLOT);
    currentJustifiedEpoch.set(record.getHeadState().getCurrent_justified_epoch().doubleValue());
    currentFinalizedEpoch.set(record.getHeadState().getFinalized_epoch().doubleValue());
    previousJustifiedEpoch.set(record.getHeadState().getPrevious_justified_epoch().longValue());
  }
}
