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
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.networks.Eth2Presets;

public class SpecConfigLoader {
  private static final String CONFIG_PATH = "configs/";
  private static final String PRESET_PATH = "presets/";

  public static SpecConfig loadConfigStrict(final String configName) {
    return loadConfig(configName, false, __ -> {});
  }

  public static SpecConfig loadConfig(final String configName) {
    return loadConfig(configName, __ -> {});
  }

  public static SpecConfig loadConfig(
      final String configName, final Consumer<SpecConfigBuilder> modifier) {
    return loadConfig(configName, true, modifier);
  }

  public static SpecConfig loadConfig(
      final String configName,
      final boolean ignoreUnknownConfigItems,
      final Consumer<SpecConfigBuilder> modifier) {
    final SpecConfigReader reader = new SpecConfigReader();
    processConfig(configName, reader, ignoreUnknownConfigItems);
    return reader.build(modifier);
  }

  public static SpecConfig loadRemoteConfig(final Map<String, String> config) {
    final SpecConfigReader reader = new SpecConfigReader();
    reader.loadFromMap(config, true);
    return reader.build();
  }

  static void processConfig(
      final String source, final SpecConfigReader reader, final boolean ignoreUnknownConfigItems) {
    try (final InputStream configFile = loadConfigurationFile(source)) {
      final Map<String, String> configValues = reader.readValues(configFile);
      final Optional<String> maybePreset =
          Optional.ofNullable(configValues.get(SpecConfigReader.PRESET_KEY));

      // Legacy config files won't have a preset field
      if (maybePreset.isPresent()) {
        final String preset = maybePreset.get();
        applyPreset(source, reader, ignoreUnknownConfigItems, preset);
      }

      reader.loadFromMap(configValues, ignoreUnknownConfigItems);
    } catch (IOException | IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unable to load configuration for network \"" + source + "\": " + e.getMessage(), e);
    }
  }

  private static void applyPreset(
      final String source,
      final SpecConfigReader reader,
      final boolean ignoreUnknownConfigItems,
      final String preset)
      throws IOException {
    try (final InputStream phase0Input = loadPhase0Preset(source, preset)) {
      reader.readAndApply(phase0Input, ignoreUnknownConfigItems);
    }

    try (final InputStream altairInput = loadAltairPreset(preset).orElse(null)) {
      // Altair is optional
      if (altairInput != null) {
        reader.readAndApply(altairInput, ignoreUnknownConfigItems);
      }
    }

    try (final InputStream bellatrixInput = loadBellatrixPreset(preset).orElse(null)) {
      // Bellatrix is optional
      if (bellatrixInput != null) {
        reader.readAndApply(bellatrixInput, ignoreUnknownConfigItems);
      }
    }

    try (final InputStream capellaInput = loadCapellaPreset(preset).orElse(null)) {
      // capella is optional
      if (capellaInput != null) {
        reader.readAndApply(capellaInput, ignoreUnknownConfigItems);
      }
    }
  }

  private static InputStream loadConfigurationFile(final String source) throws IOException {
    return getConfigLoader()
        .load(source, CONFIG_PATH + source + ".yaml")
        .orElseThrow(() -> new FileNotFoundException("Could not load spec config from " + source));
  }

  private static InputStream loadPhase0Preset(final String source, final String preset)
      throws IOException {
    return getPresetLoader()
        .load(PRESET_PATH + preset + "/phase0.yaml", PRESET_PATH + preset + "/phase0.yml")
        .orElseThrow(
            () ->
                new FileNotFoundException(
                    String.format(
                        "Could not load spec config preset '%s' specified in config '%s'",
                        preset, source)));
  }

  private static Optional<InputStream> loadAltairPreset(final String preset) throws IOException {
    return getPresetLoader()
        .load(PRESET_PATH + preset + "/altair.yaml", PRESET_PATH + preset + "/altair.yml");
  }

  private static Optional<InputStream> loadBellatrixPreset(final String preset) throws IOException {
    return getPresetLoader()
        .load(PRESET_PATH + preset + "/bellatrix.yaml", PRESET_PATH + preset + "/bellatrix.yml");
  }

  private static Optional<InputStream> loadCapellaPreset(final String preset) throws IOException {
    return getPresetLoader()
        .load(PRESET_PATH + preset + "/capella.yaml", PRESET_PATH + preset + "/capella.yml");
  }

  private static ResourceLoader getConfigLoader() {
    return ResourceLoader.classpathUrlOrFile(
        SpecConfig.class,
        enumerateAvailableConfigResources(),
        s -> s.endsWith(".yaml") || s.endsWith(".yml"));
  }

  private static ResourceLoader getPresetLoader() {
    return ResourceLoader.classpathUrlOrFile(
        SpecConfig.class,
        enumerateAvailablePresetResources(),
        s -> s.endsWith(".yaml") || s.endsWith(".yml"));
  }

  private static List<String> enumerateAvailableConfigResources() {
    return Arrays.stream(Eth2Network.values())
        .map(Eth2Network::configName)
        .map(s -> CONFIG_PATH + s + ".yaml")
        .collect(Collectors.toList());
  }

  private static List<String> enumerateAvailablePresetResources() {
    return Arrays.stream(Eth2Presets.values())
        .map(Eth2Presets::presetName)
        .flatMap(
            s ->
                Stream.of(
                    PRESET_PATH + s + "/phase0.yaml",
                    PRESET_PATH + s + "/altair.yaml",
                    PRESET_PATH + s + "/bellatrix.yaml"))
        .collect(Collectors.toList());
  }
}
