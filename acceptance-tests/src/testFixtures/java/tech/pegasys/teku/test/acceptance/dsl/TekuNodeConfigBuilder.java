/*
 * Copyright Consensys Software Inc., 2024
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.teku.test.acceptance.dsl.Node.DATA_PATH;
import static tech.pegasys.teku.test.acceptance.dsl.Node.JSON_PROVIDER;
import static tech.pegasys.teku.test.acceptance.dsl.Node.JWT_SECRET_FILE_PATH;
import static tech.pegasys.teku.test.acceptance.dsl.Node.METRICS_PORT;
import static tech.pegasys.teku.test.acceptance.dsl.Node.NETWORK_FILE_PATH;
import static tech.pegasys.teku.test.acceptance.dsl.Node.P2P_PORT;
import static tech.pegasys.teku.test.acceptance.dsl.Node.PRIVATE_KEY_FILE_PATH;
import static tech.pegasys.teku.test.acceptance.dsl.Node.REST_API_PORT;
import static tech.pegasys.teku.test.acceptance.dsl.Node.SENTRY_NODE_CONFIG_FILE_PATH;
import static tech.pegasys.teku.test.acceptance.dsl.Node.WORKING_DIRECTORY;
import static tech.pegasys.teku.test.acceptance.dsl.Node.copyToTmpFile;
import static tech.pegasys.teku.test.acceptance.dsl.TekuNode.VALIDATOR_API_PORT;
import static tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfig.DEFAULT_VALIDATOR_COUNT;
import static tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfig.INITIAL_STATE_FILE;

import com.google.common.io.Resources;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.KeyType;
import io.libp2p.core.crypto.PrivKey;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class TekuNodeConfigBuilder {
  private static final Logger LOG = LogManager.getLogger();
  public static final String DEFAULT_NETWORK_NAME = "swift";
  protected static final String TRUSTED_SETUP_FILE = "/trusted-setup.txt";

  public static final String EE_JWT_SECRET_FILE_KEY = "ee-jwt-secret-file";
  private final NodeType nodeType;
  protected final Map<File, String> configFileMap = new HashMap<>();
  protected Consumer<SpecConfigBuilder> specConfigModifier = builder -> {};
  private final Map<String, String> readOnlyMountPoints = new HashMap<>();
  private final Map<String, String> writableMountPoints = new HashMap<>();
  private final List<File> tarballsToCopy = new ArrayList<>();
  private final Map<String, Object> configMap;
  private Optional<PrivKey> maybePrivKey = Optional.empty();
  private Optional<PeerId> maybePeerId = Optional.empty();

  private boolean keyfilesGenerated = false;

  public static TekuNodeConfigBuilder createBeaconNode() throws IOException {
    final PrivKey privKey = KeyKt.generateKeyPair(KeyType.SECP256K1).component1();
    final Map<String, Object> configMap = new HashMap<>();
    configMap.put("network", DEFAULT_NETWORK_NAME);
    configMap.put("p2p-enabled", false);
    configMap.put("p2p-discovery-enabled", false);
    configMap.put("p2p-port", P2P_PORT);
    configMap.put("p2p-advertised-port", P2P_PORT);
    configMap.put("p2p-interface", "0.0.0.0");
    configMap.put("p2p-private-key-file", PRIVATE_KEY_FILE_PATH);
    configMap.put("Xinterop-genesis-time", 0);
    configMap.put("Xinterop-owned-validator-start-index", 0);
    configMap.put("Xstartup-target-peer-count", 0);
    configMap.put("Xinterop-owned-validator-count", DEFAULT_VALIDATOR_COUNT);
    configMap.put("Xinterop-number-of-validators", DEFAULT_VALIDATOR_COUNT);
    configMap.put("Xinterop-enabled", true);
    configMap.put("rest-api-enabled", true);
    configMap.put("rest-api-port", REST_API_PORT);
    configMap.put("rest-api-docs-enabled", false);
    configMap.put("metrics-enabled", true);
    configMap.put("metrics-port", METRICS_PORT);
    configMap.put("metrics-interface", "0.0.0.0");
    configMap.put("metrics-host-allowlist", "*");
    configMap.put("data-path", DATA_PATH);
    configMap.put("eth1-deposit-contract-address", "0xdddddddddddddddddddddddddddddddddddddddd");
    configMap.put("log-destination", "console");
    configMap.put("rest-api-host-allowlist", "*");
    return new TekuNodeConfigBuilder(NodeType.BEACON_NODE, configMap).withPrivateKey(privKey);
  }

  public TekuNodeConfigBuilder withAltairEpoch(final UInt64 altairForkEpoch) {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("Xnetwork-altair-fork-epoch={}", altairForkEpoch);
    configMap.put("Xnetwork-altair-fork-epoch", altairForkEpoch.toString());
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.altairBuilder(
                    altairBuilder -> altairBuilder.altairForkEpoch(altairForkEpoch)));
    return this;
  }

  public TekuNodeConfigBuilder withBellatrixEpoch(final UInt64 bellatrixForkEpoch) {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("Xnetwork-bellatrix-fork-epoch={}", bellatrixForkEpoch);
    configMap.put("Xnetwork-bellatrix-fork-epoch", bellatrixForkEpoch.toString());
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.bellatrixBuilder(
                    bellatrixBuilder -> bellatrixBuilder.bellatrixForkEpoch(bellatrixForkEpoch)));
    return this;
  }

  public TekuNodeConfigBuilder withCapellaEpoch(final UInt64 capellaForkEpoch) {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("Xnetwork-capella-fork-epoch={}", capellaForkEpoch);
    configMap.put("Xnetwork-capella-fork-epoch", capellaForkEpoch.toString());
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.capellaBuilder(
                    capellaBuilder -> capellaBuilder.capellaForkEpoch(capellaForkEpoch)));
    return this;
  }

  public TekuNodeConfigBuilder withTrustedSetupFromClasspath(final String trustedSetup)
      throws Exception {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("Xtrusted-setup={}", TRUSTED_SETUP_FILE);
    configMap.put("Xtrusted-setup", TRUSTED_SETUP_FILE);
    final URL trustedSetupResource = Eth2NetworkConfiguration.class.getResource(trustedSetup);
    assert trustedSetupResource != null;
    configFileMap.put(copyToTmpFile(trustedSetupResource), TRUSTED_SETUP_FILE);
    return this;
  }

  public TekuNodeConfigBuilder withExecutionEngine(final BesuNode node) {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("ee-endpoint={}", node.getInternalEngineJsonRpcUrl());
    configMap.put("ee-endpoint", node.getInternalEngineJsonRpcUrl());
    return this;
  }

  public TekuNodeConfigBuilder withJwtSecretFile(final URL jwtFile) throws Exception {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("{}={}", EE_JWT_SECRET_FILE_KEY, JWT_SECRET_FILE_PATH);
    configMap.put(EE_JWT_SECRET_FILE_KEY, JWT_SECRET_FILE_PATH);
    configFileMap.put(copyToTmpFile(jwtFile), JWT_SECRET_FILE_PATH);
    return this;
  }

  public TekuNodeConfigBuilder withDenebEpoch(final UInt64 denebForkEpoch) {

    mustBe(NodeType.BEACON_NODE);
    LOG.debug("Xnetwork-deneb-fork-epoch={}", denebForkEpoch);
    configMap.put("Xnetwork-deneb-fork-epoch", denebForkEpoch.toString());
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.denebBuilder(
                    denebBuilder -> denebBuilder.denebForkEpoch(denebForkEpoch)));
    return this;
  }

  public TekuNodeConfigBuilder withEip7594Epoch(final UInt64 eip7594ForkEpoch) {

    mustBe(NodeType.BEACON_NODE);
    LOG.debug("Xnetwork-das-fork-epoch={}", eip7594ForkEpoch);
    configMap.put("Xnetwork-das-fork-epoch", eip7594ForkEpoch.toString());
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.eip7594Builder(
                    eip7594Builder -> eip7594Builder.eip7594ForkEpoch(eip7594ForkEpoch)));
    return this;
  }

  public TekuNodeConfigBuilder withTotalTerminalDifficulty(final long totalTerminalDifficulty) {
    return withTotalTerminalDifficulty(UInt256.valueOf(totalTerminalDifficulty));
  }

  public TekuNodeConfigBuilder withTotalTerminalDifficulty(final UInt256 totalTerminalDifficulty) {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("Xnetwork-total-terminal-difficulty-override={}", totalTerminalDifficulty);
    configMap.put(
        "Xnetwork-total-terminal-difficulty-override",
        totalTerminalDifficulty.toBigInteger().toString());
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.bellatrixBuilder(
                    bellatrixBuilder ->
                        bellatrixBuilder.terminalTotalDifficulty(totalTerminalDifficulty)));
    return this;
  }

  public TekuNodeConfigBuilder withRealNetwork() {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("P2P enabled");
    configMap.put("p2p-enabled", true);
    return this;
  }

  public TekuNodeConfigBuilder genesisTime(final int genesisTime) {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("Genesis time: {}", genesisTime);
    configMap.put("Xinterop-genesis-time", genesisTime);
    return this;
  }

  public TekuNodeConfigBuilder withNetwork(final String networkName) {
    LOG.debug("Network={}", networkName);
    configMap.put("network", networkName);
    return this;
  }

  public TekuNodeConfigBuilder withNetwork(final URL networkYaml, final String networkName)
      throws Exception {
    LOG.debug("Network={}", networkName);
    LOG.debug("Copy Network from URL {}", networkYaml);
    configMap.put("network", NETWORK_FILE_PATH);
    configFileMap.put(copyToTmpFile(networkYaml), NETWORK_FILE_PATH);
    return this;
  }

  public TekuNodeConfigBuilder withInteropValidators(
      final int startIndex, final int validatorCount) {
    LOG.debug("Owned validator start index: {}, Validator count: {}", startIndex, validatorCount);
    configMap.put("Xinterop-owned-validator-start-index", startIndex);
    configMap.put("Xinterop-owned-validator-count", validatorCount);
    return this;
  }

  public TekuNodeConfigBuilder withExitWhenNoValidatorKeysEnabled(
      final boolean exitWhenNoValidatorKeysEnabled) {
    LOG.debug("exit-when-no-validator-keys-enabled: {}", exitWhenNoValidatorKeysEnabled);
    configMap.put("exit-when-no-validator-keys-enabled", exitWhenNoValidatorKeysEnabled);
    return this;
  }

  public TekuNodeConfigBuilder withBeaconNodes(final TekuBeaconNode... beaconNodes) {
    mustBe(NodeType.VALIDATOR);
    configMap.put(
        "beacon-node-api-endpoint",
        Arrays.stream(beaconNodes)
            .map(TekuBeaconNode::getBeaconRestApiUrl)
            .collect(Collectors.joining(",")));
    return this;
  }

  public static TekuNodeConfigBuilder createValidatorClient() {
    final Map<String, Object> configMap = new HashMap<>();
    configMap.put("Xinterop-owned-validator-start-index", 0);
    configMap.put("Xinterop-owned-validator-count", DEFAULT_VALIDATOR_COUNT);
    configMap.put("Xinterop-number-of-validators", DEFAULT_VALIDATOR_COUNT);
    configMap.put("Xinterop-enabled", true);
    configMap.put("data-path", DATA_PATH);
    configMap.put("log-destination", "console");
    configMap.put("beacon-node-api-endpoint", "http://notvalid.restapi.com");
    configMap.put("metrics-enabled", true);
    configMap.put("metrics-port", METRICS_PORT);
    configMap.put("metrics-interface", "0.0.0.0");
    configMap.put("metrics-host-allowlist", "*");
    return new TekuNodeConfigBuilder(NodeType.VALIDATOR, configMap);
  }

  public TekuNodeConfig build() {
    TekuNodeConfig tekuNodeConfig =
        new TekuNodeConfig(
            configMap,
            specConfigModifier,
            maybePrivKey,
            maybePeerId,
            writableMountPoints,
            readOnlyMountPoints,
            tarballsToCopy,
            configFileMap);
    return tekuNodeConfig;
  }

  public TekuNodeConfigBuilder withValidatorApiEnabled() {
    configMap.put("validator-api-enabled", true);
    configMap.put("validator-api-port", VALIDATOR_API_PORT);
    configMap.put("validator-api-host-allowlist", "*");
    configMap.put("validator-api-keystore-file", "/keystore.pfx");
    try {
      publishSelfSignedCertificate("/keystore.pfx");
    } catch (Exception e) {
      LOG.error("Could not generate self signed cert", e);
    }
    return this;
  }

  public TekuNodeConfigBuilder withValidatorApiNoSsl(final boolean ignoreSslInterface) {
    LOG.debug("Validator api enabled, no SSL, ignoreInterface={}", ignoreSslInterface);
    configMap.put("validator-api-enabled", true);
    configMap.put("Xvalidator-api-ssl-enabled", false);
    configMap.put("validator-api-port", VALIDATOR_API_PORT);
    configMap.put("validator-api-host-allowlist", "*");
    configMap.put("Xvalidator-api-unsafe-hosts-enabled", ignoreSslInterface);
    return this;
  }

  public TekuNodeConfigBuilder withSpecifiedBearerToken(final String password) throws IOException {
    final String bearerPath = "/bearer.txt";
    LOG.debug("Setting bearer password, and mapping to file {}}", bearerPath);
    configMap.put("validator-api-bearer-file", bearerPath);
    final File bearerFile = File.createTempFile("bearer", ".txt");
    Files.writeString(bearerFile.toPath(), password, UTF_8);
    bearerFile.deleteOnExit();
    configFileMap.put(bearerFile, bearerPath);
    return this;
  }

  public TekuNodeConfigBuilder withWritableKeystorePath(
      ValidatorKeystores keystores, Path tempDir) {
    LOG.debug("Xinterop-enabled=false");
    configMap.put("Xinterop-enabled", false);
    final String keysFolder = "/" + keystores.getKeysDirectoryName();
    final String passFolder = "/" + keystores.getPasswordsDirectoryName();
    keystores.writeToTempDir(tempDir);
    LOG.debug("set validator-keys folder to {}:{}", keysFolder, passFolder);
    configMap.put("validator-keys", keysFolder + ":" + passFolder);
    writableMountPoints.put(
        tempDir.resolve(keystores.getKeysDirectoryName()).toAbsolutePath().toString(), keysFolder);
    readOnlyMountPoints.put(
        tempDir.resolve(keystores.getPasswordsDirectoryName()).toAbsolutePath().toString(),
        passFolder);
    return this;
  }

  public TekuNodeConfigBuilder withReadOnlyKeystorePath(final ValidatorKeystores keystores) {
    LOG.debug("Xinterop-enabled=false");
    configMap.put("Xinterop-enabled", false);
    configMap.put("validators-keystore-locking-enabled", false);
    tarballsToCopy.add(keystores.getTarball());
    final String keysFolder = WORKING_DIRECTORY + keystores.getKeysDirectoryName();
    final String passFolder = WORKING_DIRECTORY + keystores.getPasswordsDirectoryName();
    LOG.debug("set validator-keys folder to {}:{}", keysFolder, passFolder);
    configMap.put("validator-keys", keysFolder + ":" + passFolder);
    return this;
  }

  // will mount keystores folder as a read only filesystem
  public TekuNodeConfigBuilder withReadOnlyKeystorePath(
      final ValidatorKeystores keystores, final Path tempDir) {
    LOG.debug("Xinterop-enabled=false");
    configMap.put("Xinterop-enabled", false);
    final String keysFolder = "/" + keystores.getKeysDirectoryName();
    final String passFolder = "/" + keystores.getPasswordsDirectoryName();
    keystores.writeToTempDir(tempDir);
    LOG.debug("set validator-keys folder to {}:{}", keysFolder, passFolder);
    configMap.put("validator-keys", keysFolder + ":" + passFolder);
    readOnlyMountPoints.put(
        tempDir.resolve(keystores.getKeysDirectoryName()).toAbsolutePath().toString(), keysFolder);
    readOnlyMountPoints.put(
        tempDir.resolve(keystores.getPasswordsDirectoryName()).toAbsolutePath().toString(),
        passFolder);
    return this;
  }

  public TekuNodeConfigBuilder withInitialState(
      final GenesisGenerator.InitialStateData initialState) throws Exception {
    mustBe(NodeType.BEACON_NODE);
    configMap.put("initial-state", INITIAL_STATE_FILE);
    final File initialStateFile = copyToTmpFile(initialState.writeToTempFile().toURI().toURL());
    configFileMap.put(initialStateFile, INITIAL_STATE_FILE);
    return this;
  }

  public TekuNodeConfigBuilder withInteropModeDisabled() {
    LOG.debug("Xinterop-enabled=false");
    configMap.put("Xinterop-enabled", false);
    return this;
  }

  public TekuNodeConfigBuilder withDoppelgangerDetectionEnabled() {
    LOG.debug("doppelganger-detection-enabled=true");
    configMap.put("doppelganger-detection-enabled", true);
    return this;
  }

  public TekuNodeConfigBuilder withDepositsFrom(final BesuNode eth1Node) {
    mustBe(NodeType.BEACON_NODE);
    configMap.put("Xinterop-enabled", false);
    LOG.debug("eth1-deposit-contract-address={}}", eth1Node.getDepositContractAddress().toString());
    LOG.debug("eth1-endpoint={}", eth1Node.getInternalJsonRpcUrl());
    configMap.put("eth1-deposit-contract-address", eth1Node.getDepositContractAddress().toString());
    configMap.put("eth1-endpoint", eth1Node.getInternalJsonRpcUrl());
    return this;
  }

  public TekuNodeConfigBuilder withExternalMetricsClient(
      final ExternalMetricNode externalMetricsNode, final int intervalBetweenPublications) {
    LOG.debug(
        "metrics-publish-endpoint={}, interval={}",
        externalMetricsNode.getPublicationEndpointURL(),
        intervalBetweenPublications);
    configMap.put("metrics-publish-endpoint", externalMetricsNode.getPublicationEndpointURL());
    configMap.put("metrics-publish-interval", intervalBetweenPublications);
    return this;
  }

  public TekuNodeConfigBuilder withStopVcWhenValidatorSlashedEnabled() {
    LOG.debug("shut-down-when-validator-slashed-enabled={}", true);
    configMap.put("shut-down-when-validator-slashed-enabled", true);
    return this;
  }

  public TekuNodeConfigBuilder withValidatorLivenessTracking() {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("beacon-liveness-tracking-enabled={}", true);
    configMap.put("beacon-liveness-tracking-enabled", true);
    return this;
  }

  public TekuNodeConfigBuilder withSafeSlotsToImportOptimistically(final int slots) {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("Xnetwork-safe-slots-to-import-optimistically={}", slots);
    configMap.put("Xnetwork-safe-slots-to-import-optimistically", slots);
    return this;
  }

  public TekuNodeConfigBuilder withSentryNodes(final SentryNodesConfig sentryNodesConfig) {
    mustBe(NodeType.VALIDATOR);
    final File sentryNodesConfigFile;
    try {
      sentryNodesConfigFile = File.createTempFile("sentry-node-config", ".json");
      sentryNodesConfigFile.deleteOnExit();

      try (FileWriter fw = new FileWriter(sentryNodesConfigFile, StandardCharsets.UTF_8)) {
        fw.write(sentryNodesConfig.toJson(JSON_PROVIDER));
      }
    } catch (IOException e) {
      throw new RuntimeException("Error creating sentry nodes configuration file", e);
    }
    if (configMap.get("beacon-node-api-endpoints") != null) {
      LOG.debug("remove beacon-node-api-endpoints");
      configMap.remove("beacon-node-api-endpoint");
    }
    configFileMap.put(sentryNodesConfigFile, SENTRY_NODE_CONFIG_FILE_PATH);

    LOG.debug("Add sentry-config-file {}", SENTRY_NODE_CONFIG_FILE_PATH);
    configMap.put("sentry-config-file", SENTRY_NODE_CONFIG_FILE_PATH);

    return this;
  }

  public TekuNodeConfigBuilder withInteropNumberOfValidators(final int validatorCount) {
    LOG.debug("Xinterop-number-of-validators={}", validatorCount);
    configMap.put("Xinterop-number-of-validators", validatorCount);
    return this;
  }

  public TekuNodeConfigBuilder withValidatorKeystoreLockingEnabled(final boolean enabled) {
    LOG.debug("validators-keystore-locking-enabled={}", enabled);
    configMap.put("validators-keystore-locking-enabled", enabled);
    return this;
  }

  public TekuNodeConfigBuilder withPeers(final TekuBeaconNode... nodes) {
    mustBe(NodeType.BEACON_NODE);
    final String peers =
        Arrays.stream(nodes).map(TekuBeaconNode::getMultiAddr).collect(Collectors.joining(","));
    LOG.debug("p2p-static-peers={}", peers);
    configMap.put("p2p-static-peers", peers);
    return this;
  }

  public TekuNodeConfigBuilder withExternalSignerUrl(final String externalSignerUrl) {
    LOG.debug("validators-external-signer-url={}", externalSignerUrl);
    configMap.put("validators-external-signer-url", externalSignerUrl);
    return this;
  }

  public TekuNodeConfigBuilder withValidatorProposerDefaultFeeRecipient(
      final String validatorProposerDefaultFeeRecipient) {
    LOG.debug("validators-proposer-default-fee-recipient={}", validatorProposerDefaultFeeRecipient);
    configMap.put(
        "validators-proposer-default-fee-recipient", validatorProposerDefaultFeeRecipient);
    return this;
  }

  public TekuNodeConfigBuilder withStartupTargetPeerCount(final int startupTargetPeerCount) {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("Xstartup-target-peer-count={}", startupTargetPeerCount);
    configMap.put("Xstartup-target-peer-count", startupTargetPeerCount);
    return this;
  }

  public TekuNodeConfigBuilder withStubExecutionEngine(final String terminalBlockHash) {
    mustBe(NodeType.BEACON_NODE);
    final String endpoint = "unsafe-test-stub:" + terminalBlockHash;

    LOG.debug("ee-endpoint={}", endpoint);
    configMap.put("ee-endpoint", endpoint);
    return this;
  }

  public TekuNodeConfigBuilder withStubExecutionEngine() {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("ee-endpoint={}", "unsafe-test-stub");
    configMap.put("ee-endpoint", "unsafe-test-stub");
    return this;
  }

  public TekuNodeConfigBuilder withGenesisTime(int time) {
    mustBe(NodeType.BEACON_NODE);
    LOG.debug("Xinterop-genesis-time={}", time);
    configMap.put("Xinterop-genesis-time", time);
    return this;
  }

  public TekuNodeConfigBuilder withGraffiti(final String graffiti) {
    LOG.debug("validators-graffiti: {}", graffiti);
    configMap.put("validators-graffiti", graffiti);
    return this;
  }

  private TekuNodeConfigBuilder withPrivateKey(PrivKey privKey) throws IOException {
    mustBe(NodeType.BEACON_NODE);
    this.maybePrivKey = Optional.ofNullable(privKey);
    this.maybePeerId = maybePrivKey.map(privateKey -> PeerId.fromPubKey(privateKey.publicKey()));
    final File privateKeyFile = File.createTempFile("private-key", ".txt");
    LOG.debug("with-private-key peerId={}", maybePeerId.get().toBase58());
    Files.writeString(privateKeyFile.toPath(), Bytes.wrap(privKey.bytes()).toHexString(), UTF_8);
    privateKeyFile.deleteOnExit();
    configFileMap.put(privateKeyFile, PRIVATE_KEY_FILE_PATH);
    return this;
  }

  private void mustBe(final NodeType expected) {
    if (!nodeType.equals(expected)) {
      LOG.error("Function not supported for node type {}", nodeType);
      throw new UnsupportedOperationException();
    }
  }

  // validator client api
  private void publishSelfSignedCertificate(final String targetKeystoreFile)
      throws URISyntaxException, IOException {
    if (!keyfilesGenerated) {
      keyfilesGenerated = true;

      final File keystoreFile = File.createTempFile("keystore", ".pfx");
      try (OutputStream out = new FileOutputStream(keystoreFile)) {
        // validatorApi.pfx has a long expiry, and no password
        Resources.copy(
            Resources.getResource(TekuValidatorNode.class, "validatorApi.pfx").toURI().toURL(),
            out);
      }

      configFileMap.put(keystoreFile, targetKeystoreFile);
    }
  }

  private TekuNodeConfigBuilder(
      final NodeType nodeType, final Map<String, Object> configMapDefaults) {
    LOG.debug("Node type: {}", nodeType);
    this.configMap = configMapDefaults;
    this.nodeType = nodeType;
  }

  private enum NodeType {
    VALIDATOR,
    BEACON_NODE
  }
}
