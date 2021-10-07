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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.RemoteMetricsServiceStub;
import tech.pegasys.teku.test.acceptance.dsl.SuccessHandler;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;

public class ExternalMetricPublisherAcceptanceTest extends AcceptanceTestBase {
  String metricServerAddress = "http://host.docker.internal";

  @Test
  void shouldPublishDataFromPrometheus() throws Throwable {
    SuccessHandler successHandler = new SuccessHandler();
    RemoteMetricsServiceStub server = new RemoteMetricsServiceStub();
    server.registerHandler("/metrics", successHandler);
    server.startServer();

    final TekuNode node =
        createTekuNode(
            config ->
                config.withExternalMetricsClient(
                    metricServerAddress + ":" + RemoteMetricsServiceStub.PORT + "/metrics", 1));
    node.start();

    node.waitForNewBlock();
    assertThat(successHandler.getResponse()).contains("version");
    server.stopServer();
  }
}
