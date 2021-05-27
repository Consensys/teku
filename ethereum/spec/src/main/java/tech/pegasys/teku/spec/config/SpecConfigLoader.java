/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.networks.Eth2Presets;
import tech.pegasys.teku.util.config.Constants;

public class SpecConfigLoader {
  private static final String CONFIG_PATH = "configs/";
  private static final String PRESET_PATH = "presets/";

  public static SpecConfig loadConfig(final String configName) {
    return loadConfig(configName, __ -> {});
  }

  public static SpecConfig loadConfig(
      final String configName, final Consumer<SpecConfigBuilder> modifier) {
    final SpecConfigReader reader = new SpecConfigReader();
    processConfig(configName, reader::read);
    return reader.build(modifier);
  }

  public static SpecConfig loadConfig(final Map<String, ?> config) {
    final SpecConfigReader reader = new SpecConfigReader();
    reader.loadFromMap(config);
    return reader.build();
  }

  static void processConfig(final String source, final InputStreamProcessor processor) {
    // TODO(#3394) - move Constants resources from util to this module
    final ResourceLoader configLoader =
        ResourceLoader.classpathUrlOrFile(
            Constants.class,
            enumerateAvailableConfigResources(),
            s -> s.endsWith(".yaml") || s.endsWith(".yml"));
    final ResourceLoader presetLoader =
        ResourceLoader.classpathUrlOrFile(
            Constants.class,
            enumerateAvailablePresetResources(),
            s -> s.endsWith(".yaml") || s.endsWith(".yml"));

    try {
      // Load the supplied config
      final InputStream configFile =
          configLoader
              .load(CONFIG_PATH + source + ".yaml", source)
              .orElseThrow(
                  () -> new FileNotFoundException("Could not load spec config from " + source));
      final Optional<String> maybePreset = processor.processConfig(configFile);

      // Load preset if any is set
      if (maybePreset.isEmpty()) {
        return;
      }
      final String preset = maybePreset.get();
      // Phase0 is required
      final InputStream phase0Input =
          presetLoader
              .load(PRESET_PATH + preset + "/phase0.yaml", PRESET_PATH + preset + "/phase0.yml")
              .orElseThrow(
                  () -> new FileNotFoundException("Could not load spec config from " + source));
      processor.processConfig(phase0Input);
      // Altair is optional
      final Optional<InputStream> altairInput =
          presetLoader.load(
              PRESET_PATH + preset + "/altair.yaml", PRESET_PATH + preset + "/altair.yml");
      if (altairInput.isPresent()) {
        processor.processConfig(altairInput.get());
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to load spec config", e);
    }
  }

  private static List<String> enumerateAvailableConfigResources() {
    return Arrays.stream(Eth2Network.values())
        .map(Eth2Network::configName)
        .map(s -> CONFIG_PATH + s + ".yaml")
        .collect(Collectors.toList());
  }

  private static List<String> enumerateAvailablePresetResources() {
    return Arrays.stream(Eth2Presets.values())
        .map(Eth2Presets::configName)
        .map(s -> List.of(PRESET_PATH + s + "/phase0.yaml", PRESET_PATH + s + "/altair.yaml"))
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  @FunctionalInterface
  interface InputStreamProcessor {
    /**
     * Process the given config input, optionally returning a preset that should be loaded.
     *
     * @param inputStream The input stream containing config data to be processed
     * @return An optional preset that should be loaded
     * @throws IOException Thrown if an error occurs while reading the input stream
     */
    Optional<String> processConfig(InputStream inputStream) throws IOException;
  }
}
