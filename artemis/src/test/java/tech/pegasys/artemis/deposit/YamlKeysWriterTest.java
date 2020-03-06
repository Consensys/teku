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

package tech.pegasys.artemis.deposit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.eclipse.jetty.io.WriterOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.SecretKey;

class YamlKeysWriterTest {
  private static final SecretKey validator1SecretKey =
      SecretKey.fromBytes(
          Bytes.fromHexString(
              "0x000000000000000000000000000000002CF622DE0FD92C7D4E59539CBDA63100E02CF59349595356CD97FFE6CB486460"));
  private static final SecretKey withdrawal1SecretKey =
      SecretKey.fromBytes(
          Bytes.fromHexString(
              "0x000000000000000000000000000000006EA631AA885EC84AFA60BBD7887B5DBC91F594DEA29334E99576B51FAAD0E453"));
  private static final SecretKey validator2SecretKey =
      SecretKey.fromBytes(
          Bytes.fromHexString(
              "0x00000000000000000000000000000000147599AA450AADF69988F20FF1ADB3A3BF31BF9CDC77CF492FF95667708D8E79"));
  private static final SecretKey withdrawal2SecretKey =
      SecretKey.fromBytes(
          Bytes.fromHexString(
              "0x000000000000000000000000000000000610B84CD68FB0FAB2F04A2A05EE01CD5F7374EB8EA93E26DB9C61DD2704B5BD"));
  private static final String yamlLine1 =
      "- {privkey: '0x000000000000000000000000000000002CF622DE0FD92C7D4E59539CBDA63100E02CF59349595356CD97FFE6CB486460', "
          + "pubkey: '0xB21680D9E0AB6E92C034CAB6B6BCEF3EA9F3203D8FC7DFF4A17D978BF01B508F6D6852B067F9CC5ED82B462919E7DDE0', "
          + "withdrawalPrivkey: '0x000000000000000000000000000000006EA631AA885EC84AFA60BBD7887B5DBC91F594DEA29334E99576B51FAAD0E453', "
          + "withdrawalPubkey: '0xB00FA5FF2EAEE1B4354C402627ACB85A6720463E62210C663AD3619962E28EDD7E7422F69D4D229761C8F2EE6AD55B6F'}"
          + System.lineSeparator();
  private static final String yamlLine2 =
      "- {privkey: '0x00000000000000000000000000000000147599AA450AADF69988F20FF1ADB3A3BF31BF9CDC77CF492FF95667708D8E79', "
          + "pubkey: '0xA3A6F1FFCB5F2A577CC4AE4379C5492E02231869773AE9DE1DD0E18F9EC511F81ADC5980026AB3D6178D48DB17BEAE0C', "
          + "withdrawalPrivkey: '0x000000000000000000000000000000000610B84CD68FB0FAB2F04A2A05EE01CD5F7374EB8EA93E26DB9C61DD2704B5BD', "
          + "withdrawalPubkey: '0xAB70D135ED35279A636980845D94DC4B33FBC2A367EB75EF19BF0E28A2CD3C4025C1450F8D9D4D5EA9DC9AF4DD21F186'}"
          + System.lineSeparator();
  private final String expectedYaml = yamlLine1 + yamlLine2;

  @Test
  void keysAreWrittenOnSystemOutput() {
    final StringWriter stringWriter = new StringWriter();
    final KeysWriter keysWriter =
        new YamlKeysWriter(new PrintStream(new WriterOutputStream(stringWriter), true));
    keysWriter.writeKeys(
        new BLSKeyPair(new KeyPair(validator1SecretKey)),
        new BLSKeyPair(new KeyPair(withdrawal1SecretKey)));
    assertThat(stringWriter.toString()).isEqualTo(yamlLine1);

    keysWriter.writeKeys(
        new BLSKeyPair(new KeyPair(validator2SecretKey)),
        new BLSKeyPair(new KeyPair(withdrawal2SecretKey)));

    assertThat(stringWriter.toString()).isEqualTo(expectedYaml);
  }

  @Test
  void keysAreWrittenToFile(@TempDir final Path tempDir) throws IOException {
    final Path keysFile = Files.createTempFile(tempDir, "keys", ".yaml");
    final KeysWriter keysWriter = new YamlKeysWriter(keysFile);

    keysWriter.writeKeys(
        new BLSKeyPair(new KeyPair(validator1SecretKey)),
        new BLSKeyPair(new KeyPair(withdrawal1SecretKey)));
    assertThat(contentOf(keysFile.toFile())).isEqualTo(yamlLine1);

    keysWriter.writeKeys(
        new BLSKeyPair(new KeyPair(validator2SecretKey)),
        new BLSKeyPair(new KeyPair(withdrawal2SecretKey)));
    assertThat(contentOf(keysFile.toFile())).isEqualTo(expectedYaml);
  }
}
