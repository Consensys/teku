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

package tech.pegasys.teku.test.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.data.publisher.BaseMetricData;
import tech.pegasys.teku.data.publisher.BeaconNodeMetricData;
import tech.pegasys.teku.data.publisher.MetricsDataClient;
import tech.pegasys.teku.data.publisher.SystemMetricData;
import tech.pegasys.teku.data.publisher.ValidatorMetricData;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.ExternalMetricNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;

public class ExternalMetricPublisherAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldPublishDataFromPrometheus() throws Throwable {
    ExternalMetricNode externalMetricNode = createExternalMetricNode();
    externalMetricNode.start();

    final TekuNode tekuNode =
        createTekuNode(config -> config.withExternalMetricsClient(externalMetricNode, 1));
    tekuNode.start();

    externalMetricNode.waitForPublication();

    List<BaseMetricData> publishedData = externalMetricNode.getPublishedObjects();
    assertThat(publishedData.size()).isEqualTo(3);

    BeaconNodeMetricData beaconNodeMetricData = (BeaconNodeMetricData) publishedData.get(0);
    assertThat(beaconNodeMetricData.process)
        .isEqualTo(MetricsDataClient.BEACON_NODE.getDataClient());
    assertThat(beaconNodeMetricData.network_peers_connected).isNotNull();
    assertThat(beaconNodeMetricData.sync_beacon_head_slot).isNotNull();

    ValidatorMetricData validatorMetricData = (ValidatorMetricData) publishedData.get(1);
    assertThat(validatorMetricData.process).isEqualTo(MetricsDataClient.VALIDATOR.getDataClient());
    assertThat(validatorMetricData.cpu_process_seconds_total).isNotNull();
    assertThat(validatorMetricData.memory_process_bytes).isNotNull();
    assertThat(validatorMetricData.validator_total).isNotNull();
    assertThat(validatorMetricData.validator_active).isNotNull();

    SystemMetricData systemMetricData = (SystemMetricData) publishedData.get(2);
    assertThat(systemMetricData.process).isEqualTo(MetricsDataClient.SYSTEM.getDataClient());
    assertThat(systemMetricData.cpu_node_system_seconds_total).isNotNull();

    tekuNode.stop();
    externalMetricNode.stop();
  }
}
