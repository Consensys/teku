package tech.pegasys.teku.validator.coordinator.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.VALIDATOR_PERFORMANCE;

public class ValidatorPerformanceMetricsTest {
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  public final ValidatorPerformanceMetrics validatorPerformanceMetrics =
          new ValidatorPerformanceMetrics(metricsSystem);

  private final int NUMBER_OF_PRODUCED_ATTESTATIONS = 50;
  private final int NUMBER_OF_INCLUDED_ATTESTATIONS = 30;
  private final int INCLUSION_DISTANCE_MAX = 15;
  private final int INCLUSION_DISTANCE_MIN = 1;
  private final double INCLUSION_DISTANCE_AVERAGE = 1.14;
  private final int CORRECT_TARGET_COUNT = 20;
  private final int CORRECT_HEAD_BLOCK_COUNT = 14;

  private final int NUMBER_OF_PRODUCED_BLOCKS = 5;
  private final int NUMBER_OF_INCLUDED_BLOCKS = 3;

  private final AttestationPerformance attestationPerformance =
          new AttestationPerformance(
                  NUMBER_OF_PRODUCED_ATTESTATIONS,
                  NUMBER_OF_INCLUDED_ATTESTATIONS,
                  INCLUSION_DISTANCE_MAX,
                  INCLUSION_DISTANCE_MIN,
                  INCLUSION_DISTANCE_AVERAGE,
                  CORRECT_TARGET_COUNT,
                  CORRECT_HEAD_BLOCK_COUNT);

  private final BlockPerformance blockPerformance =
          new BlockPerformance(NUMBER_OF_PRODUCED_BLOCKS, NUMBER_OF_INCLUDED_BLOCKS);

  @BeforeEach
  void setUp() {
    validatorPerformanceMetrics.updateAttestationPerformanceMetrics(attestationPerformance);
    validatorPerformanceMetrics.updateBlockPerformanceMetrics(blockPerformance);
  }

  @Test
  void getProducedAttestations() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "produced_attestations").getValue())
            .isEqualTo(NUMBER_OF_PRODUCED_ATTESTATIONS);
  }

  @Test
  void getIncludedAttestations() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "included_attestations").getValue())
            .isEqualTo(NUMBER_OF_INCLUDED_ATTESTATIONS);
  }

  @Test
  void getInclusionDistanceMax() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "inclusion_distance_max").getValue())
            .isEqualTo(INCLUSION_DISTANCE_MAX);
  }

  @Test
  void getInclusionDistanceMin() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "inclusion_distance_min").getValue())
            .isEqualTo(INCLUSION_DISTANCE_MIN);
  }

  @Test
  void getInclusionDistanceAverage() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "inclusion_distance_average").getValue())
            .isEqualTo(INCLUSION_DISTANCE_AVERAGE);
  }

  @Test
  void getCorrectTargetCount() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "correct_target_count").getValue())
            .isEqualTo(CORRECT_TARGET_COUNT);
  }

  @Test
  void getCorrectHeadBlockCount() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "correct_head_block_count").getValue())
            .isEqualTo(CORRECT_HEAD_BLOCK_COUNT);
  }

  @Test
  void getProducedBlocks() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "produced_blocks").getValue())
            .isEqualTo(NUMBER_OF_PRODUCED_BLOCKS);
  }

  @Test
  void getIncludedBlocks() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "included_blocks").getValue())
            .isEqualTo(NUMBER_OF_INCLUDED_BLOCKS);
  }
}
