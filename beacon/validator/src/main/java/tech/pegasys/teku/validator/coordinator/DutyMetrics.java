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

package tech.pegasys.teku.validator.coordinator;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.spec.constants.NetworkConstants.INTERVALS_PER_SLOT;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DutyMetrics {

  private final TimeProvider timeProvider;
  private final RecentChainData recentChainData;
  private final MetricsHistogram attestationHistogram;
  private final Spec spec;

  @VisibleForTesting
  DutyMetrics(
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final MetricsHistogram attestationHistogram,
      final Spec spec) {
    this.timeProvider = timeProvider;
    this.recentChainData = recentChainData;
    this.attestationHistogram = attestationHistogram;
    this.spec = spec;
  }

  public static DutyMetrics create(
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final Spec spec) {
    final MetricsHistogram attestationHistogram =
        MetricsHistogram.create(
            TekuMetricCategory.VALIDATOR,
            metricsSystem,
            "attestation_publication_delay",
            "Histogram recording delay in milliseconds from scheduled time to an attestation being published",
            1,
            List.of());
    return new DutyMetrics(timeProvider, recentChainData, attestationHistogram, spec);
  }

  public void onAttestationPublished(final UInt64 slot) {
    final UInt64 currentTime = timeProvider.getTimeInMillis();
    final UInt64 expectedTime = calculateExpectedAttestationTimeInMillis(slot);
    if (currentTime.isGreaterThanOrEqualTo(expectedTime)) {
      attestationHistogram.recordValue(currentTime.minus(expectedTime).longValue());
    } else {
      // The attestation was published ahead of time, likely because the block was received
      attestationHistogram.recordValue(0);
    }
  }

  private UInt64 calculateExpectedAttestationTimeInMillis(final UInt64 slot) {
    final UInt64 genesisTime = recentChainData.getGenesisTime();
    UInt64 millisPerSlot = spec.getMillisPerSlot(slot);
    return secondsToMillis(genesisTime)
        .plus(slot.times(millisPerSlot))
        .plus(millisPerSlot.dividedBy(INTERVALS_PER_SLOT));
  }
}
