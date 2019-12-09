/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.test.acceptance.dsl;

import static org.apache.tuweni.toml.Toml.tomlEscape;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.util.Waiter.waitFor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.test.acceptance.dsl.data.BeaconHead;

public class ArtemisNode {
  private static final int REST_API_PORT = 9051;
  private static final Logger LOG = LogManager.getLogger();

  private final SimpleHttpClient httpClient;
  private final Config config = new Config();

  private final GenericContainer<?> container =
      new GenericContainer<>("pegasyseng/artemis:develop")
          .withExposedPorts(REST_API_PORT)
          .withLogConsumer(frame -> LOG.debug(frame.getUtf8String().trim()))
          .waitingFor(
              new HttpWaitStrategy()
                  .forPort(config.getRestApiPortNumber())
                  .forPath("/network/peer_id"));
  private boolean started = false;
  private File configFile;

  ArtemisNode(final SimpleHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public void start() throws Exception {
    assertThat(started).isFalse();
    started = true;
    configFile = File.createTempFile("config", ".toml");
    configFile.deleteOnExit();
    config.writeTo(configFile);
    container.withCopyFileToContainer(
        MountableFile.forHostPath(configFile.getAbsolutePath()), "/config.toml");
    container.setCommand("--config", "/config.toml");
    container.start();
  }

  public void waitForGenesis() {
    waitFor(() -> httpClient.get(getRestApiUrl(), "/node/genesis_time"));
  }

  public void waitForNewBlock() throws IOException {
    final Bytes32 startingBlockRoot = getCurrentBeaconHead().getBlockRoot();
    waitFor(
        () -> assertThat(getCurrentBeaconHead().getBlockRoot()).isNotEqualTo(startingBlockRoot));
  }

  private BeaconHead getCurrentBeaconHead() throws IOException {
    final BeaconHead beaconHead =
        JsonProvider.jsonToObject(
            httpClient.get(getRestApiUrl(), "/beacon/head"), BeaconHead.class);
    LOG.debug("Retrieved beacon head: {}", beaconHead);
    return beaconHead;
  }

  private URI getRestApiUrl() {
    return URI.create("http://127.0.0.1:" + container.getMappedPort(config.getRestApiPortNumber()));
  }

  public void stop() {
    if (!started) {
      return;
    }
    LOG.debug("Shutting down");
    if (!configFile.delete() && configFile.exists()) {
      throw new RuntimeException("Failed to delete config file: " + configFile);
    }
    container.stop();
  }

  private static class Config {

    private static final String BEACONRESTAPI_SECTION = "beaconrestapi";
    private static final String DEPOSIT_SECTION = "deposit";
    private static final String INTEROP_SECTION = "interop";
    private static final String NODE_SECTION = "node";
    private Map<String, Map<String, Object>> options = new HashMap<>();
    private static final int DEFAULT_VALIDATOR_COUNT = 64;

    public Config() {
      final Map<String, Object> node = getSection(NODE_SECTION);
      node.put("networkMode", "mock");
      node.put("networkInterface", "127.0.0.1");
      node.put("port", 9000);
      node.put("discovery", "static");
      node.put("constants", "minimal");

      final Map<String, Object> interop = getSection(INTEROP_SECTION);
      interop.put("genesisTime", 0);
      interop.put("ownedValidatorStartIndex", 0);
      interop.put("ownedValidatorCount", DEFAULT_VALIDATOR_COUNT);

      final Map<String, Object> deposit = getSection(DEPOSIT_SECTION);
      deposit.put("mode", "test");
      deposit.put("numValidators", DEFAULT_VALIDATOR_COUNT);

      final Map<String, Object> beaconRestApi = getSection(BEACONRESTAPI_SECTION);
      beaconRestApi.put("portNumber", REST_API_PORT);
    }

    public int getRestApiPortNumber() {
      return (int) getSection(BEACONRESTAPI_SECTION).get("portNumber");
    }

    private Map<String, Object> getSection(final String interop) {
      return options.computeIfAbsent(interop, key -> new HashMap<>());
    }

    public void writeTo(final File configFile) throws Exception {
      try (PrintWriter out =
          new PrintWriter(Files.newBufferedWriter(configFile.toPath(), StandardCharsets.UTF_8))) {
        for (Entry<String, Map<String, Object>> entry : options.entrySet()) {
          String sectionName = entry.getKey();
          Map<String, Object> section = entry.getValue();
          out.println("[" + tomlEscape(sectionName) + "]");

          for (Entry<String, Object> e : section.entrySet()) {
            out.print(e.getKey() + "=");
            writeValue(e.getValue(), out);
            out.println();
          }
          out.println();
        }
      }
    }

    private void writeValue(final Object value, final PrintWriter out) {
      if (value instanceof String) {
        out.print("\"" + tomlEscape((String) value) + "\"");
      } else if (value instanceof List) {
        out.print("[");
        writeList((List<?>) value, out);
        out.print("]");
      } else {
        out.print(value.toString());
      }
    }

    private void writeList(final List<?> values, final PrintWriter out) {
      for (int i = 0; i < values.size(); i++) {
        writeValue(values.get(i), out);
        if (i < values.size()) {
          out.print(",");
        }
      }
    }
  }
}
