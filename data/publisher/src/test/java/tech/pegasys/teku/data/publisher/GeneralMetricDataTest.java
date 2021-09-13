/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.data.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.provider.JsonProvider;

class GeneralMetricDataTest {

  private JsonProvider jsonProvider = new JsonProvider();

  PrometheusMetricsSystem prometheusMock = mock(PrometheusMetricsSystem.class);

  @Test
  public void shouldSerializeObject() throws JsonProcessingException {
    final GeneralMetricData process =
        new GeneralMetricData(1, 10L, "system", 11L, 12L, "teku", "21.8", 2, 3, 4);
    final String data = jsonProvider.objectToJSON(process);
    assertThat(process).isEqualTo(jsonProvider.jsonToObject(data, GeneralMetricData.class));
  }

  @Test
  public void shouldSerializeObjectFromPrometheusMetrics() throws JsonProcessingException {
    when(prometheusMock.streamObservations()).thenReturn(getMockObservations().stream());
    final GeneralMetricData metricData =
        new GeneralMetricData(1, 10L, "system", 10L, 10L, "Teku", "1.1", 1, 1, 1);
    final String data = jsonProvider.objectToJSON(metricData);

    assertThat(metricData).isEqualTo(jsonProvider.jsonToObject(data, GeneralMetricData.class));
  }

  @Test
  public void shouldSerializeObjectFromPrometheusMetricsWhenNoDataIsFound()
      throws JsonProcessingException {
    when(prometheusMock.streamObservations()).thenReturn(new ArrayList<Observation>().stream());
    final GeneralMetricData metricData =
        new GeneralMetricData(1, 10L, "system", 10L, 10L, "Teku", "1.1", 1, 1, 1);
    final String data = jsonProvider.objectToJSON(metricData);

    assertThat(metricData).isEqualTo(jsonProvider.jsonToObject(data, GeneralMetricData.class));
  }

  private ArrayList<Observation> getMockObservations() {
    ArrayList<Observation> list = new ArrayList<>();
    Observation cpu =
        new Observation(StandardMetricCategory.PROCESS, "cpu_seconds_total", 1.0, null);
    Observation memory =
        new Observation(StandardMetricCategory.JVM, "memory_pool_bytes_used", 1.0, null);
    Observation clientVersion =
        new Observation(TekuMetricCategory.BEACON, "teku_version", 1.0, null);
    Observation activeValidators =
        new Observation(TekuMetricCategory.BEACON, "current_active_validators", 1.0, null);
    Observation liveValidators =
        new Observation(TekuMetricCategory.BEACON, "current_live_validators", 1.0, null);
    list.add(cpu);
    list.add(memory);
    list.add(clientVersion);
    list.add(activeValidators);
    list.add(liveValidators);
    return list;
  }
}
