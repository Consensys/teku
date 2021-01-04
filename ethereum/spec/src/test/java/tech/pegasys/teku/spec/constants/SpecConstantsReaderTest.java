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

package tech.pegasys.teku.spec.constants;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SpecConstantsReaderTest {
  private final SpecConstantsReader reader = new SpecConstantsReader();

  @ParameterizedTest(name = "{0}")
  @MethodSource("getConstantsArgs")
  public void read_standardConfigs(final String network, final String filePath) throws Exception {
    final InputStream inputStream = getFileFromResourceAsStream(filePath);

    final SpecConstants result = reader.read(inputStream);
    assertThat(result).isNotNull();
    assertAllFieldsSet(result);
  }

  @Test
  public void read_mainnet() throws Exception {
    final SpecConstants constants = readMainnet();
    assertThat(constants).isNotNull();

    // Spot check a few values
    assertThat(constants.getMaxCommitteesPerSlot()).isEqualTo(64);
    Assertions.assertThat(constants.getTargetCommitteeSize()).isEqualTo(128);
    assertAllFieldsSet(constants);
  }

  @Test
  public void read_minimal() throws Exception {
    final SpecConstants constants = readMinimal();
    assertThat(constants).isNotNull();

    // Spot check a few values
    assertThat(constants.getMaxCommitteesPerSlot()).isEqualTo(4);
    Assertions.assertThat(constants.getTargetCommitteeSize()).isEqualTo(4);
    assertAllFieldsSet(constants);
  }

  @Test
  public void read_distinctFilesProduceDifferentValues() throws Exception {
    final SpecConstants mainnet = readMainnet();
    assertThat(mainnet).isNotNull();
    final SpecConstants minimal = readMinimal();
    assertThat(mainnet).isNotNull();

    assertThat(mainnet).isNotEqualTo(minimal);
    assertThat(minimal).isNotEqualTo(mainnet);
    assertThat(mainnet).isEqualTo(readMainnet());
    assertThat(minimal).isEqualTo(readMinimal());
  }

  @Test
  public void read_missingConstants() {
    final String path = getInvalidConfigPath("missingChurnLimit");
    final InputStream stream = getFileFromResourceAsStream(path);
    assertThatThrownBy(() -> reader.read(stream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing value for constant MIN_PER_EPOCH_CHURN_LIMIT");
  }

  @Test
  public void read_emptyFile() {
    final String path = getInvalidConfigPath("empty");
    final InputStream stream = getFileFromResourceAsStream(path);
    assertThatThrownBy(() -> reader.read(stream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Supplied constants are empty");
  }

  @Test
  public void read_almostEmptyFile() {
    final String path = getInvalidConfigPath("almostEmpty");
    final InputStream stream = getFileFromResourceAsStream(path);
    assertThatThrownBy(() -> reader.read(stream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing value for constant");
  }

  @Test
  public void read_invalidInteger_wrongType() {
    final String path = getInvalidConfigPath("invalidInteger_wrongType");
    final InputStream stream = getFileFromResourceAsStream(path);
    assertThatThrownBy(() -> reader.read(stream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Failed to parse value for constant MAX_COMMITTEES_PER_SLOT: 'string value'");
  }

  @Test
  public void read_invalidInteger_tooLarge() {
    final String path = getInvalidConfigPath("invalidInteger_tooLarge");
    final InputStream stream = getFileFromResourceAsStream(path);
    assertThatThrownBy(() -> reader.read(stream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Failed to parse value for constant MAX_COMMITTEES_PER_SLOT: '2147483648'");
  }

  @Test
  public void read_invalidInteger_negative() {
    final String path = getInvalidConfigPath("invalidInteger_negative");
    final InputStream stream = getFileFromResourceAsStream(path);
    assertThatThrownBy(() -> reader.read(stream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to parse value for constant MAX_COMMITTEES_PER_SLOT: '-1'");
  }

  @Test
  public void read_invalidLong_wrongType() {
    final String path = getInvalidConfigPath("invalidLong_wrongType");
    final InputStream stream = getFileFromResourceAsStream(path);
    assertThatThrownBy(() -> reader.read(stream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Failed to parse value for constant VALIDATOR_REGISTRY_LIMIT: '[1, 2, 3]'");
  }

  @Test
  public void read_invalidLong_tooLarge() {
    final String path = getInvalidConfigPath("invalidLong_tooLarge");
    final InputStream stream = getFileFromResourceAsStream(path);
    assertThatThrownBy(() -> reader.read(stream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Failed to parse value for constant VALIDATOR_REGISTRY_LIMIT: '9223372036854775808'");
  }

  @Test
  public void read_invalidLong_negative() {
    final String path = getInvalidConfigPath("invalidLong_negative");
    final InputStream stream = getFileFromResourceAsStream(path);
    assertThatThrownBy(() -> reader.read(stream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Failed to parse value for constant VALIDATOR_REGISTRY_LIMIT: '-1099511627776'");
  }

  @Test
  public void read_invalidUInt64_negative() {
    final String path = getInvalidConfigPath("invalidUInt64_negative");
    final InputStream stream = getFileFromResourceAsStream(path);
    assertThatThrownBy(() -> reader.read(stream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to parse value for constant MIN_GENESIS_TIME: '-1'");
  }

  @Test
  public void read_invalidUInt64_tooLarge() {
    final String path = getInvalidConfigPath("invalidUInt64_tooLarge");
    final InputStream stream = getFileFromResourceAsStream(path);
    assertThatThrownBy(() -> reader.read(stream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Failed to parse value for constant MIN_GENESIS_TIME: '18446744073709552001'");
  }

  @Test
  public void read_invalidBytes4_tooLarge() {
    final String path = getInvalidConfigPath("invalidBytes4_tooLarge");
    final InputStream stream = getFileFromResourceAsStream(path);
    assertThatThrownBy(() -> reader.read(stream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Failed to parse value for constant GENESIS_FORK_VERSION: '0x0102030405'");
  }

  @Test
  public void read_invalidBytes4_tooSmall() {
    final String path = getInvalidConfigPath("invalidBytes4_tooSmall");
    final InputStream stream = getFileFromResourceAsStream(path);
    assertThatThrownBy(() -> reader.read(stream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to parse value for constant GENESIS_FORK_VERSION: '0x0102'");
  }

  private SpecConstants readMainnet() throws IOException {
    return readConstants(getStandardConfigPath("mainnet"));
  }

  private SpecConstants readMinimal() throws IOException {
    return readConstants(getStandardConfigPath("minimal"));
  }

  private SpecConstants readConstants(final String path) throws IOException {
    final InputStream stream = getFileFromResourceAsStream(path);
    return reader.read(stream);
  }

  public static Stream<Arguments> getConstantsArgs() {
    return Stream.of(
        Arguments.of("mainnet", getStandardConfigPath("mainnet")),
        Arguments.of("medalla", getStandardConfigPath("medalla")),
        Arguments.of("minimal", getStandardConfigPath("minimal")),
        Arguments.of("pyrmont", getStandardConfigPath("pyrmont")),
        Arguments.of("swift", getStandardConfigPath("swift")),
        Arguments.of("toledo", getStandardConfigPath("toledo")));
  }

  private static String getInvalidConfigPath(final String name) {
    return getConfigPath("invalid/" + name);
  }

  private static String getStandardConfigPath(final String name) {
    return getConfigPath("standard/" + name);
  }

  private static String getConfigPath(final String name) {
    final String path = "tech/pegasys/teku/spec/constants/";
    return path + name + ".yaml";
  }

  private InputStream getFileFromResourceAsStream(String fileName) {
    InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
    if (inputStream == null) {
      throw new IllegalArgumentException("File not found: " + fileName);
    }

    return inputStream;
  }

  private void assertAllFieldsSet(final SpecConstants constants) throws Exception {
    final List<String> zeroFields = List.of("genesisSlot", "genesisEpoch");
    for (Field field : SpecConstants.class.getFields()) {
      final Object value = field.get(constants);
      Assertions.assertThat(value).describedAs(field.getName()).isNotNull();
      if (!zeroFields.contains(field.getName())) {
        Assertions.assertThat(value).describedAs(field.getName()).isNotEqualTo(0);
        Assertions.assertThat(value).describedAs(field.getName()).isNotEqualTo(0L);
        Assertions.assertThat(value).describedAs(field.getName()).isNotEqualTo(UInt64.ZERO);
      }
    }
  }
}
