/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.util.config;

import static org.apache.logging.log4j.util.Strings.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.logging.LoggingDestination;

final class ArtemisConfigurationTest {

  @AfterEach
  public void tearDown() {
    Constants.setConstants("minimal");
  }

  @Test
  void validMinimum() {
    ArtemisConfiguration.fromString("");
  }

  @Test
  void wrongPort() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ArtemisConfiguration.fromString("node.identity=\"2345\"\nnode.port=100000"));
  }

  @Test
  void invalidAdvertisedPort() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ArtemisConfiguration.fromString("node.identity=\"2345\"\nnode.advertisedPort=100000"));
  }

  @Test
  void advertisedPortDefaultsToPort() {
    final ArtemisConfiguration config = ArtemisConfiguration.fromString("node.port=1234");
    assertThat(config.getAdvertisedPort()).isEqualTo(1234);
  }

  @Test
  void invalidNetworkMode() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ArtemisConfiguration.fromString(
                "node.identity=\"2345\"\nnode.networkMode=\"tcpblah\""));
  }

  @Test
  void invalidMinimalArtemisConfig() {
    Constants.setConstants("minimal");
    ArtemisConfiguration config = ArtemisConfiguration.fromString("deposit.numValidators=7");
    assertThrows(IllegalArgumentException.class, () -> config.validateConfig());
  }

  @Test
  void invalidMainnetArtemisConfig() {
    Constants.setConstants("mainnet");
    ArtemisConfiguration config = ArtemisConfiguration.fromString("deposit.numValidators=31");
    assertThrows(IllegalArgumentException.class, () -> config.validateConfig());
  }

  @Test
  void shouldReadRestApiSettings() {
    ArtemisConfiguration config =
        ArtemisConfiguration.fromString(
            "beaconrestapi.portNumber=1\nbeaconrestapi.enableSwagger=false");
    assertEquals(config.getBeaconRestAPIPortNumber(), 1);
    assertEquals(config.getBeaconRestAPIEnableSwagger(), false);
  }

  @Test
  void shouldDefaultRestApiSettings() {

    ArtemisConfiguration config = ArtemisConfiguration.fromString(EMPTY);

    assertEquals(false, config.getBeaconRestAPIEnableSwagger());
    assertEquals(5051, config.getBeaconRestAPIPortNumber());
  }

  @Test
  void dataPathCanBeSet() {
    final ArtemisConfiguration config = ArtemisConfiguration.fromString("output.dataPath=\".\"");
    assertThat(config.getDataPath()).isEqualTo(".");
  }

  @Test
  void validatorKeyStoreAndPasswordFileCanBeSet() {
    final ArtemisConfiguration config =
        ArtemisConfiguration.fromString(
            "validator.keystoreFiles=["
                + "\"/path/to/Keystore1.json\",\"/path/to/Keystore2.json\""
                + "]\n"
                + "validator.keystorePasswordFiles=["
                + "\"/path/to/Keystore1password.txt\", \"/path/to/Keystore2password.txt\""
                + "]");
    assertThat(config.getValidatorKeystorePasswordFilePairs()).size().isEqualTo(2);
    assertThat(config.getValidatorKeystorePasswordFilePairs())
        .containsExactlyInAnyOrder(
            Pair.of(Path.of("/path/to/Keystore1.json"), Path.of("/path/to/Keystore1password.txt")),
            Pair.of(Path.of("/path/to/Keystore2.json"), Path.of("/path/to/Keystore2password.txt")));
  }

  @Test
  void invalidKeystoreAndPasswordParametersThrowsException() {
    final ArtemisConfiguration config =
        ArtemisConfiguration.fromString(
            "validator.keystoreFiles=["
                + "\"/path/to/Keystore1.json\",\"/path/to/Keystore2.json\""
                + "]\n"
                + "validator.keystorePasswordFiles=["
                + "\"/path/to/Keystore1password.txt\""
                + "]");

    final String errorMessage =
        "Invalid configuration. The size of validator.validatorsKeystoreFiles [2] and validator.validatorsKeystorePasswordFiles [1] must match";
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> config.validateConfig())
        .withMessage(errorMessage);
  }

  @Test
  void loggingColorEnabledShouldExceptionWhenIsNotBoolean() {
    final Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ArtemisConfiguration.fromString("logging.colorEnabled = \"2345\""));

    assertThat(exception.getMessage()).contains("logging.colorEnabled' requires a boolean");
  }

  @Test
  void loggingColorEnableShouldDefaultToTrue() {
    final ArtemisConfiguration config = ArtemisConfiguration.fromString("");
    assertThat(config.getLoggingConfiguration().isColorEnabled()).isTrue();
  }

  @Test
  void loggingColorEnableShouldSet() {
    final ArtemisConfiguration config =
        ArtemisConfiguration.fromString("logging.colorEnabled = false");
    assertThat(config.getLoggingConfiguration().isColorEnabled()).isFalse();
  }

  @Test
  void loggingIncludeEventsEnabledShouldExceptionWhenIsNotBoolean() {
    final Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ArtemisConfiguration.fromString("logging.includeEventsEnabled = \"2345\""));

    assertThat(exception.getMessage()).contains("logging.includeEventsEnabled' requires a boolean");
  }

  @Test
  void loggingIncludeEventsEnableShouldDefaultToTrue() {
    final ArtemisConfiguration config = ArtemisConfiguration.fromString("");
    assertThat(config.getLoggingConfiguration().isIncludeEventsEnabled()).isTrue();
  }

  @Test
  void loggingIncludeEventsEnableShouldSet() {
    final ArtemisConfiguration config =
        ArtemisConfiguration.fromString("logging.includeEventsEnabled = false");
    assertThat(config.getLoggingConfiguration().isIncludeEventsEnabled()).isFalse();
  }

  @Test
  void loggingDestinationShouldExceptionWhenIsNotString() {
    final Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ArtemisConfiguration.fromString("logging.destination = false"));

    assertThat(exception.getMessage()).contains("logging.destination' requires a string");
  }

  @Test
  void loggingDestinationShouldExceptionWhenIsNotAcceptableString() {
    final Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ArtemisConfiguration.fromString("logging.destination = \"Not acceptable\""));

    assertThat(exception.getMessage())
        .contains("logging.destination' should be \"consoleOnly\", \"fileOnly\", or \"both\"");
  }

  @Test
  void loggingDestinationShouldDefaultToBoth() {
    final ArtemisConfiguration config = ArtemisConfiguration.fromString("");
    assertThat(config.getLoggingConfiguration().getDestination())
        .isEqualTo(LoggingDestination.BOTH);
  }

  @Test
  void loggingDestinationShouldSet() {
    final ArtemisConfiguration configBoth =
        ArtemisConfiguration.fromString("logging.destination = \"both\"");
    assertThat(configBoth.getLoggingConfiguration().getDestination())
        .isEqualTo(LoggingDestination.BOTH);

    final ArtemisConfiguration configCondole =
        ArtemisConfiguration.fromString("logging.destination = \"consoleOnly\"");
    assertThat(configCondole.getLoggingConfiguration().getDestination())
        .isEqualTo(LoggingDestination.CONSOLE_ONLY);

    final ArtemisConfiguration configFile =
        ArtemisConfiguration.fromString("logging.destination = \"fileOnly\"");
    assertThat(configFile.getLoggingConfiguration().getDestination())
        .isEqualTo(LoggingDestination.FILE_ONLY);
  }

  @Test
  void loggingFileShouldExceptionWhenIsNotString() {
    final Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ArtemisConfiguration.fromString("logging.file = false"));

    assertThat(exception.getMessage()).contains("logging.file' requires a string");
  }

  @Test
  void loggingFileShouldDefaultToTekuLog() {
    final ArtemisConfiguration config = ArtemisConfiguration.fromString("");
    assertThat(config.getLoggingConfiguration().getFile()).isEqualTo("teku.log");
  }

  @Test
  void loggingFileNamePatternShouldExceptionWhenIsNotString() {
    final Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> ArtemisConfiguration.fromString("logging.fileNamePattern = false"));

    assertThat(exception.getMessage()).contains("logging.fileNamePattern' requires a string");
  }

  @Test
  void loggingFileNamePatternShouldDefault() {
    final ArtemisConfiguration config = ArtemisConfiguration.fromString("");
    assertThat(config.getLoggingConfiguration().getFileNamePattern())
        .isEqualTo("teku_%d{yyyy-MM-dd}.log");
  }
}
