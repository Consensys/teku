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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllAltairFieldsSet;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllBellatrixFieldsSet;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllFieldsSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class SpecConfigLoaderTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("knownNetworks")
  public void shouldLoadAllKnownNetworks(final String name, final Class<?> configType)
      throws Exception {
    final SpecConfig config = SpecConfigLoader.loadConfigStrict(name);
    assertAllFieldsSet(config, configType);
  }

  /**
   * For the three networks supported by Infura, go the extra mile and ensure the CONFIG_NAME key is
   * still included in the raw config which is exposed by the config/spec REST API.
   *
   * <p>Prior to Altair, Lighthouse required this field to be a known testnet name, mainnet or
   * minimal. Post-Altair we will be able to remove this as the new PRESET_BASE key will be
   * sufficient.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"prater", "mainnet"})
  public void shouldMaintainConfigNameBackwardsCompatibility(final String name) {
    final SpecConfig config = SpecConfigLoader.loadConfig(name);
    assertThat(config.getRawConfig().get("CONFIG_NAME")).isEqualTo(name);
  }

  @Test
  public void shouldLoadMainnet() throws Exception {
    final SpecConfig config = SpecConfigLoader.loadConfig("mainnet");
    assertAllBellatrixFieldsSet(config);
  }

  @Test
  public void shouldLoadMainnetFromFileUrl() throws Exception {
    final URL url = getMainnetConfigResourceAsUrl();
    final SpecConfig config = SpecConfigLoader.loadConfig(url.toString());
    assertAllBellatrixFieldsSet(config);
  }

  @Test
  public void shouldLoadMainnetFromFile(@TempDir Path tempDir) throws Exception {
    try (final InputStream inputStream = getMainnetConfigAsStream()) {
      final Path file = tempDir.resolve("mainnet.yml");
      writeStreamToFile(inputStream, file);
      final SpecConfig config = SpecConfigLoader.loadConfig(file.toAbsolutePath().toString());
      assertAllBellatrixFieldsSet(config);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"altairForkSpec.json", "mergeForkSpec.json", "bellatrixForkSpec.json"})
  void shouldLoadMainnetPreservingBackwardsCompatibilityWithRestApi(final String specFilename)
      throws Exception {
    try (final InputStream in =
        Resources.getResource(SpecConfigLoaderTest.class, specFilename).openStream()) {
      final SpecConfig config = SpecConfigLoader.loadRemoteConfig(readJsonConfig(in));
      assertAllAltairFieldsSet(config);
    }
  }

  @Test
  public void shouldHandleInvalidPresetValue_wrongType(@TempDir Path tempDir) throws Exception {
    try (final InputStream inputStream = loadInvalidFile("invalidPreset_wrongType.yaml")) {
      final Path file = tempDir.resolve("invalid.yml");
      writeStreamToFile(inputStream, file);
      assertThatThrownBy(() -> SpecConfigLoader.loadConfig(file.toAbsolutePath().toString()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Unable to load configuration for network \""
                  + file.toAbsolutePath()
                  + "\": Could not load spec config preset '300' specified in config '"
                  + file.toAbsolutePath()
                  + "'");
    }
  }

  @Test
  public void shouldHandleInvalidPresetValue_unknownPreset(@TempDir Path tempDir) throws Exception {
    try (final InputStream inputStream = loadInvalidFile("invalidPreset_unknown.yaml")) {
      final Path file = tempDir.resolve("invalid.yml");
      writeStreamToFile(inputStream, file);
      assertThatThrownBy(() -> SpecConfigLoader.loadConfig(file.toAbsolutePath().toString()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Unable to load configuration for network \""
                  + file.toAbsolutePath()
                  + "\": Could not load spec config preset 'foo' specified in config '"
                  + file.toAbsolutePath()
                  + "'")
          .hasRootCauseMessage(
              "Could not load spec config preset 'foo' specified in config '%s'",
              file.toAbsolutePath().toString());
    }
  }

  @Test
  public void shouldBeAbleToOverridePresetValues() {
    final URL configUrl = SpecConfigLoaderTest.class.getResource("standard/with-overrides.yaml");
    final SpecConfig config = SpecConfigLoader.loadConfig(configUrl.toString(), false, __ -> {});
    assertThat(config).isNotNull();
    assertThat(config.getMaxCommitteesPerSlot()).isEqualTo(12); // Mainnet preset is 64.
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
    return Stream.of(Eth2Network.values())
        .map(network -> Arguments.of(network.configName(), SpecConfigBellatrix.class));
  }

  private void writeStreamToFile(final InputStream inputStream, final Path filePath)
      throws Exception {
    try (final OutputStream outputStream = Files.newOutputStream(filePath)) {
      IOUtils.copy(inputStream, outputStream);
    }
  }

  private InputStream getMainnetConfigAsStream() {
    return SpecConfig.class.getResourceAsStream("configs/mainnet.yaml");
  }

  private URL getMainnetConfigResourceAsUrl() {
    return SpecConfig.class.getResource("configs/mainnet.yaml");
  }

  private InputStream loadInvalidFile(final String file) {
    return getClass().getResourceAsStream("invalid/" + file);
  }

  private Map<String, String> readJsonConfig(final InputStream source) throws IOException {
    final ObjectMapper mapper = new ObjectMapper();
    return mapper
        .readerFor(mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class))
        .readValue(source);
  }
}
