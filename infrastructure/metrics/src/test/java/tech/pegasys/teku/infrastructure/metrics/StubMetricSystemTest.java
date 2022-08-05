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

package tech.pegasys.teku.infrastructure.metrics;

import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StubMetricSystemTest {

  private final MetricsSystem metricsSystem = new StubMetricsSystem();

  private final MetricCategory metricCategory =
      new MetricCategory() {
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
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            metricsSystem.createLabelledCounter(
                metricCategory, "1incorrect_name", "", "correct_label"));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            metricsSystem.createLabelledCounter(
                metricCategory, "$incorrect_name", "", "correct_label"));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            metricsSystem.createLabelledCounter(
                metricCategory, "incorrect-name", "", "correct_label"));
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
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            metricsSystem.createLabelledCounter(
                metricCategory, "correct_name", "", "1incorrect_label"));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            metricsSystem.createLabelledCounter(
                metricCategory, "correct_name", "", "@incorrect_label"));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            metricsSystem.createLabelledCounter(
                metricCategory, "correct_name", "", "incorrect-label"));
  }
}
