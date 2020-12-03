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

package tech.pegasys.teku.test.acceptance.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.MountableFile;

public class TekuVoluntaryExit extends Node {
  private static final Logger LOG = LogManager.getLogger();

  private final TekuVoluntaryExit.Config config;
  private boolean started = false;
  private Set<File> configFiles;

  private TekuVoluntaryExit(final Network network, final TekuVoluntaryExit.Config config) {
    super(network, TEKU_DOCKER_IMAGE, LOG);
    this.config = config;

    container
        .withWorkingDirectory(WORKING_DIRECTORY)
        .withCommand("validator-client", "--config-file", CONFIG_FILE_PATH);
  }

  public static TekuVoluntaryExit create(
      final Network network, Consumer<TekuVoluntaryExit.Config> configOptions) {

    final TekuVoluntaryExit.Config config = new TekuVoluntaryExit.Config();
    configOptions.accept(config);

    final TekuVoluntaryExit node = new TekuVoluntaryExit(network, config);

    return node;
  }

  public void start() throws Exception {
    assertThat(started).isFalse();
    LOG.debug("Start voluntary exit command line process {}", nodeAlias);
    started = true;
    final Map<File, String> configFiles = config.write();
    this.configFiles = configFiles.keySet();
    configFiles.forEach(
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
    configFiles.forEach(
        configFile -> {
          if (!configFile.delete() && configFile.exists()) {
            throw new RuntimeException("Failed to delete config file: " + configFile);
          }
        });
    container.stop();
  }

  public static class Config {
    private static final String VALIDATORS_FILE_PATH = "/validators.yml";
    private static final int DEFAULT_VALIDATOR_COUNT = 64;

    private Map<String, Object> configMap = new HashMap<>();

    private Optional<String> validatorKeys = Optional.empty();

    public Config() {
      configMap.put("epoch", 1);
      //      configMap.put("validators-keystore-locking-enabled", false);
      //      configMap.put("Xinterop-owned-validator-start-index", 0);
      //      configMap.put("Xinterop-owned-validator-count", DEFAULT_VALIDATOR_COUNT);
      //      configMap.put("Xinterop-number-of-validators", DEFAULT_VALIDATOR_COUNT);
      //      configMap.put("Xinterop-enabled", true);
      //      configMap.put("Xtransition-record-directory", WORKING_DIRECTORY + "transitions/");
      //      configMap.put("data-path", DATA_PATH);
      configMap.put("log-destination", "console");
      configMap.put("beacon-node-api-endpoint", "http://notvalid.restapi.com");
    }

    //    public TekuVoluntaryExit.Config withInteropModeDisabled() {
    //      configMap.put("Xinterop-enabled", false);
    //      return this;
    //    }

    public TekuVoluntaryExit.Config withValidatorKeys(final String validatorKeyInformation) {
      configMap.put("validator-keys", validatorKeyInformation);
      return this;
    }

    public TekuVoluntaryExit.Config withExitAtEpoch(final int exitEpoch) {
      configMap.put("epoch", exitEpoch);
      return this;
    }

    public TekuVoluntaryExit.Config withBeaconNodeEndpoint(final String beaconNodeEndpoint) {
      configMap.put("beacon-node-api-endpoint", beaconNodeEndpoint);
      return this;
    }

    public Map<File, String> write() throws Exception {
      final Map<File, String> configFiles = new HashMap<>();
      if (validatorKeys.isPresent()) {
        final File validatorsFile = Files.createTempFile("validators", ".yml").toFile();
        validatorsFile.deleteOnExit();
        Files.writeString(validatorsFile.toPath(), validatorKeys.get());
        configFiles.put(validatorsFile, VALIDATORS_FILE_PATH);
      }

      final File configFile = File.createTempFile("config", ".yaml");
      configFile.deleteOnExit();
      writeTo(configFile);
      configFiles.put(configFile, CONFIG_FILE_PATH);
      return configFiles;
    }

    private void writeTo(final File configFile) throws Exception {
      YAML_MAPPER.writeValue(configFile, configMap);
    }
  }
}
