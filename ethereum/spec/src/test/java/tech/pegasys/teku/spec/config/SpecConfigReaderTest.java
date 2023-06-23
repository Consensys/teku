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

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllAltairFieldsSet;

import java.io.IOException;
import java.io.InputStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SpecConfigReaderTest {
  private final SpecConfigReader reader = new SpecConfigReader();

  @Test
  public void read_missingConfig() {
    processFileAsInputStream(getInvalidConfigPath("missingChurnLimit"), this::readConfig);

    assertThatThrownBy(reader::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing value for spec constant 'MIN_PER_EPOCH_CHURN_LIMIT'");
  }

  @Test
  public void read_missingAltairConstant() {
    processFileAsInputStream(getInvalidConfigPath("missingAltairField"), this::readConfig);

    assertThatThrownBy(
            () ->
                reader.build(
                    builder ->
                        builder
                            .altairBuilder(
                                altairBuilder -> altairBuilder.altairForkEpoch(UInt64.ZERO))
                            .bellatrixBuilder(
                                bellatrixBuilder ->
                                    bellatrixBuilder.bellatrixForkEpoch(UInt64.ZERO))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing value for spec constant 'EPOCHS_PER_SYNC_COMMITTEE_PERIOD'");
  }

  @Test
  void read_unknownConstant() {
    assertThatThrownBy(
            () -> processFileAsInputStream(getInvalidConfigPath("unknownField"), this::readConfig))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Detected unknown spec config entries: UNKNOWN_CONSTANT");
  }

  @Test
  void read_ignoringUnknownConstant() {
    Assertions.assertThatCode(
            () -> {
              processFileAsInputStream(
                  getInvalidConfigPath("unknownField"),
                  source -> reader.readAndApply(source, true));
              assertAllAltairFieldsSet(reader.build());
            })
        .doesNotThrowAnyException();
  }

  @Test
  public void read_emptyFile() {
    processFileAsInputStream(
        getInvalidConfigPath("empty"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Supplied spec config is empty"));
  }

  @Test
  public void read_almostEmptyFile() {
    processFileAsInputStream(getInvalidConfigPath("almostEmpty"), this::readConfig);

    assertThatThrownBy(reader::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing value for spec constant");
  }

  @Test
  public void read_invalidInteger_wrongType() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidInteger_wrongType"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant MAX_COMMITTEES_PER_SLOT: 'string value'"));
  }

  @Test
  public void read_invalidInteger_tooLarge() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidInteger_tooLarge"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant MAX_COMMITTEES_PER_SLOT: '2147483648'"));
  }

  @Test
  public void read_invalidInteger_negative() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidInteger_negative"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant MAX_COMMITTEES_PER_SLOT: '-1'"));
  }

  @Test
  public void read_invalidLong_wrongType() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidLong_wrongType"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Cannot read spec config: Cannot deserialize value of type `java.lang.String` from Array"));
  }

  @Test
  public void read_invalidLong_tooLarge() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidLong_tooLarge"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant VALIDATOR_REGISTRY_LIMIT: '9223372036854775808'"));
  }

  @Test
  public void read_invalidLong_negative() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidLong_negative"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant VALIDATOR_REGISTRY_LIMIT: '-1099511627776'"));
  }

  @Test
  public void read_invalidUInt64_negative() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidUInt64_negative"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse value for constant MIN_GENESIS_TIME: '-1'"));
  }

  @Test
  public void read_invalidUInt64_tooLarge() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidUInt64_tooLarge"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant MIN_GENESIS_TIME: '18446744073709552001'"));
  }

  @Test
  public void read_invalidBytes4_tooLarge() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidBytes4_tooLarge"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant GENESIS_FORK_VERSION: '0x0102030405'"));
  }

  @Test
  public void read_invalidBytes4_tooSmall() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidBytes4_tooSmall"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant GENESIS_FORK_VERSION: '0x0102'"));
  }

  private void readConfig(final InputStream preset) throws IOException {
    reader.readAndApply(preset, false);
  }

  private static String getInvalidConfigPath(final String name) {
    return getConfigPath("invalid/" + name);
  }

  private static String getConfigPath(final String name) {
    final String path = "tech/pegasys/teku/spec/config/";
    return path + name + ".yaml";
  }

  private void processFileAsInputStream(final String fileName, final InputStreamHandler handler) {
    try (final InputStream inputStream = getFileFromResourceAsStream(fileName)) {
      handler.accept(inputStream);
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

  private interface InputStreamHandler {
    void accept(InputStream inputStream) throws IOException;
  }
}
