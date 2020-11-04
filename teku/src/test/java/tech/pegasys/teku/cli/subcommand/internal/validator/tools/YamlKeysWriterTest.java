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

package tech.pegasys.teku.cli.subcommand.internal.validator.tools;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;

class YamlKeysWriterTest {
  private static final BLSSecretKey validator1SecretKey =
      BLSSecretKey.fromBytes(
          Bytes32.fromHexString(
              "0x2CF622DE0FD92C7D4E59539CBDA63100E02CF59349595356CD97FFE6CB486460"));
  private static final BLSSecretKey withdrawal1SecretKey =
      BLSSecretKey.fromBytes(
          Bytes32.fromHexString(
              "0x6EA631AA885EC84AFA60BBD7887B5DBC91F594DEA29334E99576B51FAAD0E453"));
  private static final BLSSecretKey validator2SecretKey =
      BLSSecretKey.fromBytes(
          Bytes32.fromHexString(
              "0x147599AA450AADF69988F20FF1ADB3A3BF31BF9CDC77CF492FF95667708D8E79"));
  private static final BLSSecretKey withdrawal2SecretKey =
      BLSSecretKey.fromBytes(
          Bytes32.fromHexString(
              "0x0610B84CD68FB0FAB2F04A2A05EE01CD5F7374EB8EA93E26DB9C61DD2704B5BD"));
  private static final String yamlLine1 =
      "- {privkey: '0x2cf622de0fd92c7d4e59539cbda63100e02cf59349595356cd97ffe6cb486460', "
          + "pubkey: '0xb21680d9e0ab6e92c034cab6b6bcef3ea9f3203d8fc7dff4a17d978bf01b508f6d6852b067f9cc5ed82b462919e7dde0', "
          + "withdrawalPrivkey: '0x6ea631aa885ec84afa60bbd7887b5dbc91f594dea29334e99576b51faad0e453', "
          + "withdrawalPubkey: '0xb00fa5ff2eaee1b4354c402627acb85a6720463e62210c663ad3619962e28edd7e7422f69d4d229761c8f2ee6ad55b6f'}"
          + System.lineSeparator();
  private static final String yamlLine2 =
      "- {privkey: '0x147599aa450aadf69988f20ff1adb3a3bf31bf9cdc77cf492ff95667708d8e79', "
          + "pubkey: '0xa3a6f1ffcb5f2a577cc4ae4379c5492e02231869773ae9de1dd0e18f9ec511f81adc5980026ab3d6178d48db17beae0c', "
          + "withdrawalPrivkey: '0x0610b84cd68fb0fab2f04a2a05ee01cd5f7374eb8ea93e26db9c61dd2704b5bd', "
          + "withdrawalPubkey: '0xab70d135ed35279a636980845d94dc4b33fbc2a367eb75ef19bf0e28a2cd3c4025c1450f8d9d4d5ea9dc9af4dd21f186'}"
          + System.lineSeparator();
  private final String expectedYaml = yamlLine1 + yamlLine2;

  @Test
  void keysAreWrittenOnSystemOutput() {
    final ByteArrayOutputStream bytesStream = new ByteArrayOutputStream();
    final KeysWriter keysWriter = new YamlKeysWriter(new PrintStream(bytesStream, true, UTF_8));
    keysWriter.writeKeys(new BLSKeyPair(validator1SecretKey), new BLSKeyPair(withdrawal1SecretKey));
    assertThat(new String(bytesStream.toByteArray(), UTF_8)).isEqualTo(yamlLine1);

    keysWriter.writeKeys(new BLSKeyPair(validator2SecretKey), new BLSKeyPair(withdrawal2SecretKey));

    assertThat(new String(bytesStream.toByteArray(), UTF_8)).isEqualTo(expectedYaml);
  }

  @Test
  void keysAreWrittenToExistingFile(@TempDir final Path tempDir) throws IOException {
    final Path keysFile = Files.createTempFile(tempDir, "keys", ".yaml");
    assertKeysAreWritten(keysFile);
  }

  @Test
  void fileGetsCreatedAndKeysAreWritten(@TempDir final Path tempDir) throws IOException {
    final Path keysFile = tempDir.resolve("keys.yaml");
    assertKeysAreWritten(keysFile);
  }

  private void assertKeysAreWritten(final Path keysFile) {
    final KeysWriter keysWriter = new YamlKeysWriter(keysFile);

    keysWriter.writeKeys(new BLSKeyPair(validator1SecretKey), new BLSKeyPair(withdrawal1SecretKey));
    assertThat(contentOf(keysFile.toFile())).isEqualTo(yamlLine1);

    keysWriter.writeKeys(new BLSKeyPair(validator2SecretKey), new BLSKeyPair(withdrawal2SecretKey));
    assertThat(contentOf(keysFile.toFile())).isEqualTo(expectedYaml);
  }
}
