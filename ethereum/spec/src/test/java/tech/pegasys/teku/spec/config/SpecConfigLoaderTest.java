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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllAltairFieldsSet;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllFieldsSet;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllPhase0FieldsSet;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.util.config.Constants;

public class SpecConfigLoaderTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("knownNetworks")
  public void shouldLoadAllKnownNetworks(final String name, final Class<?> configType)
      throws Exception {
    final SpecConfig config = SpecConfigLoader.loadConfig(name);
    assertAllFieldsSet(config, configType);
  }

  @Test
  public void shouldLoadMainnet() throws Exception {
    final SpecConfig config = SpecConfigLoader.loadConfig("mainnet");
    assertAllAltairFieldsSet(config);
  }

  @Test
  public void shouldLoadMainnetFromFileUrl() throws Exception {
    final URL url = getMainnetConfigResourceAsUrl();
    final SpecConfig config = SpecConfigLoader.loadConfig(url.toString());
    assertAllAltairFieldsSet(config);
  }

  @Test
  public void shouldLoadMainnetFromFile(@TempDir Path tempDir) throws Exception {
    try (final InputStream inputStream = getMainnetConfigAsStream()) {
      final Path file = tempDir.resolve("mainnet.yml");
      writeStreamToFile(inputStream, file);
      final SpecConfig config = SpecConfigLoader.loadConfig(file.toAbsolutePath().toString());
      assertAllAltairFieldsSet(config);
    }
  }

  @Test
  public void shouldLoadLegacyMainnetConfigFromFileUrl() throws Exception {
    final URL url = getLegacyMainnetConfigResourceAsUrl();
    final SpecConfig config = SpecConfigLoader.loadConfig(url.toString());
    assertAllPhase0FieldsSet(config);
  }

  @Test
  public void shouldLoadLegacyMainnetConfigFromFile(@TempDir Path tempDir) throws Exception {
    try (final InputStream inputStream = getLegacyMainnetConfigAsStream()) {
      final Path file = tempDir.resolve("mainnet.yml");
      writeStreamToFile(inputStream, file);
      final SpecConfig config = SpecConfigLoader.loadConfig(file.toAbsolutePath().toString());
      assertAllPhase0FieldsSet(config);
    }
  }

  @Test
  public void shouldHandleInvalidPresetValue_wrongType(@TempDir Path tempDir) throws Exception {
    try (final InputStream inputStream = loadInvalidFile("invalidPreset_wrongType.yaml")) {
      final Path file = tempDir.resolve("invalid.yml");
      writeStreamToFile(inputStream, file);
      assertThatThrownBy(() -> SpecConfigLoader.loadConfig(file.toAbsolutePath().toString()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Failed to load spec config")
          .hasRootCauseMessage(
              "Unable to parse config field 'PRESET_BASE' (value = '300') as a string");
    }
  }

  @Test
  public void shouldHandleInvalidPresetValue_unknownPreset(@TempDir Path tempDir) throws Exception {
    try (final InputStream inputStream = loadInvalidFile("invalidPreset_unknown.yaml")) {
      final Path file = tempDir.resolve("invalid.yml");
      writeStreamToFile(inputStream, file);
      assertThatThrownBy(() -> SpecConfigLoader.loadConfig(file.toAbsolutePath().toString()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Failed to load spec config")
          .hasRootCauseMessage(
              "Could not load spec config preset 'foo' specified in config '%s'",
              file.toAbsolutePath().toString());
    }
  }

  @Test
  public void shouldTestAllKnownNetworks() {
    final List<String> testedNetworks =
        knownNetworks().map(args -> (String) args.get()[0]).sorted().collect(Collectors.toList());
    final List<String> allKnownNetworks =
        Arrays.stream(Eth2Network.values())
            .map(Eth2Network::configName)
            .sorted()
            .collect(Collectors.toList());

    assertThat(testedNetworks).isEqualTo(allKnownNetworks);
  }

  static Stream<Arguments> knownNetworks() {
    return Stream.of(
        Arguments.of(Eth2Network.MAINNET.configName(), SpecConfigAltair.class),
        Arguments.of(Eth2Network.PYRMONT.configName(), SpecConfigPhase0.class),
        Arguments.of(Eth2Network.PRATER.configName(), SpecConfigPhase0.class),
        Arguments.of(Eth2Network.MINIMAL.configName(), SpecConfigAltair.class),
        Arguments.of(Eth2Network.SWIFT.configName(), SpecConfigPhase0.class),
        Arguments.of(Eth2Network.LESS_SWIFT.configName(), SpecConfigPhase0.class));
  }

  private void writeStreamToFile(final InputStream inputStream, final Path filePath)
      throws Exception {
    byte[] buffer = new byte[inputStream.available()];
    inputStream.read(buffer);
    Files.write(filePath, buffer);
  }

  private InputStream getMainnetConfigAsStream() {
    return Constants.class
        .getClassLoader()
        .getResourceAsStream("tech/pegasys/teku/util/config/configs/mainnet.yaml");
  }

  private URL getMainnetConfigResourceAsUrl() {
    return Constants.class
        .getClassLoader()
        .getResource("tech/pegasys/teku/util/config/configs/mainnet.yaml");
  }

  private InputStream getLegacyMainnetConfigAsStream() {
    return getClass()
        .getClassLoader()
        .getResourceAsStream("tech/pegasys/teku/spec/config/legacy/mainnet.yaml");
  }

  private URL getLegacyMainnetConfigResourceAsUrl() {
    return getClass()
        .getClassLoader()
        .getResource("tech/pegasys/teku/spec/config/legacy/mainnet.yaml");
  }

  private InputStream loadInvalidFile(final String file) {
    return getClass().getResourceAsStream("invalid/" + file);
  }
}
