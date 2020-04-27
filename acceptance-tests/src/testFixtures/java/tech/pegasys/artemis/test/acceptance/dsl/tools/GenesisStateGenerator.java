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

package tech.pegasys.teku.test.acceptance.dsl.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GenesisStateGenerator {
  private static final Logger LOG = LogManager.getLogger();
  private Map<GenesisStateConfig, File> cache = new HashMap<>();

  public File generateState(final GenesisStateConfig config) throws TimeoutException, IOException {
    final File cachedGenesisState = cache.get(config);
    if (cachedGenesisState != null) {
      return cachedGenesisState;
    }

    String genesisStateFilename = "genesis-state.bin";
    final String outputDirectory = generateTmpDirectory("generate-genesis-state").getAbsolutePath();
    final File genesisState = Paths.get(outputDirectory, genesisStateFilename).toFile();
    genesisState.deleteOnExit();

    String containerDir = "/";
    final String containerOutputPath = Paths.get(containerDir, genesisStateFilename).toString();

    final TekuCLI container =
        createContainer()
            .withCommand(
                "genesis",
                "mock",
                "-o",
                containerOutputPath,
                "-v",
                Integer.toString(config.getNumValidators(), 10),
                "-t",
                Long.toString(config.getGenesisTime(), 10));

    container.start();
    container.waitForOutput("Genesis state file saved");
    container.copyFileFromContainer(containerOutputPath, genesisState.getAbsolutePath());
    container.stop();

    // Genesis state should contain data
    assertThat(genesisState.length()).isGreaterThan(0);
    LOG.debug(
        "Generated state of size {} at {}", genesisState.length(), genesisState.getAbsolutePath());
    cache.put(config, genesisState);
    return genesisState;
  }

  private File generateTmpDirectory(final String directoryPrefix) throws IOException {
    final Path mountPath = Files.createTempDirectory(directoryPrefix);
    final File mountDirectory = new File(mountPath.toAbsolutePath().toString());
    mountDirectory.mkdirs();
    mountDirectory.deleteOnExit();

    return mountDirectory;
  }

  private TekuCLI createContainer() {
    return new TekuCLI();
  }
}
