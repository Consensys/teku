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

package tech.pegasys.teku.beacon.sync.historical;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class ProgressLogger {
  private final SettableGauge reconstructGauge;
  private Instant lastLogged;
  private final StatusLogger statusLogger;

  protected ProgressLogger(final MetricsSystem metricsSystem, final StatusLogger statusLogger) {
    this(metricsSystem, statusLogger, Instant.now());
  }

  protected ProgressLogger(
      final MetricsSystem metricsSystem, final StatusLogger statusLogger, final Instant time) {
    this.reconstructGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "reconstruct_historical_states_slot",
            "The slot the reconstruct historical states service has last saved");

    this.lastLogged = time;
    this.statusLogger = statusLogger;
  }

  void update(final SignedBeaconBlock block, final UInt64 anchorSlot) {
    final UInt64 currentSlot = block.getSlot();
    reconstructGauge.set(currentSlot.doubleValue());

    final Instant now = Instant.now();
    if (lastLogged.plus(5, ChronoUnit.MINUTES).isBefore(now)) {
      statusLogger.reconstructedHistoricalBlocks(currentSlot, anchorSlot);
      lastLogged = now;
    }
  }
}
