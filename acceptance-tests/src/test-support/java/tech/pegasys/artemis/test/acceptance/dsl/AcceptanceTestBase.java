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
import tech.pegasys.artemis.test.acceptance.dsl.AcceptanceTestBase.CaptureArtifacts;
import tech.pegasys.artemis.test.acceptance.dsl.tools.GenesisStateGenerator;

@ExtendWith(CaptureArtifacts.class)
public class AcceptanceTestBase {

  private final SimpleHttpClient httpClient = new SimpleHttpClient();
  private final List<Node> nodes = new ArrayList<>();
  private final Network network = Network.newNetwork();
  private final GenesisStateGenerator genesisStateGenerator = new GenesisStateGenerator();

  @AfterEach
  final void shutdownNodes() {
    nodes.forEach(Node::stop);
    network.close();
  }

  protected ArtemisNode createArtemisNode() {
    return createArtemisNode(config -> {});
  }

  protected ArtemisNode createArtemisNode(final Consumer<ArtemisNode.Config> configOptions) {
    try {
      return addNode(ArtemisNode.create(httpClient, network, configOptions, genesisStateGenerator));
    } catch (IOException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  protected ArtemisDepositSender createArtemisDepositSender() {
    return addNode(new ArtemisDepositSender(network));
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
    private static final String TEST_ARTIFACT_DIR_PROPERTY = "artemis.testArtifactDir";

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
