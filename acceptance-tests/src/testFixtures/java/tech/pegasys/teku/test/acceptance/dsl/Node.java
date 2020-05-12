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

import static com.google.common.io.Files.createTempDir;
import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import tech.pegasys.teku.util.Waiter;
import tech.pegasys.teku.util.file.FileUtil;

public abstract class Node {
  private static final AtomicInteger NODE_UNIQUIFIER = new AtomicInteger();
  protected final GenericContainer<?> container;
  protected final String nodeAlias;
  private final List<File> tempDirectories = new ArrayList<>();

  public Node(final Network network, final String dockerImageName, final Logger log) {
    this.nodeAlias =
        getClass().getSimpleName().toLowerCase(Locale.US) + NODE_UNIQUIFIER.incrementAndGet();
    this.container =
        new GenericContainer<>(dockerImageName)
            .withNetwork(network)
            .withNetworkAliases(nodeAlias)
            .withLogConsumer(frame -> log.debug(frame.getUtf8String().trim()));
  }

  public void stop() {
    container.stop();
    cleanupTemporaryDirectories();
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

  protected File createTempDirectory() {
    File tmpDir = createTempDir();
    tempDirectories.add(tmpDir);
    return tmpDir;
  }

  private void cleanupTemporaryDirectories() {
    FileUtil.recursivelyDeleteDirectories(tempDirectories);
  }
}
