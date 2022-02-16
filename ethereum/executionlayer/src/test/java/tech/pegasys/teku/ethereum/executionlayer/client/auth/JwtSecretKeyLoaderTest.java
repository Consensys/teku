/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionlayer.client.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtSecretKeyLoaderTest {
  @Test
  void testGetSecretKey_FromProvidedFilePath(@TempDir final Path tempDir) throws IOException {
    final String fileName = "jwt.hex";
    final Path jwtSecretFile = tempDir.resolve(fileName);
    final SecretKeySpec jwtSecret = TestHelper.generateJwtSecret();
    Files.writeString(jwtSecretFile, Bytes.wrap(jwtSecret.getEncoded()).toHexString());
    final JwtSecretKeyLoader keyLoader =
        new JwtSecretKeyLoader(Optional.of(jwtSecretFile.toString()));
    final Key loadedSecret = keyLoader.getSecretKey();
    assertThat(TestHelper.secretEquals(jwtSecret, loadedSecret)).isTrue();
  }

  @Test
  void testGetSecretKey_KeyGeneration() {
    final JwtSecretKeyLoader keyLoader = new JwtSecretKeyLoader(Optional.empty());
    final Key loadedSecret = keyLoader.getSecretKey();
    assertThat(loadedSecret).isNotNull();
    assertThat(Bytes.wrap(loadedSecret.getEncoded()).toHexString()).isNotBlank();
  }
}
