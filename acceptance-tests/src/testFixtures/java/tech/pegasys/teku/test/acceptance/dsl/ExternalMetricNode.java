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

package tech.pegasys.teku.test.acceptance.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.ImageFromDockerfile;
import tech.pegasys.teku.data.publisher.MetricsDataClient;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.test.data.publisher.DeserializedMetricDataObject;

public class ExternalMetricNode extends Node {
  private static final Logger LOG = LogManager.getLogger();

  public static final int STUB_PORT = 8001;
  protected static final String WORKING_DIRECTORY =
      "/opt/tech/pegasys/teku/test/acceptance/stubServer/";

  private boolean started = false;

  private ExternalMetricNode(Network network, ImageFromDockerfile image) {
    super(network, image, LOG);
    container.withWorkingDirectory(WORKING_DIRECTORY).withExposedPorts(STUB_PORT);
  }

  public static ExternalMetricNode create(final Network network) {
    final ImageFromDockerfile image =
        new ImageFromDockerfile()
            .withDockerfile(
                Path.of(
                    "src/testFixtures/java/tech/pegasys/teku/test/acceptance/stubServer/Dockerfile"));
    return new ExternalMetricNode(network, image);
  }

  public String getAddress() {
    return "http://" + this.container.getHost() + ":" + this.container.getMappedPort(STUB_PORT);
  }

  public String getPublicationEndpointURL() {
    return "http://" + this.nodeAlias + ":" + STUB_PORT + "/input";
  }

  public String getResponse() throws URISyntaxException, IOException {
    return httpClient.get(new URI(this.getAddress()), "/output");
  }

  private DeserializedMetricDataObject[] getPublishedObjects()
      throws URISyntaxException, IOException {
    String response = getResponse();
    LOG.debug("Metric data was published " + response);
    return jsonProvider.jsonToObject(response, DeserializedMetricDataObject[].class);
  }

  public void waitForBeaconNodeMetricPublication() {
    Waiter.waitFor(
        () -> {
          DeserializedMetricDataObject[] publishedData = getPublishedObjects();
          assertThat(publishedData.length).isEqualTo(3);

          assertThat(publishedData[0]).isNotNull();
          assertThat(publishedData[0].process).isNotNull();
          assertThat(publishedData[0].process)
              .isEqualTo(MetricsDataClient.BEACON_NODE.getDataClient());
          assertThat(publishedData[0].version).isNotNull();
          assertThat(publishedData[0].timestamp).isNotNull();
          assertThat(publishedData[0].client_name).isNotNull();
          assertThat(publishedData[0].client_version).isNotNull();
          assertThat(publishedData[0].cpu_process_seconds_total).isNotNull();
          assertThat(publishedData[0].memory_process_bytes).isNotNull();
          assertThat(publishedData[0].network_peers_connected).isNotNull();
          assertThat(publishedData[0].sync_beacon_head_slot).isNotNull();
        });
  }

  public void waitForValidatorMetricPublication() {
    Waiter.waitFor(
        () -> {
          DeserializedMetricDataObject[] publishedData = getPublishedObjects();
          assertThat(publishedData.length).isEqualTo(3);

          assertThat(publishedData[1]).isNotNull();
          assertThat(publishedData[1].process).isNotNull();
          assertThat(publishedData[1].process)
              .isEqualTo(MetricsDataClient.VALIDATOR.getDataClient());
          assertThat(publishedData[1].version).isNotNull();
          assertThat(publishedData[1].timestamp).isNotNull();
          assertThat(publishedData[1].client_name).isNotNull();
          assertThat(publishedData[1].client_version).isNotNull();
          assertThat(publishedData[1].cpu_process_seconds_total).isNotNull();
          assertThat(publishedData[1].memory_process_bytes).isNotNull();
          assertThat(publishedData[1].validator_total).isNotNull();
        });
  }

  public void waitForSystemMetricPublication() {
    Waiter.waitFor(
        () -> {
          DeserializedMetricDataObject[] publishedData = getPublishedObjects();
          assertThat(publishedData.length).isEqualTo(3);

          assertThat(publishedData[2]).isNotNull();
          assertThat(publishedData[2].process).isNotNull();
          assertThat(publishedData[2].process).isEqualTo(MetricsDataClient.SYSTEM.getDataClient());
          assertThat(publishedData[2].version).isNotNull();
          assertThat(publishedData[2].timestamp).isNotNull();
          assertThat(publishedData[2].cpu_node_system_seconds_total).isNotNull();
          assertThat(publishedData[2].misc_os).isNotNull();
        });
  }

  public void start() {
    assertThat(started).isFalse();
    LOG.debug("Start node {}", nodeAlias);
    started = true;
    container.start();
  }

  @Override
  public void stop() {
    if (!started) {
      return;
    }
    started = false;
    LOG.debug("Shutting down");
    container.stop();
  }
}
