/*
 * Copyright Consensys Software Inc., 2022
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

import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withLabelsContaining;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withNameEqualsTo;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withValueGreaterThan;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;

public class TekuValidatorNode extends TekuNode {

  private static final Logger LOG = LogManager.getLogger();
  public static final int VALIDATOR_API_PORT = 9052;
  protected static final String VALIDATOR_PATH = DATA_PATH + "validator/";

  private final TekuValidatorNode.Config config;
  private final ValidatorKeysApi validatorKeysApi =
      new ValidatorKeysApi(
          new TrustingSimpleHttpsClient(), this::getValidatorApiUrl, this::getApiPassword);

  private TekuValidatorNode(
      final Network network,
      final TekuDockerVersion version,
      final TekuValidatorNode.Config config) {
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
      final TekuDockerVersion version,
      Consumer<TekuNodeConfig> configOptions) {

    final TekuValidatorNode.Config config = new TekuValidatorNode.Config();
    configOptions.accept(config);

    return new TekuValidatorNode(network, version, config);
  }

  public ValidatorKeysApi getValidatorKeysApi() {
    if (!config.configMap.containsKey("validator-api-enabled")) {
      LOG.error("Retrieving validator keys api but api is not enabled");
    }
    return validatorKeysApi;
  }

  @Override
  public TekuNodeConfig getConfig() {
    return config;
  }

  private URI getValidatorApiUrl() {
    return URI.create("https://127.0.0.1:" + container.getMappedPort(VALIDATOR_API_PORT));
  }

  public String getApiPassword() {
    return container.copyFileFromContainer(
        VALIDATOR_PATH + "key-manager/validator-api-bearer",
        in -> IOUtils.toString(in, StandardCharsets.UTF_8));
  }

  public void waitForDutiesRequestedFrom(final TekuBeaconNode node) {
    waitForMetric(
        withNameEqualsTo("validator_remote_beacon_nodes_requests_total"),
        withLabelsContaining(
            Map.of(
                "endpoint", node.getBeaconRestApiUrl() + "/",
                "method", "get_proposer_duties",
                "outcome", "success")),
        withValueGreaterThan(0));
  }

  public void waitForAttestationPublishedTo(final TekuBeaconNode node) {
    waitForMetric(
        withNameEqualsTo("validator_remote_beacon_nodes_requests_total"),
        withLabelsContaining(
            Map.of(
                "endpoint", node.getBeaconRestApiUrl() + "/",
                "method", "publish_attestation",
                "outcome", "success")),
        withValueGreaterThan(0));
  }

  public void waitForBlockPublishedTo(final TekuBeaconNode node) {
    waitForMetric(
        withNameEqualsTo("validator_remote_beacon_nodes_requests_total"),
        withLabelsContaining(
            Map.of(
                "endpoint", node.getBeaconRestApiUrl() + "/",
                "method", "publish_block",
                "outcome", "success")),
        withValueGreaterThan(0));
  }

  public static class Config extends TekuNodeConfig {

    public Config() {
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
      this.nodeType = "Validator";
    }
  }
}
