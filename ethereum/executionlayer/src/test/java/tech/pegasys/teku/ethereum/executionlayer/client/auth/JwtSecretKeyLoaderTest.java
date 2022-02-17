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
import static tech.pegasys.teku.ethereum.executionlayer.client.auth.JwtTestHelper.assertSecretEquals;

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
  void testGetSecretKey_ReadSecretFromProvidedFilePath(@TempDir final Path tempDir)
      throws IOException {
    final Path jwtSecretFile = tempDir.resolve(JwtSecretKeyLoader.JWT_SECRET_FILE_NAME);
    final SecretKeySpec jwtSecret = JwtTestHelper.generateJwtSecret();
    Files.writeString(jwtSecretFile, Bytes.wrap(jwtSecret.getEncoded()).toHexString());
    final JwtSecretKeyLoader keyLoader =
        new JwtSecretKeyLoader(Optional.of(jwtSecretFile.toString()), tempDir);
    final Key loadedSecret = keyLoader.getSecretKey();
    assertSecretEquals(jwtSecret, loadedSecret);
  }

  @Test
  void testGetSecretKey_KeyGenerationWhenFileNotProvided(@TempDir final Path tempDir) {
    final JwtSecretKeyLoader generatedKeyLoader = new JwtSecretKeyLoader(Optional.empty(), tempDir);
    final Key generatedSecret = generatedKeyLoader.getSecretKey();
    assertThat(generatedSecret).isNotNull();
    assertThat(Bytes.wrap(generatedSecret.getEncoded()).toHexString()).isNotBlank();
    final Path jwtSecretFile = tempDir.resolve(JwtSecretKeyLoader.JWT_SECRET_FILE_NAME);

    final JwtSecretKeyLoader fileKeyLoader =
        new JwtSecretKeyLoader(Optional.of(jwtSecretFile.toString()), tempDir);
    final Key loadedSecret = fileKeyLoader.getSecretKey();
    assertThat(loadedSecret).isNotNull();
    assertThat(Bytes.wrap(loadedSecret.getEncoded()).toHexString()).isNotBlank();
  }
}
