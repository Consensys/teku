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

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;
import tech.pegasys.teku.api.response.v1.beacon.FinalityCheckpointsResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockRootResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetGenesisResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateFinalityCheckpointsResponse;
import tech.pegasys.teku.api.response.v1.debug.GetStateResponse;
import tech.pegasys.teku.api.response.v2.beacon.GetBlockResponseV2;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.altair.SignedBeaconBlockAltair;
import tech.pegasys.teku.api.schema.interfaces.SignedBlock;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.ssz.collections.SszBitvector;
import tech.pegasys.teku.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.test.acceptance.dsl.tools.GenesisStateConfig;
import tech.pegasys.teku.test.acceptance.dsl.tools.GenesisStateGenerator;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class TekuNode extends Node {
  private static final Logger LOG = LogManager.getLogger();

  private final SimpleHttpClient httpClient;
  private final Config config;
  private final JsonProvider jsonProvider = new JsonProvider();
  private final Spec spec;

  private boolean started = false;
  private Set<File> configFiles;

  private TekuNode(final SimpleHttpClient httpClient, final Network network, final Config config) {
    super(network, TEKU_DOCKER_IMAGE, LOG);
    this.httpClient = httpClient;
    this.config = config;
    this.spec = SpecFactory.create(config.getNetworkName());

    container
        .withWorkingDirectory(WORKING_DIRECTORY)
        .withExposedPorts(REST_API_PORT, METRICS_PORT)
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
    config.getTarballsToCopy().forEach(this::copyContentsToWorkingDirectory);
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

  public void waitForFullSyncCommitteeAggregate() {
    LOG.debug("Wait for full sync committee aggregates");
    waitFor(
        () -> {
          final Optional<SignedBlock> block = fetchHeadBlock();
          assertThat(block).isPresent();
          assertThat(block.get()).isInstanceOf(SignedBeaconBlockAltair.class);

          final SignedBeaconBlockAltair altairBlock = (SignedBeaconBlockAltair) block.get();
          final int syncCommitteeSize = spec.getSyncCommitteeSize(altairBlock.getMessage().slot);
          final SszBitvectorSchema<SszBitvector> syncCommitteeSchema =
              SszBitvectorSchema.create(syncCommitteeSize);

          final Bytes syncCommitteeBits =
              altairBlock.getMessage().getBody().syncAggregate.syncCommitteeBits;
          final int actualSyncBitCount =
              syncCommitteeSchema.sszDeserialize(syncCommitteeBits).getBitCount();
          final double percentageOfBitsSet =
              actualSyncBitCount == syncCommitteeSize
                  ? 1.0
                  : actualSyncBitCount / (double) syncCommitteeSize;
          if (percentageOfBitsSet < 1.0) {
            LOG.debug(
                String.format(
                    "Sync committee bits are only %s%% full, expecting %s%%: %s",
                    percentageOfBitsSet * 100, 100, syncCommitteeBits));
          }
          assertThat(percentageOfBitsSet >= 1.0).isTrue();
        },
        5,
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

  private Optional<SignedBlock> fetchHeadBlock() throws IOException {
    final String result = httpClient.get(getRestApiUrl(), "/eth/v2/beacon/blocks/head");
    if (result.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(jsonProvider.jsonToObject(result, GetBlockResponseV2.class).data);
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
          assertThat(state.asInternalBeaconState(spec).getValidators().size())
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
          final Optional<SignedBlock> maybeBlock = fetchHeadBlock();
          final Optional<BeaconState> maybeState = fetchHeadState();
          assertThat(maybeBlock).isPresent();
          assertThat(maybeState).isPresent();
          SignedBeaconBlock block = (SignedBeaconBlock) maybeBlock.get();
          BeaconState state = maybeState.get();

          // Check that the fetched block and state are in sync
          assertThat(state.latest_block_header.parent_root)
              .isEqualTo(block.getMessage().parent_root);

          tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalBeaconState =
              state.asInternalBeaconState(spec);
          UInt64 proposerIndex = block.getMessage().proposer_index;

          Set<UInt64> attesterIndicesInAttestations =
              block.getMessage().getBody().attestations.stream()
                  .map(
                      a ->
                          spec.getAttestingIndices(
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

  public void addValidators(final ValidatorKeystores additionalKeystores) {
    LOG.debug("Adding {} validators", additionalKeystores.getValidatorCount());
    container.expandTarballToContainer(additionalKeystores.getTarball(), WORKING_DIRECTORY);
    container
        .getDockerClient()
        .killContainerCmd(container.getContainerId())
        .withSignal("HUP")
        .exec();
  }

  public void waitForOwnedValidatorCount(final int expectedValidatorCount) throws IOException {
    LOG.debug("Waiting for validator count to be {}", expectedValidatorCount);
    waitFor(
        () -> {
          final String validatorCount = getMetricValue("validator_local_validator_count");
          LOG.debug("Current validator count {}", validatorCount);
          assertThat(Double.parseDouble(validatorCount)).isEqualTo(expectedValidatorCount);
        });
  }

  private String getMetricValue(final String metricName) throws IOException {
    final String prefix = metricName + " ";
    final String allMetrics = httpClient.get(getMetricsUrl(), "/metrics");
    try (BufferedReader reader = new BufferedReader(new StringReader(allMetrics))) {
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        if (line.startsWith(prefix)) {
          return line.substring(prefix.length());
        }
      }
    }
    throw new IllegalArgumentException(
        "Did not find metric " + metricName + " in: \n" + allMetrics);
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

  private URI getMetricsUrl() {
    return URI.create("http://127.0.0.1:" + container.getMappedPort(METRICS_PORT));
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
    public static String DEFAULT_NETWORK_NAME = "swift";

    private final PrivKey privateKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
    private final PeerId peerId = PeerId.fromPubKey(privateKey.publicKey());
    private static final int DEFAULT_VALIDATOR_COUNT = 64;

    private String networkName = DEFAULT_NETWORK_NAME;
    private final Map<String, Object> configMap = new HashMap<>();
    private final List<File> tarballsToCopy = new ArrayList<>();

    private Optional<GenesisStateConfig> genesisStateConfig = Optional.empty();

    public Config() {
      configMap.put("network", networkName);
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
      configMap.put("validators-keystore-locking-enabled", false);
      configMap.put("rest-api-enabled", true);
      configMap.put("rest-api-port", REST_API_PORT);
      configMap.put("rest-api-docs-enabled", false);
      configMap.put("metrics-enabled", true);
      configMap.put("metrics-port", METRICS_PORT);
      configMap.put("metrics-interface", "0.0.0.0");
      configMap.put("metrics-host-allowlist", "*");
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

    public Config withLogging(final String logging) {
      configMap.put("logging", logging);
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

    public Config withValidatorKeystores(final ValidatorKeystores keystores) {
      tarballsToCopy.add(keystores.getTarball());
      configMap.put(
          "validator-keys",
          WORKING_DIRECTORY
              + keystores.getKeysDirectoryName()
              + ":"
              + WORKING_DIRECTORY
              + keystores.getPasswordsDirectoryName());
      return this;
    }

    public Config withGenesisTime(int time) {
      configMap.put("Xinterop-genesis-time", time);
      return this;
    }

    public Config withNetwork(String networkName) {
      this.networkName = networkName;
      configMap.put("network", networkName);
      return this;
    }

    public Config withAltairEpoch(final UInt64 altairSlot) {
      configMap.put("Xnetwork-altair-fork-epoch", altairSlot.toString());
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

      final File configFile = File.createTempFile("config", ".yaml");
      configFile.deleteOnExit();
      writeTo(configFile);
      configFiles.put(configFile, CONFIG_FILE_PATH);
      return configFiles;
    }

    public String getNetworkName() {
      return networkName;
    }

    private void writeTo(final File configFile) throws Exception {
      YAML_MAPPER.writeValue(configFile, configMap);
    }

    private List<File> getTarballsToCopy() {
      return tarballsToCopy;
    }
  }
}
