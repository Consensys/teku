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
import java.util.List;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.test.data.publisher.DeserializedMetricDataObject;

class MetricsDataFactoryTest {

  private final JsonProvider jsonProvider = new JsonProvider();
  private static final int CURRENT_TIME = 10_000;
  PrometheusMetricsSystem prometheusMock = mock(PrometheusMetricsSystem.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(CURRENT_TIME);

  @Test
  public void shouldExtractMetricsFromPrometheusMetricsAndSerialiseJson()
      throws JsonProcessingException {
    when(prometheusMock.streamObservations()).thenReturn(getMockObservations().stream());
    final MetricsDataFactory metricsDataFactory = new MetricsDataFactory(prometheusMock);

    final List<BaseMetricData> baseMetricData = metricsDataFactory.getMetricData(timeProvider);
    assertThat(baseMetricData.size()).isEqualTo(3);
    final String beaconNode = jsonProvider.objectToJSON(baseMetricData.get(0));
    final String validator = jsonProvider.objectToJSON(baseMetricData.get(1));
    final String system = jsonProvider.objectToJSON(baseMetricData.get(2));

    BeaconNodeMetricData beaconNodeDeserialized =
        jsonProvider.jsonToObject(beaconNode, BeaconNodeMetricData.class);
    ValidatorMetricData validatorDeserialized =
        jsonProvider.jsonToObject(validator, ValidatorMetricData.class);
    SystemMetricData systemDeserialized = jsonProvider.jsonToObject(system, SystemMetricData.class);

    assertThat(baseMetricData.get(0)).isInstanceOf(BeaconNodeMetricData.class);
    assertThat(baseMetricData.get(1)).isInstanceOf(ValidatorMetricData.class);
    assertThat(baseMetricData.get(2)).isInstanceOf(SystemMetricData.class);

    assertThat(baseMetricData.get(0)).isEqualTo(beaconNodeDeserialized);
    assertThat(baseMetricData.get(1)).isEqualTo(validatorDeserialized);
    assertThat(baseMetricData.get(2)).isEqualTo(systemDeserialized);
  }

  @Test
  public void shouldDeserializeObjectFromString() throws JsonProcessingException {
    when(prometheusMock.streamObservations()).thenReturn(getMockObservations().stream());
    final MetricsDataFactory metricsDataFactory = new MetricsDataFactory(prometheusMock);
    final List<BaseMetricData> baseMetricData = metricsDataFactory.getMetricData(timeProvider);
    assertThat(baseMetricData.size()).isEqualTo(3);

    String listOfMetrics = jsonProvider.objectToJSON(baseMetricData);
    DeserializedMetricDataObject[] base =
        jsonProvider.jsonToObject(listOfMetrics, DeserializedMetricDataObject[].class);

    assertThat(base.length).isEqualTo(3);
  }

  @Test
  public void shouldSerializeObjectFromPrometheusMetricsWithDefaultValues()
      throws JsonProcessingException {
    when(prometheusMock.streamObservations()).thenReturn(new ArrayList<Observation>().stream());
    final MetricsDataFactory metricsDataFactory = new MetricsDataFactory(prometheusMock);

    final List<BaseMetricData> baseMetricData = metricsDataFactory.getMetricData(timeProvider);
    assertThat(baseMetricData.size()).isEqualTo(3);
    final String beaconNode = jsonProvider.objectToJSON(baseMetricData.get(0));
    final String validator = jsonProvider.objectToJSON(baseMetricData.get(1));
    final String system = jsonProvider.objectToJSON(baseMetricData.get(2));

    BeaconNodeMetricData beaconNodeDeserialized =
        jsonProvider.jsonToObject(beaconNode, BeaconNodeMetricData.class);
    ValidatorMetricData validatorDeserialized =
        jsonProvider.jsonToObject(validator, ValidatorMetricData.class);
    SystemMetricData systemDeserialized = jsonProvider.jsonToObject(system, SystemMetricData.class);

    assertThat(baseMetricData.get(0)).isInstanceOf(BeaconNodeMetricData.class);
    assertThat(baseMetricData.get(1)).isInstanceOf(ValidatorMetricData.class);
    assertThat(baseMetricData.get(2)).isInstanceOf(SystemMetricData.class);

    assertThat(baseMetricData.get(0)).isEqualTo(beaconNodeDeserialized);
    assertThat(baseMetricData.get(1)).isEqualTo(validatorDeserialized);
    assertThat(baseMetricData.get(2)).isEqualTo(systemDeserialized);

    assertThat(beaconNodeDeserialized.network_peers_connected).isNull();
    assertThat(validatorDeserialized.validator_total).isNull();
    assertThat(systemDeserialized.cpu_node_system_seconds_total).isNull();
  }

  private ArrayList<Observation> getMockObservations() {
    ArrayList<Observation> list = new ArrayList<>();
    Observation cpu =
        new Observation(StandardMetricCategory.PROCESS, "cpu_seconds_total", 1.0, null);
    Observation memory =
        new Observation(StandardMetricCategory.JVM, "memory_pool_bytes_used", 1.0, null);
    Observation activeValidators =
        new Observation(TekuMetricCategory.BEACON, "current_active_validators", 1.0, null);
    Observation liveValidators =
        new Observation(TekuMetricCategory.BEACON, "current_live_validators", 1.0, null);
    list.add(cpu);
    list.add(memory);
    list.add(activeValidators);
    list.add(liveValidators);
    return list;
  }
}
