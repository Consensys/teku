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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.Network;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase.CaptureArtifacts;

@ExtendWith(CaptureArtifacts.class)
public class AcceptanceTestBase {
  static {
    // Disable libsodium in tuweni Hash because the check for its presence can be very slow.
    System.setProperty("org.apache.tuweni.crypto.useSodium", "false");
  }

  private final List<Node> nodes = new ArrayList<>();
  private final Network network = Network.newNetwork();

  @AfterEach
  final void shutdownNodes() {
    nodes.forEach(Node::stop);
    network.close();
  }

  protected TekuNode createTekuNode() {
    return createTekuNode(config -> {});
  }

  protected TekuNode createTekuNode(final Consumer<TekuNode.Config> configOptions) {
    return createTekuNode(TekuDockerVersion.LOCAL_BUILD, configOptions);
  }

  protected TekuNode createTekuNode(
      final TekuDockerVersion version, final Consumer<TekuNode.Config> configOptions) {
    try {
      return addNode(TekuNode.create(network, version, configOptions));
    } catch (IOException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  protected ExternalMetricNode createExternalMetricNode() {
    return addNode(ExternalMetricNode.create(network));
  }

  protected TekuVoluntaryExit createVoluntaryExit(
      final Consumer<TekuVoluntaryExit.Config> configOptions) {
    return addNode(TekuVoluntaryExit.create(network, configOptions));
  }

  protected TekuValidatorNode createValidatorNode(
      final Consumer<TekuValidatorNode.Config> configOptions) {
    return createValidatorNode(TekuDockerVersion.LOCAL_BUILD, configOptions);
  }

  protected TekuValidatorNode createValidatorNode(
      final TekuDockerVersion version, final Consumer<TekuValidatorNode.Config> configOptions) {
    return addNode(TekuValidatorNode.create(network, version, configOptions));
  }

  protected Web3SignerNode createWeb3SignerNode(
      final Consumer<Web3SignerNode.Config> configOptions) {
    return Web3SignerNode.create(network, configOptions);
  }

  protected TekuDepositSender createTekuDepositSender(final String networkName) {
    final Spec spec = SpecFactory.create(networkName);
    return addNode(new TekuDepositSender(network, spec));
  }

  protected BesuNode createBesuNode() {
    return createBesuNode(config -> {});
  }

  protected BesuNode createBesuNode(final Consumer<BesuNode.Config> configOptions) {
    return createBesuNode(BesuDockerVersion.STABLE, configOptions);
  }

  protected BesuNode createBesuNode(
      final BesuDockerVersion version, final Consumer<BesuNode.Config> configOptions) {
    return addNode(BesuNode.create(network, version, configOptions, Collections.emptyMap()));
  }

  protected BesuNode createBesuNode(
      final BesuDockerVersion version,
      final Consumer<BesuNode.Config> configOptions,
      final Map<String, String> genesisOverrides) {
    return addNode(BesuNode.create(network, version, configOptions, genesisOverrides));
  }

  protected GenesisGenerator createGenesisGenerator() {
    return new GenesisGenerator();
  }

  private <T extends Node> T addNode(final T node) {
    nodes.add(node);
    return node;
  }

  static class CaptureArtifacts implements AfterTestExecutionCallback {
    private static final Logger LOG = LogManager.getLogger();
    private static final String TEST_ARTIFACT_DIR_PROPERTY = "teku.testArtifactDir";

    @Override
    public void afterTestExecution(final ExtensionContext context) {
      if (context.getExecutionException().isPresent()) {
        context
            .getTestInstance()
            .filter(test -> test instanceof AcceptanceTestBase)
            .map(test -> (AcceptanceTestBase) test)
            .filter(test -> !test.nodes.isEmpty())
            .ifPresent(
                test -> {
                  final String artifactDir =
                      System.getProperty(TEST_ARTIFACT_DIR_PROPERTY, "build/test-artifacts");
                  final String dirName =
                      context.getRequiredTestClass().getName()
                          + "."
                          + context.getRequiredTestMethod().getName();
                  final File captureDir = new File(artifactDir, dirName);
                  if (!captureDir.mkdirs() && !captureDir.isDirectory()) {
                    LOG.error("Failed to create test artifact directory to capture transitions");
                  }
                  LOG.info("Capturing debug artifacts to " + captureDir.getAbsolutePath());
                  test.nodes.forEach(node -> node.captureDebugArtifacts(captureDir));
                });
      }
    }
  }
}
