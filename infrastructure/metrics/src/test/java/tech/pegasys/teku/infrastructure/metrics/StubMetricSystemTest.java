package tech.pegasys.teku.infrastructure.metrics;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class StubMetricSystemTest {

    private final MetricsSystem metricsSystem = new StubMetricsSystem();

    private final MetricCategory metricCategory = new MetricCategory() {
        @Override
        public String getName() {
            return "dummyMetricCategory";
        }
        @Override
        public Optional<String> getApplicationPrefix() {
            return Optional.empty();
        }
    };

    @Test
    public void mustAcceptValidMetricNames() {
        metricsSystem.createLabelledCounter(metricCategory, "correctname", "", "correct_label");
        metricsSystem.createLabelledCounter(metricCategory, ":correctname", "", "correct_label");
        metricsSystem.createLabelledCounter(metricCategory, "_correctname", "", "correct_label");
        metricsSystem.createLabelledCounter(metricCategory, "correctNAME", "", "correct_label");
        metricsSystem.createLabelledCounter(metricCategory, "correct_name", "", "correct_label");
        metricsSystem.createLabelledCounter(metricCategory, "correct_name__", "", "correct_label");
        metricsSystem.createLabelledCounter(metricCategory, "correct_name::123", "", "correct_label");
    }

    @Test
    public void mustRejectInvalidMetricNames() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> metricsSystem.createLabelledCounter(metricCategory, "1incorrect_name", "", "correct_label"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> metricsSystem.createLabelledCounter(metricCategory, "$incorrect_name", "", "correct_label"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> metricsSystem.createLabelledCounter(metricCategory, "incorrect-name", "", "correct_label"));
    }

    @Test
    public void mustAcceptValidLabelNames() {
        metricsSystem.createLabelledCounter(metricCategory, "correct_name", "", "correctlabel");
        metricsSystem.createLabelledCounter(metricCategory, "correct_name", "", "correctLabel");
        metricsSystem.createLabelledCounter(metricCategory, "correct_name", "", "correct_label");
        metricsSystem.createLabelledCounter(metricCategory, "correct_name", "", "_correct_label");
    }

    @Test
    public void mustRejectInvalidLabelNames() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> metricsSystem.createLabelledCounter(metricCategory, "correct_name", "", "1incorrect_label"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> metricsSystem.createLabelledCounter(metricCategory, "correct_name", "", "@incorrect_label"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> metricsSystem.createLabelledCounter(metricCategory, "correct_name", "", "incorrect-label"));
    }
}
