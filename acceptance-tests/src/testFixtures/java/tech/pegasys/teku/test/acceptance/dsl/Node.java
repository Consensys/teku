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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import tech.pegasys.teku.infrastructure.async.Waiter;

public abstract class Node {
  public static final String TEKU_DOCKER_IMAGE = "consensys/teku:develop";

  protected static final int REST_API_PORT = 9051;
  protected static final String CONFIG_FILE_PATH = "/config.yaml";
  protected static final String WORKING_DIRECTORY = "/opt/teku/";
  protected static final String DATA_PATH = WORKING_DIRECTORY + "data/";
  protected static final int P2P_PORT = 9000;
  protected static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory().disable(WRITE_DOC_START_MARKER));
  protected final NodeContainer container;
  protected final String nodeAlias;

  private static final AtomicInteger NODE_UNIQUIFIER = new AtomicInteger();

  protected Node(final Network network, final String dockerImageName, final Logger log) {
    this.nodeAlias =
        getClass().getSimpleName().toLowerCase(Locale.US) + NODE_UNIQUIFIER.incrementAndGet();
    this.container =
        new NodeContainer(dockerImageName)
            .withNetwork(network)
            .withNetworkAliases(nodeAlias)
            .withLogConsumer(frame -> log.debug(frame.getUtf8String().trim()));
  }

  public void stop() {
    container.stop();
  }

  public void waitForLogMessageContaining(final String filter) {
    waitFor(() -> assertThat(getFilteredOutput(filter)).isNotEmpty(), 2, TimeUnit.MINUTES);
  }

  protected void waitFor(
      final Waiter.Condition condition, final int timeoutAmount, final TimeUnit timeoutUnit) {
    try {
      Waiter.waitFor(condition, timeoutAmount, timeoutUnit);
    } catch (final Throwable t) {
      fail(t.getMessage() + " Logs: " + container.getLogs(), t);
    }
  }

  protected void waitFor(final Waiter.Condition condition) {
    try {
      Waiter.waitFor(condition, 1, TimeUnit.MINUTES);
    } catch (final Throwable t) {
      fail(t.getMessage() + " Logs: " + container.getLogs(), t);
    }
  }

  public String getLoggedErrors() {
    return container.getLogs(OutputFrame.OutputType.STDERR);
  }

  public List<String> getFilteredOutput(final String filter) {
    return container
        .getLogs(OutputFrame.OutputType.STDOUT)
        .lines()
        .filter(s -> s.contains(filter))
        .collect(Collectors.toList());
  }

  public void captureDebugArtifacts(final File artifactDir) {}

  protected void copyDirectoryToTar(final String sourcePath, final File localTargetDir) {
    try {
      try (InputStream inputStream =
          container
              .getDockerClient()
              .copyArchiveFromContainerCmd(container.getContainerId(), sourcePath)
              .exec()) {
        IOUtils.copy(inputStream, Files.newOutputStream(localTargetDir.toPath()));
      }
    } catch (final IOException e) {
      throw new RuntimeException("Failed to copy directory from " + nodeAlias, e);
    }
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
}
