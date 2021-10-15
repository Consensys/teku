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
    super(network, image);
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

  public void waitForPublication() {
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

  public List<BaseMetricData> getPublishedObjects() throws URISyntaxException, IOException {
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
