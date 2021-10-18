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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.ImageFromDockerfile;
import tech.pegasys.teku.data.publisher.BaseMetricData;
import tech.pegasys.teku.data.publisher.BeaconNodeMetricData;
import tech.pegasys.teku.data.publisher.MetricsDataClient;
import tech.pegasys.teku.data.publisher.SystemMetricData;
import tech.pegasys.teku.data.publisher.ValidatorMetricData;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.provider.JsonProvider;

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

  private List<BaseMetricData> getPublishedObjects() throws URISyntaxException, IOException {
    waitForPublication();
    String response = getResponse();
    String beaconJson = response.substring(1, response.indexOf("},") + 1);
    String validatorJson =
        response.substring(response.indexOf("},") + 2, response.lastIndexOf("},") + 1);
    String systemJson = response.substring(response.lastIndexOf("},") + 2, response.length() - 1);

    BeaconNodeMetricData beaconNodeMetricData =
        jsonProvider.jsonToObject(beaconJson, BeaconNodeMetricData.class);
    ValidatorMetricData validatorMetricData =
        jsonProvider.jsonToObject(validatorJson, ValidatorMetricData.class);
    SystemMetricData systemMetricData =
        jsonProvider.jsonToObject(systemJson, SystemMetricData.class);
    List<BaseMetricData> dataReadings = new ArrayList<>();
    dataReadings.add(beaconNodeMetricData);
    dataReadings.add(validatorMetricData);
    dataReadings.add(systemMetricData);
    return dataReadings;
  }

  public void waitForBeaconNodeMetricPublication() throws URISyntaxException, IOException {
    List<BaseMetricData> publishedData = getPublishedObjects();
    assertThat(publishedData.size()).isEqualTo(3);

    BeaconNodeMetricData beaconNodeMetricData = (BeaconNodeMetricData) publishedData.get(0);
    assertThat(beaconNodeMetricData.process)
        .isEqualTo(MetricsDataClient.BEACON_NODE.getDataClient());
    assertThat(beaconNodeMetricData.network_peers_connected).isNotNull();
    assertThat(beaconNodeMetricData.sync_beacon_head_slot).isNotNull();
  }

  public void waitForValidatorMetricPublication() throws URISyntaxException, IOException {
    List<BaseMetricData> publishedData = getPublishedObjects();
    assertThat(publishedData.size()).isEqualTo(3);

    ValidatorMetricData validatorMetricData = (ValidatorMetricData) publishedData.get(1);
    assertThat(validatorMetricData.process).isEqualTo(MetricsDataClient.VALIDATOR.getDataClient());
    assertThat(validatorMetricData.cpu_process_seconds_total).isNotNull();
    assertThat(validatorMetricData.memory_process_bytes).isNotNull();
    assertThat(validatorMetricData.validator_total).isNotNull();
    assertThat(validatorMetricData.validator_active).isNotNull();
  }

  public void waitForSystemMetricPublication() throws URISyntaxException, IOException {
    List<BaseMetricData> publishedData = getPublishedObjects();
    assertThat(publishedData.size()).isEqualTo(3);

    SystemMetricData systemMetricData = (SystemMetricData) publishedData.get(2);
    assertThat(systemMetricData.process).isEqualTo(MetricsDataClient.SYSTEM.getDataClient());
    assertThat(systemMetricData.cpu_node_system_seconds_total).isNotNull();
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
