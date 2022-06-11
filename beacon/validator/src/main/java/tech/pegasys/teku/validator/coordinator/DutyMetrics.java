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

package tech.pegasys.teku.validator.coordinator;

import static tech.pegasys.teku.spec.constants.NetworkConstants.INTERVALS_PER_SLOT;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.MetricsCountersByIntervals;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DutyMetrics {

  private final TimeProvider timeProvider;
  private final RecentChainData recentChainData;
  private final MetricsCountersByIntervals attestationTimings;
  private final MetricsCountersByIntervals blockTimings;
  private final Spec spec;

  @VisibleForTesting
  DutyMetrics(
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final MetricsCountersByIntervals attestationTimings,
      final MetricsCountersByIntervals blockTimings,
      final Spec spec) {
    this.timeProvider = timeProvider;
    this.recentChainData = recentChainData;
    this.attestationTimings = attestationTimings;
    this.blockTimings = blockTimings;
    this.spec = spec;
  }

  public static DutyMetrics create(
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final Spec spec) {
    final MetricsCountersByIntervals attestationTimings =
        MetricsCountersByIntervals.create(
            TekuMetricCategory.VALIDATOR,
            metricsSystem,
            "attestation_publication_delay",
            "Counter of attestations published in different time intervals after their due time",
            Collections.emptyList(),
            Map.of(List.of(), List.of(1L, 500L, 1000L, 2000L, 3000L, 4000L, 5000L, 8000L)));
    final MetricsCountersByIntervals blockTimings =
        MetricsCountersByIntervals.create(
            TekuMetricCategory.VALIDATOR,
            metricsSystem,
            "block_publication_delay",
            "Counter of blocks published in different time intervals after their due time",
            Collections.emptyList(),
            Map.of(List.of(), List.of(1L, 500L, 1000L, 2000L, 3000L, 4000L, 5000L, 8000L, 12000L)));
    return new DutyMetrics(timeProvider, recentChainData, attestationTimings, blockTimings, spec);
  }

  public void onAttestationPublished(final UInt64 slot) {
    final UInt64 currentTime = timeProvider.getTimeInMillis();
    final UInt64 expectedTime = calculateExpectedAttestationTimeInMillis(slot);
    if (currentTime.isGreaterThanOrEqualTo(expectedTime)) {
      attestationTimings.recordValue(currentTime.minus(expectedTime).longValue());
    } else {
      // The attestation was published ahead of time, likely because the block was received
      attestationTimings.recordValue(0);
    }
  }

  public void onBlockPublished(final UInt64 slot) {
    final UInt64 currentTime = timeProvider.getTimeInMillis();
    final UInt64 expectedTime = calculateSlotStartTimeMillis(slot);
    blockTimings.recordValue(currentTime.minusMinZero(expectedTime).longValue());
  }

  private UInt64 calculateExpectedAttestationTimeInMillis(final UInt64 slot) {
    final UInt64 slotStartTimeMillis = calculateSlotStartTimeMillis(slot);
    UInt64 millisPerSlot = spec.getMillisPerSlot(slot);
    return slotStartTimeMillis.plus(millisPerSlot.dividedBy(INTERVALS_PER_SLOT));
  }

  private UInt64 calculateSlotStartTimeMillis(final UInt64 slot) {
    final UInt64 genesisTime = recentChainData.getGenesisTimeMillis();
    return spec.getSlotStartTimeMillis(slot, genesisTime);
  }
}
