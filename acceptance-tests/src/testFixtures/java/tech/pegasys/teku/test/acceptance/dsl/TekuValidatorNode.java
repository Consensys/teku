/*
 * Copyright ConsenSys Software Inc., 2022
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
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withLabelsContaining;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withNameEqualsTo;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withValueGreaterThan;

import com.google.common.io.Resources;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;
import org.testcontainers.utility.MountableFile;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class TekuValidatorNode extends Node {

  private static final Logger LOG = LogManager.getLogger();
  private static final int VALIDATOR_API_PORT = 9052;
  protected static final String VALIDATOR_PATH = DATA_PATH + "validator/";

  private final TekuValidatorNode.Config config;
  private boolean started = false;
  private Set<File> configFiles;
  private final ValidatorKeysApi validatorKeysApi =
      new ValidatorKeysApi(
          new TrustingSimpleHttpsClient(), this::getValidatorApiUrl, this::getApiPassword);

  private TekuValidatorNode(
      final Network network, final DockerVersion version, final TekuValidatorNode.Config config) {
    super(network, TEKU_DOCKER_IMAGE_NAME, version, LOG);
    this.config = config;
    if (config.configMap.containsKey("validator-api-enabled")) {
      container.addExposedPort(VALIDATOR_API_PORT);
    }
    container.addExposedPort(METRICS_PORT);

    container
        .withWorkingDirectory(WORKING_DIRECTORY)
        .withCommand("validator-client", "--config-file", CONFIG_FILE_PATH);
  }

  public static TekuValidatorNode create(
      final Network network,
      final DockerVersion version,
      Consumer<TekuValidatorNode.Config> configOptions) {

    final TekuValidatorNode.Config config = new TekuValidatorNode.Config();
    configOptions.accept(config);

    return new TekuValidatorNode(network, version, config);
  }

  public TekuValidatorNode withValidatorApiEnabled() {
    this.config.withValidatorApiEnabled();
    return this;
  }

  public ValidatorKeysApi getValidatorKeysApi() {
    if (!config.configMap.containsKey("validator-api-enabled")) {
      LOG.error("Retrieving validator keys api but api is not enabled");
    }
    return validatorKeysApi;
  }

  public TekuValidatorNode withValidatorKeystores(ValidatorKeystores validatorKeystores) {
    this.config.withValidatorKeys(
        WORKING_DIRECTORY
            + validatorKeystores.getKeysDirectoryName()
            + ":"
            + WORKING_DIRECTORY
            + validatorKeystores.getPasswordsDirectoryName());
    this.copyContentsToWorkingDirectory(validatorKeystores.getTarball());
    return this;
  }

  public void start() throws Exception {
    assertThat(started).isFalse();
    LOG.debug("Start validator node {}", nodeAlias);
    started = true;
    config.writeConfigFile();
    final Map<File, String> configFileMap = config.getConfigFileMap();
    this.configFiles = configFileMap.keySet();
    configFileMap.forEach(
        (localFile, targetPath) ->
            container.withCopyFileToContainer(
                MountableFile.forHostPath(localFile.getAbsolutePath()), targetPath));
    container.start();
  }

  @Override
  public void stop() {
    if (!started) {
      return;
    }
    LOG.debug("Shutting down");
    started = false;
    configFiles.forEach(
        configFile -> {
          if (!configFile.delete() && configFile.exists()) {
            throw new RuntimeException("Failed to delete config file: " + configFile);
          }
        });
    container.stop();
  }

  private URI getValidatorApiUrl() {
    return URI.create("https://127.0.0.1:" + container.getMappedPort(VALIDATOR_API_PORT));
  }

  public String getApiPassword() {
    return container.copyFileFromContainer(
        VALIDATOR_PATH + "key-manager/validator-api-bearer",
        in -> IOUtils.toString(in, StandardCharsets.UTF_8));
  }

  public void waitForDutiesRequestedFrom(final TekuNode node) {
    waitForMetric(
        withNameEqualsTo("validator_remote_beacon_nodes_requests_total"),
        withLabelsContaining(
            Map.of(
                "endpoint", node.getBeaconRestApiUrl() + "/",
                "method", "get_proposer_duties",
                "outcome", "success")),
        withValueGreaterThan(0));
  }

  public void waitForAttestationPublishedTo(final TekuNode node) {
    waitForMetric(
        withNameEqualsTo("validator_remote_beacon_nodes_requests_total"),
        withLabelsContaining(
            Map.of(
                "endpoint", node.getBeaconRestApiUrl() + "/",
                "method", "publish_attestation",
                "outcome", "success")),
        withValueGreaterThan(0));
  }

  public void waitForBlockPublishedTo(final TekuNode node) {
    waitForMetric(
        withNameEqualsTo("validator_remote_beacon_nodes_requests_total"),
        withLabelsContaining(
            Map.of(
                "endpoint", node.getBeaconRestApiUrl() + "/",
                "method", "publish_block",
                "outcome", "success")),
        withValueGreaterThan(0));
  }

  public static class Config {

    private static final int DEFAULT_VALIDATOR_COUNT = 64;

    private Map<String, Object> configMap = new HashMap<>();
    private boolean keyfilesGenerated = false;
    private final Map<File, String> configFileMap = new HashMap<>();
    private Optional<InputStream> maybeNetworkYaml = Optional.empty();

    private boolean isUsingSentryNodeConfig = false;

    public Config() {
      configMap.put("validators-keystore-locking-enabled", false);
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
    }

    public TekuValidatorNode.Config withInteropModeDisabled() {
      configMap.put("Xinterop-enabled", false);
      return this;
    }

    public TekuValidatorNode.Config withValidatorKeys(final String validatorKeyInformation) {
      configMap.put("validator-keys", validatorKeyInformation);
      return this;
    }

    public TekuValidatorNode.Config withProposerDefaultFeeRecipient(final String feeRecipient) {
      configMap.put("validators-proposer-default-fee-recipient", feeRecipient);
      return this;
    }

    public TekuValidatorNode.Config withValidatorApiEnabled() {
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

    public TekuValidatorNode.Config withExternalSignerUrl(final String externalSignerUrl) {
      configMap.put("validators-external-signer-url", externalSignerUrl);
      return this;
    }

    public TekuValidatorNode.Config withBeaconNode(final TekuNode beaconNode) {
      return withBeaconNodes(beaconNode);
    }

    public TekuValidatorNode.Config withBeaconNodes(final TekuNode... beaconNodes) {
      configMap.put(
          "beacon-node-api-endpoint",
          Arrays.stream(beaconNodes)
              .map(TekuNode::getBeaconRestApiUrl)
              .collect(Collectors.joining(",")));
      return this;
    }

    public TekuValidatorNode.Config withNetwork(String networkName) {
      configMap.put("network", networkName);
      return this;
    }

    public TekuValidatorNode.Config withInteropValidators(
        final int startIndex, final int validatorCount) {
      configMap.put("Xinterop-owned-validator-start-index", startIndex);
      configMap.put("Xinterop-owned-validator-count", validatorCount);
      return this;
    }

    public TekuValidatorNode.Config withSentryNodes(final SentryNodesConfig sentryNodesConfig) {
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
      configFileMap.put(sentryNodesConfigFile, SENTRY_NODE_CONFIG_FILE_PATH);

      configMap.put("Xsentry-config-file", SENTRY_NODE_CONFIG_FILE_PATH);
      isUsingSentryNodeConfig = true;

      return this;
    }

    public void writeConfigFile() throws Exception {
      final File configFile = File.createTempFile("config", ".yaml");
      configFile.deleteOnExit();

      if (isUsingSentryNodeConfig) {
        configMap.remove("beacon-node-api-endpoint");
      }

      writeConfigFileTo(configFile);
      configFileMap.put(configFile, CONFIG_FILE_PATH);
      if (maybeNetworkYaml.isPresent()) {
        final File networkFile = File.createTempFile("network", ".yaml");
        networkFile.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(networkFile)) {
          IOUtils.copy(maybeNetworkYaml.get(), out);
        } finally {
          if (maybeNetworkYaml.isPresent()) {
            maybeNetworkYaml.get().close();
          }
        }
        configFileMap.put(networkFile, NETWORK_FILE_PATH);
      }
    }

    public TekuValidatorNode.Config withAltairEpoch(final UInt64 altairSlot) {
      configMap.put("Xnetwork-altair-fork-epoch", altairSlot.toString());
      return this;
    }

    private void writeConfigFileTo(final File configFile) throws Exception {
      YAML_MAPPER.writeValue(configFile, configMap);
    }

    public void publishSelfSignedCertificate(final String targetKeystoreFile)
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

    public Map<File, String> getConfigFileMap() {
      return configFileMap;
    }
  }
}
