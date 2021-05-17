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
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class ValidatorPerformanceMetrics {

  // Attestation Performance Metrics
  private final SettableGauge numberOfExpectedAttestations;
  private final SettableGauge numberOfProducedAttestations;
  private final SettableGauge numberOfIncludedAttestations;
  private final SettableGauge inclusionDistanceMax;
  private final SettableGauge inclusionDistanceMin;
  private final SettableGauge inclusionDistanceAverage;
  private final SettableGauge correctTargetCount;
  private final SettableGauge correctHeadBlockCount;

  // Block Performance Metrics
  private final SettableGauge numberOfExpectedBlocks;
  private final SettableGauge numberOfProducedBlocks;
  private final SettableGauge numberOfIncludedBlocks;

  // Sync Committee Performance Metrics
  private final SettableGauge numberOfExpectedSignatures;
  private final SettableGauge numberOfProducedSignatures;
  private final SettableGauge numberOfIncludedSignatures;

  public ValidatorPerformanceMetrics(final MetricsSystem metricsSystem) {

    // Attestation Performance Metrics
    numberOfExpectedAttestations =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "expected_attestations",
            "Number of expected attestations");
    numberOfProducedAttestations =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "produced_attestations",
            "Number of produced attestations");
    numberOfIncludedAttestations =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "included_attestations",
            "Number of included attestations");
    inclusionDistanceMax =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "inclusion_distance_max",
            "Inclusion distance max");
    inclusionDistanceMin =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "inclusion_distance_min",
            "Inclusion distance min");
    inclusionDistanceAverage =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "inclusion_distance_average",
            "Inclusion distance average");
    correctTargetCount =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "correct_target_count",
            "Correct target count");
    correctHeadBlockCount =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "correct_head_block_count",
            "Correct head block count");

    // Block Performance Metrics
    numberOfExpectedBlocks =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "expected_blocks",
            "Number of expected blocks");
    numberOfIncludedBlocks =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "produced_blocks",
            "Number of produced blocks");
    numberOfProducedBlocks =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "included_blocks",
            "Number of included blocks");

    // Sync Committee Performance Metrics
    numberOfExpectedSignatures =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "expected_sync_committee_signatures",
            "Number of expected sync committee signatures");
    numberOfProducedSignatures =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "produced_sync_committee_signatures",
            "Number of produced sync committee signatures");
    numberOfIncludedSignatures =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR_PERFORMANCE,
            "included_sync_committee_signatures",
            "Number of included sync committee signatures");
  }

  public void updateAttestationPerformanceMetrics(
      final AttestationPerformance attestationPerformance) {
    numberOfExpectedAttestations.set(attestationPerformance.numberOfExpectedAttestations);
    numberOfProducedAttestations.set(attestationPerformance.numberOfProducedAttestations);
    numberOfIncludedAttestations.set(attestationPerformance.numberOfIncludedAttestations);
    inclusionDistanceMax.set(attestationPerformance.inclusionDistanceMax);
    inclusionDistanceMin.set(attestationPerformance.inclusionDistanceMin);
    inclusionDistanceAverage.set(attestationPerformance.inclusionDistanceAverage);
    correctTargetCount.set(attestationPerformance.correctTargetCount);
    correctHeadBlockCount.set(attestationPerformance.correctHeadBlockCount);
  }

  public void updateBlockPerformanceMetrics(final BlockPerformance blockPerformance) {
    numberOfExpectedBlocks.set(blockPerformance.numberOfExpectedBlocks);
    numberOfProducedBlocks.set(blockPerformance.numberOfProducedBlocks);
    numberOfIncludedBlocks.set(blockPerformance.numberOfIncludedBlocks);
  }

  public void updateSyncCommitteePerformance(final SyncCommitteePerformance performance) {
    numberOfExpectedSignatures.set(performance.getNumberOfExpectedSignatures());
    numberOfProducedSignatures.set(performance.getNumberOfProducedSignatures());
    numberOfIncludedSignatures.set(performance.getNumberOfIncludedSignatures());
  }
}
