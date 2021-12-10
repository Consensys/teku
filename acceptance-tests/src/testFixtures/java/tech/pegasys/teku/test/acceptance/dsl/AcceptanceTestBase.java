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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import tech.pegasys.teku.test.acceptance.dsl.tools.GenesisStateGenerator;

@ExtendWith(CaptureArtifacts.class)
public class AcceptanceTestBase {
  private final List<Node> nodes = new ArrayList<>();
  private final Network network = Network.newNetwork();
  private final GenesisStateGenerator genesisStateGenerator = new GenesisStateGenerator();

  @AfterEach
  final void shutdownNodes() {
    nodes.forEach(Node::stop);
    network.close();
  }

  protected TekuNode createTekuNode() {
    return createTekuNode(config -> {});
  }

  protected TekuNode createTekuNode(final Consumer<TekuNode.Config> configOptions) {
    return createTekuNode(DockerVersion.LOCAL_BUILD, configOptions);
  }

  protected TekuNode createTekuNode(
      final DockerVersion version, final Consumer<TekuNode.Config> configOptions) {
    try {
      return addNode(TekuNode.create(network, version, configOptions, genesisStateGenerator));
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
    return createValidatorNode(DockerVersion.LOCAL_BUILD, configOptions);
  }

  protected TekuValidatorNode createValidatorNode(
      final DockerVersion version, final Consumer<TekuValidatorNode.Config> configOptions) {
    return addNode(TekuValidatorNode.create(network, version, configOptions));
  }

  protected TekuDepositSender createTekuDepositSender(final String networkName) {
    final Spec spec = SpecFactory.create(networkName);
    return addNode(new TekuDepositSender(network, spec));
  }

  protected BesuNode createBesuNode() {
    return addNode(new BesuNode(network));
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
