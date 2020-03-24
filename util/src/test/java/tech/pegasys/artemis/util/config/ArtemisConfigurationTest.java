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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

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
  void validatorExternalSignerPublicKeysCanBeSet() {
    final String publicKey1 =
        "0xa99a76ed7796f7be22d5b7e85deeb7c5677e88e511e0b337618f8c4eb61349b4bf2d153f649f7b53359fe8b94a38e44c";
    final String publicKey2 =
        "0xb89bebc699769726a318c8e9971bd3171297c61aea4a6578a7a4f94b547dcba5bac16a89108b6b6a1fe3695d1a874a0b";
    final ArtemisConfiguration config =
        ArtemisConfiguration.fromString(
            "validator.externalSignerPublicKeys=[\"" + publicKey1 + "\",\"" + publicKey2 + "\"]");
    assertThat(config.getValidatorExternalSigningPublicKeys()).size().isEqualTo(2);
    assertThat(config.getValidatorExternalSigningPublicKeys())
        .containsExactlyInAnyOrder(
            BLSPublicKey.fromBytes(Bytes.fromHexString(publicKey1)),
            BLSPublicKey.fromBytes(Bytes.fromHexString(publicKey2)));
  }

  @Test
  void invalidValidatorExternalSignerPublicKeysThrowsException() {
    final String publicKey1 = "invalidPublicKey";
    final ArtemisConfiguration config =
        ArtemisConfiguration.fromString(
            "validator.externalSignerPublicKeys=[" + "\"" + publicKey1 + "\"]");
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(config::getValidatorExternalSigningPublicKeys)
        .withMessage("Invalid configuration. Signer public key is invalid");
  }

  @Test
  void validatorExternalSignerUrlCanBeSet() throws MalformedURLException {
    final ArtemisConfiguration config =
        ArtemisConfiguration.fromString("validator.externalSignerUrl=\"http://localhost:9000\"");
    assertThat(config.getValidatorExternalSigningUrl()).isEqualTo(new URL("http://localhost:9000"));
  }

  @Test
  void invalidValidatorExternalSignerUrlThrowsException() {
    final ArtemisConfiguration config =
        ArtemisConfiguration.fromString("validator.externalSignerUrl=\"invalid_url\"");
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(config::getValidatorExternalSigningUrl)
        .withMessage("Invalid configuration. Signer URL has invalid syntax");
  }

  @Test
  void validatorExternalSignerTimeoutCanBeSet() {
    final ArtemisConfiguration config =
        ArtemisConfiguration.fromString("validator.externalSignerTimeout=5000");
    assertThat(config.getValidatorExternalSigningTimeout()).isEqualTo(5000);
  }

  @Test
  void validatorExternalSignerTimeoutCanReturnsDefault() {
    final ArtemisConfiguration config = ArtemisConfiguration.fromString(EMPTY);
    assertThat(config.getValidatorExternalSigningTimeout()).isEqualTo(1000);
  }
}
