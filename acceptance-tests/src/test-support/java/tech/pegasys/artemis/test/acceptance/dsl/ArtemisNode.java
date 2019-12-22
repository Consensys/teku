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

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.tuweni.toml.Toml.tomlEscape;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.test.acceptance.dsl.data.BeaconHead;
import tech.pegasys.artemis.test.acceptance.dsl.data.FinalizedCheckpoint;

public class ArtemisNode extends Node {
  private static final Logger LOG = LogManager.getLogger();

  public static final String ARTEMIS_DOCKER_IMAGE = "pegasyseng/artemis:develop";
  private static final int REST_API_PORT = 9051;
  private static final String CONFIG_FILE_PATH = "/config.toml";
  private static final int P2P_PORT = 9000;

  private final SimpleHttpClient httpClient;
  private final Config config = new Config();

  private boolean started = false;
  private Set<File> configFiles;

  ArtemisNode(
      final SimpleHttpClient httpClient,
      final Network network,
      final Consumer<Config> configOptions) {
    super(network, ARTEMIS_DOCKER_IMAGE, LOG);
    this.httpClient = httpClient;
    configOptions.accept(config);
    container
        .withExposedPorts(REST_API_PORT)
        .waitingFor(new HttpWaitStrategy().forPort(REST_API_PORT).forPath("/network/peer_id"))
        .withCommand("--config", CONFIG_FILE_PATH);
  }

  public void start() throws Exception {
    assertThat(started).isFalse();
    started = true;
    final Map<File, String> configFiles = config.write();
    this.configFiles = configFiles.keySet();
    configFiles.forEach(
        (localFile, targetPath) ->
            container.withCopyFileToContainer(
                MountableFile.forHostPath(localFile.getAbsolutePath()), targetPath));
    container.start();
  }

  public void waitForGenesis() {
    waitFor(this::fetchGenesisTime);
  }

  public void waitForGenesisTime(final UnsignedLong expectedGenesisTime) {
    waitFor(() -> assertThat(fetchGenesisTime()).isEqualTo(expectedGenesisTime));
  }

  private UnsignedLong fetchGenesisTime() throws IOException {
    return UnsignedLong.valueOf(httpClient.get(getRestApiUrl(), "/node/genesis_time"));
  }

  public UnsignedLong getGenesisTime() throws IOException {
    waitForGenesis();
    return fetchGenesisTime();
  }

  public void waitForNewBlock() throws IOException {
    final Bytes32 startingBlockRoot = getCurrentBeaconHead().getBlockRoot();
    waitFor(
        () -> assertThat(getCurrentBeaconHead().getBlockRoot()).isNotEqualTo(startingBlockRoot));
  }

  public void waitForNewFinalization() throws IOException {
    UnsignedLong startingFinalizedEpoch = getCurrentFinalizedCheckpoint().getEpoch();
    waitFor(
        () ->
            assertThat(getCurrentFinalizedCheckpoint().getEpoch())
                .isNotEqualTo(startingFinalizedEpoch),
        540);
  }

  public void waitUntilInSyncWith(final ArtemisNode targetNode) {
    waitFor(() -> assertThat(getCurrentBeaconHead()).isEqualTo(targetNode.getCurrentBeaconHead()));
  }

  private BeaconHead getCurrentBeaconHead() throws IOException {
    final BeaconHead beaconHead =
        JsonProvider.jsonToObject(
            httpClient.get(getRestApiUrl(), "/beacon/head"), BeaconHead.class);
    LOG.debug("Retrieved beacon head: {}", beaconHead);
    return beaconHead;
  }

  private FinalizedCheckpoint getCurrentFinalizedCheckpoint() throws IOException {
    final FinalizedCheckpoint finalizedCheckpoint =
        JsonProvider.jsonToObject(
            httpClient.get(getRestApiUrl(), "/beacon/finalized_checkpoint"),
            FinalizedCheckpoint.class);
    LOG.debug("Retrieved finalized checkpoint: {}", finalizedCheckpoint);
    return finalizedCheckpoint;
  }

  public File getDatabaseFileFromContainer() throws Exception {
    File tempDatabaseFile = File.createTempFile("artemis", ".db");
    tempDatabaseFile.deleteOnExit();
    container.copyFileFromContainer("/artemis.db", tempDatabaseFile.getAbsolutePath());
    return tempDatabaseFile;
  }

  public void copyDatabaseFileToContainer(File databaseFile) {
    container.withCopyFileToContainer(
        MountableFile.forHostPath(databaseFile.getAbsolutePath()), "/artemis.db");
  }

  String getMultiAddr() {
    return "/dns4/" + nodeAlias + "/tcp/" + P2P_PORT + "/p2p/" + config.getPeerId();
  }

  private URI getRestApiUrl() {
    return URI.create("http://127.0.0.1:" + container.getMappedPort(REST_API_PORT));
  }

  @Override
  public void stop() {
    if (!started) {
      return;
    }
    LOG.debug("Shutting down");
    configFiles.forEach(
        configFile -> {
          if (!configFile.delete() && configFile.exists()) {
            throw new RuntimeException("Failed to delete config file: " + configFile);
          }
        });
    container.stop();
  }

  public static class Config {

    private final PrivKey privateKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
    private final PeerId peerId = PeerId.fromPubKey(privateKey.publicKey());

    private static final String DATABASE_SECTION = "database";
    private static final String BEACONRESTAPI_SECTION = "beaconrestapi";
    private static final String DEPOSIT_SECTION = "deposit";
    private static final String INTEROP_SECTION = "interop";
    private static final String NODE_SECTION = "node";
    private static final String VALIDATOR_SECTION = "validator";
    private static final String VALIDATORS_FILE_PATH = "/validators.yml";
    private Map<String, Map<String, Object>> options = new HashMap<>();
    private static final int DEFAULT_VALIDATOR_COUNT = 64;

    private Optional<String> validatorKeys = Optional.empty();

    public Config() {
      final Map<String, Object> node = getSection(NODE_SECTION);
      setNetworkMode("mock");
      node.put("networkInterface", "0.0.0.0");
      node.put("port", P2P_PORT);
      node.put("discovery", "static");
      node.put("constants", "minimal");

      final Map<String, Object> interop = getSection(INTEROP_SECTION);
      interop.put("genesisTime", 0);
      interop.put("ownedValidatorStartIndex", 0);
      interop.put("ownedValidatorCount", DEFAULT_VALIDATOR_COUNT);

      final Map<String, Object> deposit = getSection(DEPOSIT_SECTION);
      setDepositMode("test");
      deposit.put("numValidators", DEFAULT_VALIDATOR_COUNT);

      final Map<String, Object> beaconRestApi = getSection(BEACONRESTAPI_SECTION);
      beaconRestApi.put("portNumber", REST_API_PORT);
    }

    public Config withDepositsFrom(final BesuNode eth1Node) {
      setDepositMode("normal");
      final Map<String, Object> depositSection = getSection(DEPOSIT_SECTION);
      depositSection.put("contractAddr", eth1Node.getDepositContractAddress());
      depositSection.put("nodeUrl", eth1Node.getInternalJsonRpcUrl());
      return this;
    }

    public Config startFromDisk() {
      getSection(DATABASE_SECTION).put("startFromDisk", true);
      return this;
    }

    public Config withValidatorKeys(final String validatorKeys) {
      this.validatorKeys = Optional.of(validatorKeys);
      return this;
    }

    public Config withRealNetwork() {
      setNetworkMode("jvmlibp2p");
      getSection(INTEROP_SECTION).put("privateKey", Bytes.wrap(privateKey.bytes()).toHexString());
      return this;
    }

    public Config withPeers(final ArtemisNode... nodes) {
      getSection(NODE_SECTION)
          .put("peers", asList(nodes).stream().map(ArtemisNode::getMultiAddr).collect(toList()));
      return this;
    }

    private void setDepositMode(final String mode) {
      getSection(DEPOSIT_SECTION).put("mode", mode);
    }

    private void setNetworkMode(final String mode) {
      getSection(NODE_SECTION).put("networkMode", mode);
    }

    private Map<String, Object> getSection(final String interop) {
      return options.computeIfAbsent(interop, key -> new HashMap<>());
    }

    public String getPeerId() {
      return peerId.toBase58();
    }

    public Map<File, String> write() throws Exception {
      final Map<File, String> configFiles = new HashMap<>();
      if (validatorKeys.isPresent()) {
        final File validatorsFile = Files.createTempFile("validators", ".yml").toFile();
        validatorsFile.deleteOnExit();
        Files.writeString(validatorsFile.toPath(), validatorKeys.get());
        configFiles.put(validatorsFile, VALIDATORS_FILE_PATH);
        getSection(VALIDATOR_SECTION).put("validatorsKeyFile", VALIDATORS_FILE_PATH);
      }

      final File configFile = File.createTempFile("config", ".toml");
      configFile.deleteOnExit();
      writeTo(configFile);
      configFiles.put(configFile, CONFIG_FILE_PATH);
      return configFiles;
    }

    private void writeTo(final File configFile) throws Exception {
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
