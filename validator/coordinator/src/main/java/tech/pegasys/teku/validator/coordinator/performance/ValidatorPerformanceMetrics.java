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

package tech.pegasys.teku.validator.coordinator.performance;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.SettableDoubleGauge;
import tech.pegasys.teku.infrastructure.metrics.SettableIntGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class ValidatorPerformanceMetrics {

  // Attestation Performance Metrics
  private final SettableIntGauge numberOfProducedAttestations;
  private final SettableIntGauge numberOfIncludedAttestations;
  private final SettableIntGauge inclusionDistanceMax;
  private final SettableIntGauge inclusionDistanceMin;
  private final SettableDoubleGauge inclusionDistanceAverage;
  private final SettableIntGauge correctTargetCount;
  private final SettableIntGauge correctHeadBlockCount;

  // Block Performance Metrics
  private final SettableIntGauge numberOfProducedBlocks;
  private final SettableIntGauge numberOfIncludedBlocks;

  public ValidatorPerformanceMetrics(final MetricsSystem metricsSystem) {

    // Attestation Performance Metrics
    numberOfProducedAttestations =
        SettableIntGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "produced_attestations",
            "Number of produced attestations");
    numberOfIncludedAttestations =
        SettableIntGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "included_attestations",
            "Number of included attestations");
    inclusionDistanceMax =
        SettableIntGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "inclusion_distance_max",
            "Inclusion distance max");
    inclusionDistanceMin =
        SettableIntGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "inclusion_distance_min",
            "Inclusion distance min");
    inclusionDistanceAverage =
        SettableDoubleGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "inclusion_distance_average",
            "Inclusion distance average");
    correctTargetCount =
        SettableIntGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "correct_target_count",
            "Correct target count");
    correctHeadBlockCount =
        SettableIntGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "correct_head_block_count",
            "Correct head block count");

    // Block Performance Metrics
    numberOfIncludedBlocks =
        SettableIntGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "produced_blocks",
            "Number of produced blocks");
    numberOfProducedBlocks =
        SettableIntGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "included_blocks",
            "Number of included blocks");
  }

  public void updateAttestationPerformanceMetrics(
      final AttestationPerformance attestationPerformance) {
    numberOfProducedAttestations.set(attestationPerformance.numberOfProducedAttestations);
    numberOfIncludedAttestations.set(attestationPerformance.numberOfIncludedAttestations);
    inclusionDistanceMax.set(attestationPerformance.inclusionDistanceMax);
    inclusionDistanceMin.set(attestationPerformance.inclusionDistanceMin);
    inclusionDistanceAverage.set(attestationPerformance.inclusionDistanceAverage);
    correctTargetCount.set(attestationPerformance.correctTargetCount);
    correctHeadBlockCount.set(attestationPerformance.correctHeadBlockCount);
  }

  public void updateBlockPerformanceMetrics(final BlockPerformance blockPerformance) {
    numberOfProducedBlocks.set(blockPerformance.numberOfProducedBlocks);
    numberOfIncludedBlocks.set(blockPerformance.numberOfIncludedBlocks);
  }
}
