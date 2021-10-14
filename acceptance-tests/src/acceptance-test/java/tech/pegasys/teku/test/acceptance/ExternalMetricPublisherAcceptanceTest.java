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

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.StubNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;

public class ExternalMetricPublisherAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldPublishDataFromPrometheus() throws Throwable {

    StubNode stubNode = StubNode.create();
    stubNode.start();
    String metricServerAddress = "http://" + stubNode.getContainerNetworkAlias();

    final TekuNode node =
        createTekuNode(
            config ->
                config.withExternalMetricsClient(
                    metricServerAddress + ":" + StubNode.STUB_PORT + "/", 1),
            stubNode);
    node.start();

    node.waitForNewBlock();

    String data = readReply(stubNode);
    assertThat(data).contains("version");

    node.stop();
    stubNode.stop();
  }

  private String readReply(StubNode n) {
    String data = "";
    try {
      String s = n.getAddress() + "/content";
      URL url = new URL(s);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Accept", "application/json");
      if (conn.getResponseCode() != 200) {
        throw new RuntimeException("Failed : HTTP Error code : " + conn.getResponseCode());
      }
      data = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      conn.disconnect();
    } catch (Exception e) {
      System.out.println(e);
    }
    return data;
  }
}
