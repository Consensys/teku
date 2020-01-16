/*
 * Copyright 2020 ConsenSys AG.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame.OutputType;
import org.testcontainers.containers.output.WaitingConsumer;

public class ArtemisNodeUtils {
  private static final Logger LOG = LogManager.getLogger();
  private static final SecureRandom random = new SecureRandom();

  public static File generateGenesisState(final int numValidators) throws TimeoutException {
    final String mountPath = generateMountableDirectory("generate-genesis-state").getAbsolutePath();

    String containerMountPath = "/output";
    String outputFileName = "genesis-state.bin";
    final String containerOutputPath = Paths.get(containerMountPath, outputFileName).toString();

    final ArtemisCLI container =
        createContainer()
            .withFileSystemBind(mountPath, containerMountPath)
            .withCommand(
                "genesis",
                "mock",
                "-o",
                containerOutputPath,
                "-v",
                Integer.toString(numValidators, 10));

    container.start();
    container.waitForOutput("Genesis state file saved");
    container.stop();

    final File genesisState = Paths.get(mountPath, outputFileName).toFile();
    genesisState.deleteOnExit();

    // Genesis state should contain data
    assertThat(genesisState.length()).isGreaterThan(0);
    return genesisState;
  }

  private static File generateMountableDirectory(final String directoryPrefix) {
    long n = random.nextLong();
    Path mountPath = Paths.get("/tmp", directoryPrefix + Long.toUnsignedString(n));
    final File mountDirectory = new File(mountPath.toAbsolutePath().toString());
    mountDirectory.mkdirs();
    mountDirectory.deleteOnExit();

    return mountDirectory;
  }

  public static File generateGenesisState_tmpDir(final int numValidators)
      throws TimeoutException, IOException {
    String genesisStateFilename = "genesis-state.bin";
    final String outputDirectory = generateTmpDirectory("generate-genesis-state").getAbsolutePath();
    final File genesisState = Paths.get(outputDirectory, genesisStateFilename).toFile();
    genesisState.deleteOnExit();

    String containerDir = "/";
    final String containerOutputPath = Paths.get(containerDir, genesisStateFilename).toString();

    final ArtemisCLI container =
        createContainer()
            //      .withFileSystemBind(mountPath, containerMountPath)
            .withCommand(
                "genesis",
                "mock",
                "-o",
                containerOutputPath,
                "-v",
                Integer.toString(numValidators, 10));

    container.start();
    container.waitForOutput("Genesis state file saved");
    container.copyFileFromContainer(containerOutputPath, genesisState.getAbsolutePath());
    container.stop();

    // Genesis state should contain data
    assertThat(genesisState.length()).isGreaterThan(0);
    LOG.info("Generated state of size {} at {}", genesisState.length(), genesisState.getAbsolutePath());
    return genesisState;
  }

  private static File generateTmpDirectory(final String directoryPrefix) throws IOException {
    final Path mountPath = Files.createTempDirectory(directoryPrefix);
    final File mountDirectory = new File(mountPath.toAbsolutePath().toString());
    mountDirectory.mkdirs();
    mountDirectory.deleteOnExit();

    return mountDirectory;
  }

  private static ArtemisCLI createContainer() {
    return new ArtemisCLI();
  }

  private static class ArtemisCLI extends GenericContainer<ArtemisCLI> {

    public ArtemisCLI() {
      super(ArtemisNode.ARTEMIS_DOCKER_IMAGE);
      this.withLogConsumer(frame -> LOG.info(frame.getUtf8String().trim()));
    }

    public void waitForOutput(final String output) throws TimeoutException {
      WaitingConsumer consumer = new WaitingConsumer();
      this.followOutput(consumer, OutputType.STDOUT);
      consumer.waitUntil(frame -> frame.getUtf8String().contains(output), 30, TimeUnit.SECONDS);
    }
  }
}
