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

package tech.pegasys.teku.validator.client.loader;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.util.config.TekuConfiguration;

class YamlValidatorKeyProviderTest {
  private final YamlValidatorKeyProvider provider = new YamlValidatorKeyProvider();
  private final TekuConfiguration config = mock(TekuConfiguration.class);

  @Test
  public void shouldLoadExampleFile(@TempDir Path tempDirectory) throws Exception {
    final Path tempFile =
        writeTestFile(
            tempDirectory,
            "- {privkey: '0x25295f0d1d592a90b333e26e85149708208e9f8e8bc18f6c77bd62f8ad7a6866',\n"
                + "  pubkey: '0xa99a76ed7796f7be22d5b7e85deeb7c5677e88e511e0b337618f8c4eb61349b4bf2d153f649f7b53359fe8b94a38e44c'}\n"
                + "- {privkey: '0x51d0b65185db6989ab0b560d6deed19c7ead0e24b9b6372cbecb1f26bdfad000',\n"
                + "  pubkey: '0xb89bebc699769726a318c8e9971bd3171297c61aea4a6578a7a4f94b547dcba5bac16a89108b6b6a1fe3695d1a874a0b'}\n"
                + "- {privkey: '0x315ed405fafe339603932eebe8dbfd650ce5dafa561f6928664c75db85f97857',\n"
                + "  pubkey: '0xa3a32b0f8b4ddb83f1a0a853d81dd725dfe577d4f4c3db8ece52ce2b026eca84815c1a7e8e92a4de3d755733bf7e4a9b'}");

    final List<String> EXPECTED_PRIVATE_KEYS =
        asList(
            "0x0000000000000000000000000000000025295f0d1d592a90b333e26e85149708208e9f8e8bc18f6c77bd62f8ad7a6866",
            "0x0000000000000000000000000000000051d0b65185db6989ab0b560d6deed19c7ead0e24b9b6372cbecb1f26bdfad000",
            "0x00000000000000000000000000000000315ed405fafe339603932eebe8dbfd650ce5dafa561f6928664c75db85f97857");

    when(config.getValidatorsKeyFile()).thenReturn(tempFile.toAbsolutePath().toString());

    final List<BLSKeyPair> keys = provider.loadValidatorKeys(config);
    final List<String> actualPrivateKeys =
        keys.stream()
            .map(keypair -> keypair.getSecretKey().getSecretKey().toBytes().toHexString())
            .collect(Collectors.toList());

    assertEquals(EXPECTED_PRIVATE_KEYS, actualPrivateKeys);
  }

  @Test
  public void shouldThrowErrorIfUnableToMapJson(@TempDir Path tempDirectory) throws IOException {
    final String testFile = "- {: 'has no key!'";
    final Path tempFile = writeTestFile(tempDirectory, testFile);

    when(config.getValidatorsKeyFile()).thenReturn(tempFile.toAbsolutePath().toString());

    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> provider.loadValidatorKeys(config))
        .withMessageContaining("Error while reading validator keys file values");
  }

  @Test
  public void shouldThrowErrorIfNoPrivKey(@TempDir Path tempDirectory) throws IOException {
    final String testFile =
        "- {privkey: ,\n"
            + "  pubkey: '0xa99a76ed7796f7be22d5b7e85deeb7c5677e88e511e0b337618f8c4eb61349b4bf2d153f649f7b53359fe8b94a38e44c'}\n";
    final Path tempFile = writeTestFile(tempDirectory, testFile);

    when(config.getValidatorsKeyFile()).thenReturn(tempFile.toAbsolutePath().toString());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.loadValidatorKeys(config))
        .withMessageContaining(
            "Invalid private key supplied.  Please check your validator keys configuration file");
  }

  private Path writeTestFile(final Path tempDirectory, final String contents) throws IOException {
    final Path tempFile = tempDirectory.resolve("keys.yaml");
    Files.writeString(tempFile, contents);
    return tempFile;
  }
}
