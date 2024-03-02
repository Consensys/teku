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
import static tech.pegasys.teku.test.acceptance.dsl.Node.CONFIG_FILE_PATH;
import static tech.pegasys.teku.test.acceptance.dsl.Node.JSON_PROVIDER;
import static tech.pegasys.teku.test.acceptance.dsl.Node.JWT_SECRET_FILE_PATH;
import static tech.pegasys.teku.test.acceptance.dsl.Node.NETWORK_FILE_PATH;
import static tech.pegasys.teku.test.acceptance.dsl.Node.PRIVATE_KEY_FILE_PATH;
import static tech.pegasys.teku.test.acceptance.dsl.Node.SENTRY_NODE_CONFIG_FILE_PATH;
import static tech.pegasys.teku.test.acceptance.dsl.Node.WORKING_DIRECTORY;
import static tech.pegasys.teku.test.acceptance.dsl.Node.YAML_MAPPER;
import static tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode.Config.DEFAULT_NETWORK_NAME;
import static tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode.VALIDATOR_API_PORT;

import com.google.common.io.Resources;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
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
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;
import org.testcontainers.shaded.org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public abstract class TekuNodeConfig {
  private static final Logger LOG = LogManager.getLogger();

  protected String nodeType = "TekuNode";

  protected static final String INITIAL_STATE_FILE = "/state.ssz";

  protected static final String TRUSTED_SETUP_FILE = "/trusted-setup.txt";

  protected static final int DEFAULT_VALIDATOR_COUNT = 64;
  protected final Map<String, Object> configMap = new HashMap<>();
  protected final Map<File, String> configFileMap = new HashMap<>();
  protected Consumer<SpecConfigBuilder> specConfigModifier = builder -> {};
  protected Optional<URL> maybeNetworkYaml = Optional.empty();

  protected Optional<GenesisGenerator.InitialStateData> maybeInitialState = Optional.empty();
  protected Optional<URL> maybeJwtFile = Optional.empty();
  protected Optional<URL> maybeTrustedSetup = Optional.empty();
  protected Optional<PrivKey> maybePrivateKey = Optional.empty();
  protected Optional<PeerId> maybePeerId = Optional.empty();
  private final List<File> tarballsToCopy = new ArrayList<>();
  private final Map<String, String> readOnlyMountPoints = new HashMap<>();
  private final Map<String, String> writableMountPoints = new HashMap<>();
  private String networkName = DEFAULT_NETWORK_NAME;
  private boolean keyfilesGenerated = false;

  // will copy keystores onto the filesystem, so not read-only, except permissions are root
  // access.
  public TekuNodeConfig withReadOnlyKeystorePath(final ValidatorKeystores keystores) {
    configMap.put("Xinterop-enabled", false);
    configMap.put("validators-keystore-locking-enabled", false);
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

  // will mount keystores folder as a read only filesystem
  public TekuNodeConfig withReadOnlyKeystorePath(
      final ValidatorKeystores keystores, final Path tempDir) {
    configMap.put("Xinterop-enabled", false);
    final String keysFolder = "/home/validatorInfo/" + keystores.getKeysDirectoryName();
    final String passFolder = "/home/validatorInfo/" + keystores.getPasswordsDirectoryName();
    keystores.writeToTempDir(tempDir);
    configMap.put("validator-keys", keysFolder + ":" + passFolder);
    readOnlyMountPoints.put(
        tempDir.resolve(keystores.getKeysDirectoryName()).toAbsolutePath().toString(), keysFolder);
    readOnlyMountPoints.put(
        tempDir.resolve(keystores.getPasswordsDirectoryName()).toAbsolutePath().toString(),
        passFolder);
    return this;
  }

  // will mount keystores folder as a writable filesystem and be able to create locks
  public TekuNodeConfig withWritableKeystorePath(
      final ValidatorKeystores keystores, final Path tempDir) {
    configMap.put("Xinterop-enabled", false);
    final String keysFolder = "/home/validatorInfo/" + keystores.getKeysDirectoryName();
    final String passFolder = "/home/validatorInfo/" + keystores.getPasswordsDirectoryName();
    keystores.writeToTempDir(tempDir);
    configMap.put("validator-keys", keysFolder + ":" + passFolder);
    writableMountPoints.put(
        tempDir.resolve(keystores.getKeysDirectoryName()).toAbsolutePath().toString(), keysFolder);
    readOnlyMountPoints.put(
        tempDir.resolve(keystores.getPasswordsDirectoryName()).toAbsolutePath().toString(),
        passFolder);
    return this;
  }

  public TekuNodeConfig withNetwork(final String networkName) {
    this.networkName = networkName;
    configMap.put("network", networkName);
    return this;
  }

  public TekuNodeConfig withDoppelgangerDetectionEnabled() {
    configMap.put("doppelganger-detection-enabled", true);
    return this;
  }

  public TekuNodeConfig withValidatorKeystoreLockingEnabled(final boolean enabled) {
    configMap.put("validators-keystore-locking-enabled", enabled);
    return this;
  }

  public TekuNodeConfig withStopVcWhenValidatorSlashedEnabled() {
    configMap.put("Xshut-down-when-validator-slashed-enabled", true);
    return this;
  }

  public TekuNodeConfig withExternalMetricsClient(
      final ExternalMetricNode externalMetricsNode, final int intervalBetweenPublications) {
    configMap.put("metrics-publish-endpoint", externalMetricsNode.getPublicationEndpointURL());
    configMap.put("metrics-publish-interval", intervalBetweenPublications);
    return this;
  }

  public TekuNodeConfig withLogging(final String logging) {
    configMap.put("logging", logging);
    return this;
  }

  public TekuNodeConfig withExitWhenNoValidatorKeysEnabled(
      final boolean exitWhenNoValidatorKeysEnabled) {
    configMap.put("exit-when-no-validator-keys-enabled", exitWhenNoValidatorKeysEnabled);
    return this;
  }

  public TekuNodeConfig withInteropValidators(final int startIndex, final int validatorCount) {
    configMap.put("Xinterop-owned-validator-start-index", startIndex);
    configMap.put("Xinterop-owned-validator-count", validatorCount);
    return this;
  }

  public TekuNodeConfig withInteropNumberOfValidators(final int validatorCount) {
    configMap.put("Xinterop-number-of-validators", validatorCount);
    return this;
  }

  public TekuNodeConfig withAltairEpoch(final UInt64 altairForkEpoch) {
    configMap.put("Xnetwork-altair-fork-epoch", altairForkEpoch.toString());
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.altairBuilder(
                    altairBuilder -> altairBuilder.altairForkEpoch(altairForkEpoch)));
    return this;
  }

  public TekuNodeConfig withBellatrixEpoch(final UInt64 bellatrixForkEpoch) {
    configMap.put("Xnetwork-bellatrix-fork-epoch", bellatrixForkEpoch.toString());
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.bellatrixBuilder(
                    bellatrixBuilder -> bellatrixBuilder.bellatrixForkEpoch(bellatrixForkEpoch)));
    return this;
  }

  public TekuNodeConfig withCapellaEpoch(final UInt64 capellaForkEpoch) {
    configMap.put("Xnetwork-capella-fork-epoch", capellaForkEpoch.toString());
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.capellaBuilder(
                    capellaBuilder -> capellaBuilder.capellaForkEpoch(capellaForkEpoch)));
    return this;
  }

  public TekuNodeConfig withDenebEpoch(final UInt64 denebForkEpoch) {
    configMap.put("Xnetwork-deneb-fork-epoch", denebForkEpoch.toString());
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.denebBuilder(
                    denebBuilder -> denebBuilder.denebForkEpoch(denebForkEpoch)));
    return this;
  }

  public Consumer<SpecConfigBuilder> getSpecConfigModifier() {
    return specConfigModifier;
  }

  // VC specific
  public TekuNodeConfig withSentryNodes(final SentryNodesConfig sentryNodesConfig) {
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
      configMap.remove("beacon-node-api-endpoint");
    }
    configFileMap.put(sentryNodesConfigFile, SENTRY_NODE_CONFIG_FILE_PATH);

    configMap.put("sentry-config-file", SENTRY_NODE_CONFIG_FILE_PATH);

    return this;
  }

  // VC specific
  public TekuNodeConfig withBeaconNodes(final TekuBeaconNode... beaconNodes) {
    configMap.put(
        "beacon-node-api-endpoint",
        Arrays.stream(beaconNodes)
            .map(TekuBeaconNode::getBeaconRestApiUrl)
            .collect(Collectors.joining(",")));
    return this;
  }

  // still not perfect if we have to declare all these
  public TekuNodeConfig withPeers(final TekuBeaconNode... nodes) {
    throw new NotImplementedException();
  }

  public TekuNodeConfig withTotalTerminalDifficulty(final long totalTerminalDifficulty) {
    throw new NotImplementedException();
  }

  public TekuNodeConfig withGenesisTime(int time) {
    throw new NotImplementedException();
  }

  public TekuNodeConfig withInitialState(final GenesisGenerator.InitialStateData initialState) {
    throw new NotImplementedException();
  }

  public TekuNodeConfig withTrustedSetupFromClasspath(final String trustedSetup) {
    throw new NotImplementedException();
  }

  public TekuNodeConfig withDepositsFrom(final BesuNode eth1Node) {
    throw new NotImplementedException();
  }

  public TekuNodeConfig withValidatorProposerDefaultFeeRecipient(
      final String validatorProposerDefaultFeeRecipient) {
    configMap.put(
        "validators-proposer-default-fee-recipient", validatorProposerDefaultFeeRecipient);
    return this;
  }

  public TekuNodeConfig withExternalSignerUrl(final String externalSignerUrl) {
    configMap.put("validators-external-signer-url", externalSignerUrl);
    return this;
  }

  // BN specific
  public TekuNodeConfig withExecutionEngine(final BesuNode node) {
    configMap.put("ee-endpoint", node.getInternalEngineJsonRpcUrl());
    return this;
  }

  public TekuNodeConfig withJwtSecretFile(final URL jwtFile) {
    throw new NotImplementedException();
  }

  public TekuNodeConfig withSafeSlotsToImportOptimistically(final int slots) {
    configMap.put("Xnetwork-safe-slots-to-import-optimistically", slots);
    return this;
  }

  public TekuNodeConfig withStartupTargetPeerCount(final int startupTargetPeerCount) {
    configMap.put("Xstartup-target-peer-count", startupTargetPeerCount);
    return this;
  }

  // BN specific
  public TekuNodeConfig withRealNetwork() {
    configMap.put("p2p-enabled", true);
    return this;
  }

  // BN specific
  public TekuNodeConfig withStubExecutionEngine(final String terminalBlockHash) {
    configMap.put("ee-endpoint", "unsafe-test-stub:" + terminalBlockHash);
    return this;
  }

  // BN specific
  public TekuNodeConfig withStubExecutionEngine() {
    configMap.put("ee-endpoint", "unsafe-test-stub");
    return this;
  }

  public TekuNodeConfig withValidatorApiEnabled() {
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

  public TekuNodeConfig withInteropModeDisabled() {
    configMap.put("Xinterop-enabled", false);
    return this;
  }

  public void writeConfigFiles() throws Exception {
    if (configMap.get("sentry-config-file") != null
        && configMap.get("beacon-node-api-endpoint") != null) {
      LOG.error("Using Sentry node, removing beacon-node-api");
      configMap.remove("beacon-node-api-endpoint");
    }

    writeConfigMapToFile();
    writePrivateKeyIfRequired();
    writeInitialStateIfRequired();

    if (maybeNetworkYaml.isPresent()) {
      configFileMap.put(copyToTmpFile(maybeNetworkYaml.get()), NETWORK_FILE_PATH);
    }
    if (maybeJwtFile.isPresent()) {
      configFileMap.put(copyToTmpFile(maybeJwtFile.get()), JWT_SECRET_FILE_PATH);
    }
    if (maybeTrustedSetup.isPresent()) {
      configFileMap.put(copyToTmpFile(maybeTrustedSetup.get()), TRUSTED_SETUP_FILE);
    }
    if (maybeNetworkYaml.isPresent()) {
      configFileMap.put(copyToTmpFile(maybeNetworkYaml.get()), NETWORK_FILE_PATH);
    }
  }

  public String getNetworkName() {
    return networkName;
  }

  public List<File> getTarballsToCopy() {
    return tarballsToCopy;
  }

  void writeConfigFileTo(final File configFile) throws Exception {
    YAML_MAPPER.writeValue(configFile, configMap);
  }

  Map<String, String> getWritableMountPoints() {
    return writableMountPoints;
  }

  Map<String, String> getReadOnlyMountPoints() {
    return readOnlyMountPoints;
  }

  Map<File, String> getConfigFileMap() {
    return configFileMap;
  }

  public String getPeerId() {
    return maybePeerId.map(PeerId::toBase58).orElse("");
  }

  public String getNodeType() {
    return this.nodeType;
  }

  private static File copyToTmpFile(final URL fileUrl) throws Exception {
    final File tmpFile = File.createTempFile("teku", ".tmp");
    tmpFile.deleteOnExit();
    try (InputStream inputStream = fileUrl.openStream();
        FileOutputStream out = new FileOutputStream(tmpFile)) {
      IOUtils.copy(inputStream, out);
    } catch (Exception ex) {
      LOG.error("Failed to copy provided URL to temporary file", ex);
    }
    return tmpFile;
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

  private void writeConfigMapToFile() throws Exception {
    final File configFile = File.createTempFile("config", ".yaml");
    configFile.deleteOnExit();
    writeConfigFileTo(configFile);
    configFileMap.put(configFile, CONFIG_FILE_PATH);
  }

  private void writeInitialStateIfRequired() throws Exception {
    if (maybeInitialState.isPresent()) {
      final GenesisGenerator.InitialStateData initialStateData = maybeInitialState.get();
      final File initialStateFile =
          copyToTmpFile(initialStateData.writeToTempFile().toURI().toURL());
      configFileMap.put(initialStateFile, INITIAL_STATE_FILE);
    }
  }

  private void writePrivateKeyIfRequired() throws IOException {
    if (maybePrivateKey.isPresent()) {
      final PrivKey privateKey = maybePrivateKey.get();
      final File privateKeyFile = File.createTempFile("private-key", ".txt");
      privateKeyFile.deleteOnExit();
      Files.writeString(
          privateKeyFile.toPath(), Bytes.wrap(privateKey.bytes()).toHexString(), UTF_8);
      configFileMap.put(privateKeyFile, PRIVATE_KEY_FILE_PATH);
    }
  }
}
