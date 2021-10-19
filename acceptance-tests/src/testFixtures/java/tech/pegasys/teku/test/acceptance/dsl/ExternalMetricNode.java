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
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.ImageFromDockerfile;
import tech.pegasys.teku.data.publisher.MetricsDataClient;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.test.data.publisher.DeserializedMetricDataObject;

public class ExternalMetricNode extends Node {
  private final JsonProvider jsonProvider = new JsonProvider();
  private static final Logger LOG = LogManager.getLogger();
  private final SimpleHttpClient httpClient;

  public static final int STUB_PORT = 8001;
  protected static final String WORKING_DIRECTORY =
      "/opt/tech/pegasys/teku/test/acceptance/stubServer/";

  private boolean started = false;

  private ExternalMetricNode(
      final SimpleHttpClient httpClient, Network network, ImageFromDockerfile image) {
    super(network, image, LOG);
    this.httpClient = httpClient;
    container.withWorkingDirectory(WORKING_DIRECTORY).withExposedPorts(STUB_PORT);
  }

  public static ExternalMetricNode create(
      final SimpleHttpClient httpClient, final Network network) {
    final ImageFromDockerfile image =
        new ImageFromDockerfile()
            .withDockerfile(
                Path.of(
                    "src/testFixtures/java/tech/pegasys/teku/test/acceptance/stubServer/Dockerfile"));
    return new ExternalMetricNode(httpClient, network, image);
  }

  public String getAddress() {
    return "http://" + this.container.getHost() + ":" + this.container.getMappedPort(STUB_PORT);
  }

  public String getPublicationEndpointURL() {
    return "http://" + this.nodeAlias + ":" + STUB_PORT + "/input";
  }

  private void waitForPublication() {
    try {
      Waiter.waitFor(() -> assertThat(fetchPublication().get()).isNotEmpty(), 1, TimeUnit.MINUTES);
    } catch (final Throwable t) {
      fail(t.getMessage() + " Logs: " + container.getLogs(), t);
    }
  }

  private Optional<String> fetchPublication() throws IOException, URISyntaxException {
    final String result = httpClient.get(new URI(this.getAddress()), "/output");
    if (result.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(result);
  }

  public String getResponse() throws URISyntaxException, IOException {
    return httpClient.get(new URI(this.getAddress()), "/output");
  }

  private DeserializedMetricDataObject[] getPublishedObjects()
      throws URISyntaxException, IOException {
    String response;
    waitForPublication();
    response = getResponse();

    return jsonProvider.jsonToObject(response, DeserializedMetricDataObject[].class);
  }

  public void waitForBeaconNodeMetricPublication() throws URISyntaxException, IOException {
    DeserializedMetricDataObject[] publishedData = getPublishedObjects();
    assertThat(publishedData.length).isEqualTo(3);

    assertThat(publishedData[0].process).isNotNull();

    assertThat(publishedData[0].process).isEqualTo(MetricsDataClient.BEACON_NODE.getDataClient());
    assertThat(publishedData[0].network_peers_connected).isNotNull();
    assertThat(publishedData[0].sync_beacon_head_slot).isNotNull();
  }

  public void waitForValidatorMetricPublication() throws URISyntaxException, IOException {
    DeserializedMetricDataObject[] publishedData = getPublishedObjects();
    assertThat(publishedData.length).isEqualTo(3);

    assertThat(publishedData[1].process).isNotNull();

    assertThat(publishedData[1].process).isEqualTo(MetricsDataClient.VALIDATOR.getDataClient());
    assertThat(publishedData[1].cpu_process_seconds_total).isNotNull();
    assertThat(publishedData[1].memory_process_bytes).isNotNull();
    assertThat(publishedData[1].validator_total).isNotNull();
    assertThat(publishedData[1].validator_active).isNotNull();
  }

  public void waitForSystemMetricPublication() throws URISyntaxException, IOException {
    DeserializedMetricDataObject[] publishedData = getPublishedObjects();
    assertThat(publishedData.length).isEqualTo(3);

    assertThat(publishedData[2].process).isNotNull();

    assertThat(publishedData[2].process).isEqualTo(MetricsDataClient.SYSTEM.getDataClient());
    assertThat(publishedData[2].cpu_node_system_seconds_total).isNotNull();
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
