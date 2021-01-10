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

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.get_attesting_indices;

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;
import tech.pegasys.teku.api.response.v1.beacon.FinalityCheckpointsResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockRootResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetGenesisResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateFinalityCheckpointsResponse;
import tech.pegasys.teku.api.response.v1.debug.GetStateResponse;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.test.acceptance.dsl.tools.GenesisStateConfig;
import tech.pegasys.teku.test.acceptance.dsl.tools.GenesisStateGenerator;

public class TekuNode extends Node {
  private static final Logger LOG = LogManager.getLogger();

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
                .forPath("/eth/v1/node/identity")
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
    String genesisTime = httpClient.get(getRestApiUrl(), "/eth/v1/beacon/genesis");
    final GetGenesisResponse response =
        jsonProvider.jsonToObject(genesisTime, GetGenesisResponse.class);
    return response.data.genesisTime;
  }

  public UInt64 getGenesisTime() throws IOException {
    waitForGenesis();
    return fetchGenesisTime();
  }

  public void waitForNewBlock() {
    final Bytes32 startingBlockRoot = waitForBeaconHead();
    waitFor(() -> assertThat(fetchBeaconHead().get()).isNotEqualTo(startingBlockRoot));
  }

  public void waitForNewFinalization() {
    UInt64 startingFinalizedEpoch = waitForChainHead().finalized.epoch;
    LOG.debug("Wait for finalized block");
    waitFor(
        () ->
            assertThat(fetchStateFinalityCheckpoints().get().finalized.epoch)
                .isNotEqualTo(startingFinalizedEpoch),
        9,
        MINUTES);
  }

  public void waitUntilInSyncWith(final TekuNode targetNode) {
    LOG.debug("Wait for {} to sync to {}", nodeAlias, targetNode.nodeAlias);
    waitFor(
        () -> {
          final Optional<Bytes32> beaconHead = fetchBeaconHead();
          assertThat(beaconHead).isPresent();
          final Optional<Bytes32> targetBeaconHead = targetNode.fetchBeaconHead();
          assertThat(targetBeaconHead).isPresent();
          assertThat(beaconHead).isEqualTo(targetBeaconHead);
        },
        5,
        MINUTES);
  }

  private Bytes32 waitForBeaconHead() {
    LOG.debug("Waiting for beacon head");
    final AtomicReference<Bytes32> beaconHead = new AtomicReference<>(null);
    waitFor(
        () -> {
          final Optional<Bytes32> fetchedHead = fetchBeaconHead();
          assertThat(fetchedHead).isPresent();
          beaconHead.set(fetchedHead.get());
        });
    LOG.debug("Retrieved beacon head: {}", beaconHead.get());
    return beaconHead.get();
  }

  private Optional<Bytes32> fetchBeaconHead() throws IOException {
    final String result = httpClient.get(getRestApiUrl(), "/eth/v1/beacon/blocks/head/root");
    if (result.isEmpty()) {
      return Optional.empty();
    }

    final GetBlockRootResponse response =
        jsonProvider.jsonToObject(result, GetBlockRootResponse.class);

    return Optional.of(response.data.root);
  }

  private FinalityCheckpointsResponse waitForChainHead() {
    LOG.debug("Waiting for chain head");
    final AtomicReference<FinalityCheckpointsResponse> chainHead = new AtomicReference<>(null);
    waitFor(
        () -> {
          final Optional<FinalityCheckpointsResponse> fetchCheckpoints =
              fetchStateFinalityCheckpoints();
          assertThat(fetchCheckpoints).isPresent();
          chainHead.set(fetchCheckpoints.get());
        });
    LOG.debug("Retrieved chain head: {}", chainHead.get());
    return chainHead.get();
  }

  private Optional<FinalityCheckpointsResponse> fetchStateFinalityCheckpoints() throws IOException {
    final String result =
        httpClient.get(getRestApiUrl(), "/eth/v1/beacon/states/head/finality_checkpoints");
    if (result.isEmpty()) {
      return Optional.empty();
    }
    final GetStateFinalityCheckpointsResponse response =
        jsonProvider.jsonToObject(result, GetStateFinalityCheckpointsResponse.class);
    return Optional.of(response.data);
  }

  private Optional<SignedBeaconBlock> fetchHeadBlock() throws IOException {
    final String result = httpClient.get(getRestApiUrl(), "/eth/v1/beacon/blocks/head");
    if (result.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(jsonProvider.jsonToObject(result, GetBlockResponse.class).data);
    }
  }

  private Optional<BeaconState> fetchHeadState() throws IOException {
    final String result = httpClient.get(getRestApiUrl(), "/eth/v1/debug/beacon/states/head");
    if (result.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(jsonProvider.jsonToObject(result, GetStateResponse.class).data);
    }
  }

  public void waitForValidators(int numberOfValidators) {
    waitFor(
        () -> {
          Optional<BeaconState> maybeState = fetchHeadState();
          assertThat(maybeState).isPresent();
          BeaconState state = maybeState.get();
          assertThat(state.asInternalBeaconState().getValidators().size())
              .isEqualTo(numberOfValidators);
        });
  }

  public void waitForAttestationBeingGossiped(
      int validatorSeparationIndex, int totalValidatorCount) {
    List<UInt64> node1Validators =
        IntStream.range(0, validatorSeparationIndex).mapToObj(UInt64::valueOf).collect(toList());
    List<UInt64> node2Validators =
        IntStream.range(validatorSeparationIndex, totalValidatorCount)
            .mapToObj(UInt64::valueOf)
            .collect(toList());
    waitFor(
        () -> {
          final Optional<SignedBeaconBlock> maybeBlock = fetchHeadBlock();
          final Optional<BeaconState> maybeState = fetchHeadState();
          assertThat(maybeBlock).isPresent();
          assertThat(maybeState).isPresent();
          SignedBeaconBlock block = maybeBlock.get();
          BeaconState state = maybeState.get();

          // Check that the fetched block and state are in sync
          assertThat(state.latest_block_header.parent_root).isEqualTo(block.message.parent_root);

          tech.pegasys.teku.datastructures.state.BeaconState internalBeaconState =
              state.asInternalBeaconState();
          UInt64 proposerIndex = block.message.proposer_index;

          Set<UInt64> attesterIndicesInAttestations =
              block.message.body.attestations.stream()
                  .map(
                      a ->
                          get_attesting_indices(
                              internalBeaconState,
                              a.asInternalAttestation().getData(),
                              a.asInternalAttestation().getAggregation_bits()))
                  .flatMap(Collection::stream)
                  .map(UInt64::valueOf)
                  .collect(toSet());

          if (node1Validators.contains(proposerIndex)) {
            assertThat(attesterIndicesInAttestations.stream().anyMatch(node2Validators::contains))
                .isTrue();
          } else if (node2Validators.contains(proposerIndex)) {
            assertThat(attesterIndicesInAttestations.stream().anyMatch(node1Validators::contains))
                .isTrue();
          } else {
            throw new IllegalStateException("Proposer index greater than total validator count");
          }
        },
        2,
        MINUTES);
  }

  public Config getConfig() {
    return config;
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

  public void copyFileToContainer(File file, String containerPath) {
    container.withCopyFileToContainer(
        MountableFile.forHostPath(file.getAbsolutePath()), containerPath);
  }

  String getMultiAddr() {
    return "/dns4/" + nodeAlias + "/tcp/" + P2P_PORT + "/p2p/" + config.getPeerId();
  }

  public String getBeaconRestApiUrl() {
    final String url = "http://" + nodeAlias + ":" + REST_API_PORT;
    LOG.debug("Node REST url: " + url);
    return url;
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
      configMap.put("Xstartup-target-peer-count", 0);
      configMap.put("Xinterop-owned-validator-count", DEFAULT_VALIDATOR_COUNT);
      configMap.put("Xinterop-number-of-validators", DEFAULT_VALIDATOR_COUNT);
      configMap.put("Xinterop-enabled", true);
      configMap.put("rest-api-enabled", true);
      configMap.put("rest-api-port", REST_API_PORT);
      configMap.put("rest-api-docs-enabled", false);
      configMap.put("data-path", DATA_PATH);
      configMap.put("eth1-deposit-contract-address", "0xdddddddddddddddddddddddddddddddddddddddd");
      configMap.put("eth1-endpoint", "http://notvalid.com");
      configMap.put("log-destination", "console");
      configMap.put("rest-api-host-allowlist", "*");
    }

    public Config withDepositsFrom(final BesuNode eth1Node) {
      configMap.put("Xinterop-enabled", false);
      configMap.put("eth1-deposit-contract-address", eth1Node.getDepositContractAddress());
      configMap.put("eth1-endpoint", eth1Node.getInternalJsonRpcUrl());
      return this;
    }

    public Config withInteropValidators(final int startIndex, final int validatorCount) {
      configMap.put("Xinterop-owned-validator-start-index", startIndex);
      configMap.put("Xinterop-owned-validator-count", validatorCount);
      return this;
    }

    public Config withInteropNumberOfValidators(final int validatorCount) {
      configMap.put("Xinterop-number-of-validators", validatorCount);
      return this;
    }

    public Config withGenesisTime(int time) {
      configMap.put("Xinterop-genesis-time", time);
      return this;
    }

    public Config withNetwork(String networkName) {
      configMap.put("network", networkName);
      return this;
    }

    public Config withRealNetwork() {
      configMap.put("p2p-enabled", true);
      return this;
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
