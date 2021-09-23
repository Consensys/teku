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
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllPhase0FieldsSet;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SpecConfigReaderTest {
  private SpecConfigReader reader = new SpecConfigReader();

  @ParameterizedTest(name = "{0}")
  @MethodSource("getConfigArgs")
  public void read_standardConfigs(final String network, final String filePath) throws Exception {
    processFileAsInputStream(filePath, reader::read);
    final SpecConfig result = reader.build();

    assertThat(result).isNotNull();
    assertAllPhase0FieldsSet(result);
  }

  @Test
  public void read_altair() throws Exception {
    processFileAsInputStream(getLegacyConfigPath("mainnetAltair"), reader::read);
    final SpecConfig result = reader.build();

    assertThat(result).isNotNull();
    assertAllAltairFieldsSet(result);
  }

  @Test
  public void read_multiFileFormat() throws Exception {
    processFileAsInputStream(getLegacyConfigPath("multifile/phase0"), reader::read);
    processFileAsInputStream(getLegacyConfigPath("multifile/altair"), reader::read);
    final SpecConfig result = reader.build();

    assertThat(result).isNotNull();
    assertAllAltairFieldsSet(result);
  }

  @Test
  public void read_multiFileFormat_mismatchedDuplicateFields() {
    processFileAsInputStream(getInvalidConfigPath("multifile_dupFields/config"), reader::read);
    processFileAsInputStream(
        getInvalidConfigPath("multifile_dupFields/preset_phase0"),
        preset -> {
          assertThatThrownBy(() -> reader.read(preset))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining(
                  "Found duplicate declarations for spec constant 'MAX_COMMITTEES_PER_SLOT' with divergent values: '12' and '64'");
          return null;
        });
  }

  @Test
  public void read_mainnet() throws Exception {
    final SpecConfig config = readStandardConfigWithPreset("mainnet");
    assertThat(config).isNotNull();

    // Spot check a few values
    assertThat(config.getMaxCommitteesPerSlot()).isEqualTo(64);
    Assertions.assertThat(config.getTargetCommitteeSize()).isEqualTo(128);
    assertAllAltairFieldsSet(config);
  }

  @Test
  public void read_minimal() throws Exception {
    final SpecConfig config = readStandardConfigWithPreset("minimal");
    assertThat(config).isNotNull();

    // Spot check a few values
    assertThat(config.getMaxCommitteesPerSlot()).isEqualTo(4);
    Assertions.assertThat(config.getTargetCommitteeSize()).isEqualTo(4);
    assertAllAltairFieldsSet(config);
  }

  @Test
  public void read_legacyMainnet() throws Exception {
    final SpecConfig config = readLegacyMainnet();
    assertThat(config).isNotNull();

    // Spot check a few values
    assertThat(config.getMaxCommitteesPerSlot()).isEqualTo(64);
    Assertions.assertThat(config.getTargetCommitteeSize()).isEqualTo(128);
    assertAllPhase0FieldsSet(config);
  }

  @Test
  public void read_legacyMinimal() throws Exception {
    final SpecConfig config = readLegacyMinimal();
    assertThat(config).isNotNull();

    // Spot check a few values
    assertThat(config.getMaxCommitteesPerSlot()).isEqualTo(4);
    Assertions.assertThat(config.getTargetCommitteeSize()).isEqualTo(4);
    assertAllPhase0FieldsSet(config);
  }

  @Test
  public void read_distinctFilesProduceDifferentValues() throws Exception {
    final SpecConfig mainnet = readLegacyMainnet();
    assertThat(mainnet).isNotNull();
    // Reset config reader
    reader = new SpecConfigReader();
    final SpecConfig minimal = readLegacyMinimal();
    assertThat(mainnet).isNotNull();

    assertThat(mainnet).isNotEqualTo(minimal);
    assertThat(minimal).isNotEqualTo(mainnet);
  }

  @Test
  public void read_missingConfig() throws Exception {
    processFileAsInputStream(getInvalidConfigPath("missingChurnLimit"), reader::read);

    assertThatThrownBy(reader::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing value for spec constant 'MIN_PER_EPOCH_CHURN_LIMIT'");
  }

  @Test
  public void read_missingAltairConstant() throws IOException {
    processFileAsInputStream(getInvalidConfigPath("missingAltairField"), reader::read);

    assertThatThrownBy(reader::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing value for spec constant 'EPOCHS_PER_SYNC_COMMITTEE_PERIOD'");
  }

  @Test
  public void read_emptyFile() {
    processFileAsInputStream(
        getInvalidConfigPath("empty"),
        stream -> {
          assertThatThrownBy(() -> reader.read(stream))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Supplied spec config is empty");
          return null;
        });
  }

  @Test
  public void read_almostEmptyFile() throws Exception {
    processFileAsInputStream(getInvalidConfigPath("almostEmpty"), reader::read);

    assertThatThrownBy(reader::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing value for spec constant");
  }

  @Test
  public void read_invalidInteger_wrongType() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidInteger_wrongType"),
        stream -> {
          assertThatThrownBy(() -> reader.read(stream))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining(
                  "Failed to parse value for constant MAX_COMMITTEES_PER_SLOT: 'string value'");
          return null;
        });
  }

  @Test
  public void read_invalidInteger_tooLarge() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidInteger_tooLarge"),
        stream -> {
          assertThatThrownBy(() -> reader.read(stream))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining(
                  "Failed to parse value for constant MAX_COMMITTEES_PER_SLOT: '2147483648'");
          return null;
        });
  }

  @Test
  public void read_invalidInteger_negative() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidInteger_negative"),
        stream -> {
          assertThatThrownBy(() -> reader.read(stream))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining(
                  "Failed to parse value for constant MAX_COMMITTEES_PER_SLOT: '-1'");
          return null;
        });
  }

  @Test
  public void read_invalidLong_wrongType() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidLong_wrongType"),
        stream -> {
          assertThatThrownBy(() -> reader.read(stream))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining(
                  "Failed to parse value for constant VALIDATOR_REGISTRY_LIMIT: '[1, 2, 3]'");
          return null;
        });
  }

  @Test
  public void read_invalidLong_tooLarge() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidLong_tooLarge"),
        stream -> {
          assertThatThrownBy(() -> reader.read(stream))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining(
                  "Failed to parse value for constant VALIDATOR_REGISTRY_LIMIT: '9223372036854775808'");
          return null;
        });
  }

  @Test
  public void read_invalidLong_negative() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidLong_negative"),
        stream -> {
          assertThatThrownBy(() -> reader.read(stream))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining(
                  "Failed to parse value for constant VALIDATOR_REGISTRY_LIMIT: '-1099511627776'");
          return null;
        });
  }

  @Test
  public void read_invalidUInt64_negative() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidUInt64_negative"),
        stream -> {
          assertThatThrownBy(() -> reader.read(stream))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Failed to parse value for constant MIN_GENESIS_TIME: '-1'");
          return null;
        });
  }

  @Test
  public void read_invalidUInt64_tooLarge() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidUInt64_tooLarge"),
        stream -> {
          assertThatThrownBy(() -> reader.read(stream))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining(
                  "Failed to parse value for constant MIN_GENESIS_TIME: '18446744073709552001'");
          return null;
        });
  }

  @Test
  public void read_invalidBytes4_tooLarge() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidBytes4_tooLarge"),
        stream -> {
          assertThatThrownBy(() -> reader.read(stream))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining(
                  "Failed to parse value for constant GENESIS_FORK_VERSION: '0x0102030405'");
          return null;
        });
  }

  @Test
  public void read_invalidBytes4_tooSmall() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidBytes4_tooSmall"),
        stream -> {
          assertThatThrownBy(() -> reader.read(stream))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining(
                  "Failed to parse value for constant GENESIS_FORK_VERSION: '0x0102'");
          return null;
        });
  }

  private SpecConfig readLegacyMainnet() throws IOException {
    return readConfig(getLegacyConfigPath("mainnet"));
  }

  private SpecConfig readLegacyMinimal() throws IOException {
    return readConfig(getLegacyConfigPath("minimal"));
  }

  private SpecConfig readConfig(final String path) {
    processFileAsInputStream(path, reader::read);
    return reader.build();
  }

  private SpecConfig readStandardConfigWithPreset(final String configName) {
    final String configPath = getStandardConfigPath(configName);
    final SpecConfigReader reader = new SpecConfigReader();

    final Optional<String> preset = processFileAsInputStream(configPath, reader::read);
    if (preset.isPresent()) {
      for (String presetPath : getPresetPaths(preset.get())) {
        processFileAsInputStream(presetPath, reader::read);
      }
    }
    return reader.build();
  }

  private List<String> getPresetPaths(final String presetName) {
    return List.of(
        getStandardConfigPath("presets/" + presetName + "/phase0"),
        getStandardConfigPath("presets/" + presetName + "/altair"));
  }

  public static Stream<Arguments> getConfigArgs() {
    return Stream.of(
        Arguments.of("mainnet", getLegacyConfigPath("mainnet")),
        Arguments.of("minimal", getLegacyConfigPath("minimal")),
        Arguments.of("prater", getLegacyConfigPath("prater")),
        Arguments.of("swift", getLegacyConfigPath("swift")));
  }

  private static String getStandardConfigPath(final String name) {
    return getConfigPath("standard/" + name);
  }

  private static String getInvalidConfigPath(final String name) {
    return getConfigPath("invalid/" + name);
  }

  private static String getLegacyConfigPath(final String name) {
    return getConfigPath("legacy/" + name);
  }

  private static String getConfigPath(final String name) {
    final String path = "tech/pegasys/teku/spec/config/";
    return path + name + ".yaml";
  }

  private <T> T processFileAsInputStream(
      final String fileName, final InputStreamHandler<T> handler) {
    try (final InputStream inputStream = getFileFromResourceAsStream(fileName)) {
      return handler.accept(inputStream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private InputStream getFileFromResourceAsStream(String fileName) {
    InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
    if (inputStream == null) {
      throw new IllegalArgumentException("File not found: " + fileName);
    }

    return inputStream;
  }

  private interface InputStreamHandler<T> {
    T accept(InputStream inputStream) throws IOException;
  }
}
