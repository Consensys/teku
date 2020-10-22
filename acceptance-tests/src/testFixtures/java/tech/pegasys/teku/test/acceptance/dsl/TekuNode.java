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

package tech.pegasys.teku.test.acceptance.dsl;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;
import tech.pegasys.teku.api.schema.BeaconChainHead;
import tech.pegasys.teku.api.schema.BeaconHead;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.test.acceptance.dsl.tools.GenesisStateConfig;
import tech.pegasys.teku.test.acceptance.dsl.tools.GenesisStateGenerator;

public class TekuNode extends Node {
  private static final Logger LOG = LogManager.getLogger();

  public static final String TEKU_DOCKER_IMAGE = "consensys/teku:develop";
  private static final int REST_API_PORT = 9051;
  private static final String CONFIG_FILE_PATH = "/config.yaml";
  protected static final String WORKING_DIRECTORY = "/opt/teku/";
  private static final String DATA_PATH = WORKING_DIRECTORY + "data/";
  private static final int P2P_PORT = 9000;
  private static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory().disable(WRITE_DOC_START_MARKER));

  private final SimpleHttpClient httpClient;
  private final Config config;
  private final JsonProvider jsonProvider = new JsonProvider();

  private boolean started = false;
  private Set<File> configFiles;

  private TekuNode(final SimpleHttpClient httpClient, final Network network, final Config config) {
    super(network, TEKU_DOCKER_IMAGE, LOG);
    this.httpClient = httpClient;
    this.config = config;

    container
        .withWorkingDirectory(WORKING_DIRECTORY)
        .withExposedPorts(REST_API_PORT)
        .waitingFor(
            new HttpWaitStrategy()
                .forPort(REST_API_PORT)
                .forPath("/network/peer_id")
                .withStartupTimeout(Duration.ofMinutes(2)))
        .withCommand("--config-file", CONFIG_FILE_PATH);
  }

  public static TekuNode create(
      final SimpleHttpClient httpClient,
      final Network network,
      Consumer<Config> configOptions,
      final GenesisStateGenerator genesisStateGenerator)
      throws TimeoutException, IOException {

    final Config config = new Config();
    configOptions.accept(config);

    final TekuNode node = new TekuNode(httpClient, network, config);

    if (config.getGenesisStateConfig().isPresent()) {
      final GenesisStateConfig genesisConfig = config.getGenesisStateConfig().get();
      File genesisFile = genesisStateGenerator.generateState(genesisConfig);
      node.copyFileToContainer(genesisFile, genesisConfig.getPath());
    }

    return node;
  }

  public void start() throws Exception {
    assertThat(started).isFalse();
    LOG.debug("Start node {}", nodeAlias);
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
    LOG.debug("Wait for genesis");
    waitFor(this::fetchGenesisTime);
  }

  public void waitForGenesisTime(final UInt64 expectedGenesisTime) {
    waitFor(() -> assertThat(fetchGenesisTime()).isEqualTo(expectedGenesisTime));
  }

  private UInt64 fetchGenesisTime() throws IOException {
    String genesisTime = httpClient.get(getRestApiUrl(), "/node/genesis_time");
    return jsonProvider.jsonToObject(genesisTime, UInt64.class);
  }

  public UInt64 getGenesisTime() throws IOException {
    waitForGenesis();
    return fetchGenesisTime();
  }

  public void waitForNewBlock() {
    final Bytes32 startingBlockRoot = waitForBeaconHead().block_root;
    waitFor(() -> assertThat(fetchBeaconHead().get().block_root).isNotEqualTo(startingBlockRoot));
  }

  public void waitForNewFinalization() {
    UInt64 startingFinalizedEpoch = waitForChainHead().finalized_epoch;
    LOG.debug("Wait for finalized block");
    waitFor(
        () ->
            assertThat(fetchChainHead().get().finalized_epoch).isNotEqualTo(startingFinalizedEpoch),
        9,
        MINUTES);
  }

  public void waitUntilInSyncWith(final TekuNode targetNode) {
    LOG.debug("Wait for {} to sync to {}", nodeAlias, targetNode.nodeAlias);
    waitFor(
        () -> {
          final Optional<BeaconHead> beaconHead = fetchBeaconHead();
          assertThat(beaconHead).isPresent();
          final Optional<BeaconHead> targetBeaconHead = targetNode.fetchBeaconHead();
          assertThat(targetBeaconHead).isPresent();
          assertThat(beaconHead).isEqualTo(targetBeaconHead);
        },
        5,
        MINUTES);
  }

  private BeaconHead waitForBeaconHead() {
    LOG.debug("Waiting for beacon head");
    final AtomicReference<BeaconHead> beaconHead = new AtomicReference<>(null);
    waitFor(
        () -> {
          final Optional<BeaconHead> fetchedHead = fetchBeaconHead();
          assertThat(fetchedHead).isPresent();
          beaconHead.set(fetchedHead.get());
        });
    LOG.debug("Retrieved beacon head: {}", beaconHead.get());
    return beaconHead.get();
  }

  private Optional<BeaconHead> fetchBeaconHead() throws IOException {
    final String result = httpClient.get(getRestApiUrl(), "/beacon/head");
    if (result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        jsonProvider.jsonToObject(
            httpClient.get(getRestApiUrl(), "/beacon/head"), BeaconHead.class));
  }

  private BeaconChainHead waitForChainHead() {
    LOG.debug("Waiting for chain head");
    final AtomicReference<BeaconChainHead> chainHead = new AtomicReference<>(null);
    waitFor(
        () -> {
          final Optional<BeaconChainHead> fetchedHead = fetchChainHead();
          assertThat(fetchedHead).isPresent();
          chainHead.set(fetchedHead.get());
        });
    LOG.debug("Retrieved chain head: {}", chainHead.get());
    return chainHead.get();
  }

  private Optional<BeaconChainHead> fetchChainHead() throws IOException {
    final String result = httpClient.get(getRestApiUrl(), "/beacon/chainhead");
    if (result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        jsonProvider.jsonToObject(
            httpClient.get(getRestApiUrl(), "/beacon/chainhead"), BeaconChainHead.class));
  }

  /**
   * Copies data directory from node into a temporary directory.
   *
   * @return A file containing the data directory.
   * @throws Exception
   */
  public File getDataDirectoryFromContainer() throws Exception {
    File dbTar = File.createTempFile("database", ".tar");
    dbTar.deleteOnExit();
    copyDirectoryToTar(DATA_PATH, dbTar);
    return dbTar;
  }

  /**
   * Copies contents of the given directory into node's working directory.
   *
   * @param tarFile
   * @throws IOException
   */
  public void copyContentsToWorkingDirectory(File tarFile) throws IOException {
    container.withExpandedTarballToContainer(tarFile, WORKING_DIRECTORY);
  }

  public void copyFileToContainer(File file, String containerPath) {
    container.withCopyFileToContainer(
        MountableFile.forHostPath(file.getAbsolutePath()), containerPath);
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

  @Override
  public void captureDebugArtifacts(final File artifactDir) {
    if (container.isRunning()) {
      copyDirectoryToTar(WORKING_DIRECTORY, new File(artifactDir, nodeAlias + ".tar"));
    } else {
      // Can't capture artifacts if it's not running but then it probably didn't cause the failure
      LOG.debug("Not capturing artifacts from {} because it is not running", nodeAlias);
    }
  }

  public static class Config {

    private final PrivKey privateKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
    private final PeerId peerId = PeerId.fromPubKey(privateKey.publicKey());
    private static final String VALIDATORS_FILE_PATH = "/validators.yml";
    private static final int DEFAULT_VALIDATOR_COUNT = 64;

    private Map<String, Object> configMap = new HashMap<>();

    private Optional<String> validatorKeys = Optional.empty();
    private Optional<GenesisStateConfig> genesisStateConfig = Optional.empty();

    public Config() {
      configMap.put("network", "swift");
      configMap.put("p2p-enabled", false);
      configMap.put("p2p-discovery-enabled", false);
      configMap.put("p2p-port", P2P_PORT);
      configMap.put("p2p-advertised-port", P2P_PORT);
      configMap.put("p2p-interface", "0.0.0.0");
      configMap.put("Xinterop-genesis-time", 0);
      configMap.put("Xinterop-owned-validator-start-index", 0);
      configMap.put("Xinterop-owned-validator-count", DEFAULT_VALIDATOR_COUNT);
      configMap.put("Xinterop-number-of-validators", DEFAULT_VALIDATOR_COUNT);
      configMap.put("Xinterop-enabled", true);
      configMap.put("rest-api-enabled", true);
      configMap.put("rest-api-port", REST_API_PORT);
      configMap.put("rest-api-docs-enabled", false);
      configMap.put("Xtransition-record-directory", WORKING_DIRECTORY + "transitions/");
      configMap.put("data-path", DATA_PATH);
      configMap.put("eth1-deposit-contract-address", "0xdddddddddddddddddddddddddddddddddddddddd");
      configMap.put("eth1-endpoint", "http://notvalid.com");
      configMap.put("log-destination", "console");
    }

    public Config withDepositsFrom(final BesuNode eth1Node) {
      configMap.put("Xinterop-enabled", false);
      configMap.put("eth1-deposit-contract-address", eth1Node.getDepositContractAddress());
      configMap.put("eth1-endpoint", eth1Node.getInternalJsonRpcUrl());
      return this;
    }

    public Config withValidatorKeys(final String validatorKeys) {
      this.validatorKeys = Optional.of(validatorKeys);
      return this;
    }

    public Config withInteropValidators(final int startIndex, final int validatorCount) {
      configMap.put("Xinterop-owned-validator-start-index", startIndex);
      configMap.put("Xinterop-owned-validator-count", validatorCount);
      return this;
    }

    public Config withGenesisTime(int time) {
      configMap.put("Xinterop-genesis-time", time);
      return this;
    }

    public Config withRealNetwork() {
      configMap.put("p2p-enabled", true);
      return this;
    }

    public Config withGenesisState(String pathToGenesisState) {
      checkNotNull(pathToGenesisState);
      configMap.put("initial-state", pathToGenesisState);
      return this;
    }

    /**
     * Configures parameters for generating a genesis state.
     *
     * @param config Configuration defining how to generate the genesis state.
     * @return this config
     */
    public Config withGenesisConfig(final GenesisStateConfig config) {
      checkNotNull(config);
      this.genesisStateConfig = Optional.of(config);
      return withGenesisState(config.getPath());
    }

    public Config withPeers(final TekuNode... nodes) {
      final String peers =
          asList(nodes).stream().map(TekuNode::getMultiAddr).collect(Collectors.joining(", "));
      LOG.debug("Set peers: {}", peers);
      configMap.put("p2p-static-peers", peers);
      return this;
    }

    public String getPeerId() {
      return peerId.toBase58();
    }

    public Optional<GenesisStateConfig> getGenesisStateConfig() {
      return genesisStateConfig;
    }

    public Map<File, String> write() throws Exception {
      final Map<File, String> configFiles = new HashMap<>();
      if (validatorKeys.isPresent()) {
        final File validatorsFile = Files.createTempFile("validators", ".yml").toFile();
        validatorsFile.deleteOnExit();
        Files.writeString(validatorsFile.toPath(), validatorKeys.get());
        configFiles.put(validatorsFile, VALIDATORS_FILE_PATH);
      }

      final File configFile = File.createTempFile("config", ".yaml");
      configFile.deleteOnExit();
      writeTo(configFile);
      configFiles.put(configFile, CONFIG_FILE_PATH);
      return configFiles;
    }

    private void writeTo(final File configFile) throws Exception {
      YAML_MAPPER.writeValue(configFile, configMap);
    }
  }
}
