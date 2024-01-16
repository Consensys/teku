/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.networking.p2p.network.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.network.p2p.jvmlibp2p.PrivateKeyGenerator;

class FilePrivateKeySourceTest {

  @Test
  void shouldCreateKeyAndSaveToFile(@TempDir Path tempDir) throws IOException {
    final Path file = tempDir.resolve("file.txt");
    final PrivateKeySource privKeySource = new FilePrivateKeySource(file.toString());
    final Bytes generatedBytes = privKeySource.getOrGeneratePrivateKeyBytes();
    final Bytes savedBytes = Bytes.fromHexString(Files.readString(file));

    assertThat(generatedBytes).isEqualTo(savedBytes);
  }

  @Test
  void shouldGetKeyFromSavedFile(@TempDir Path tempDir) throws IOException {
    final Path file = tempDir.resolve("file.txt");
    final Bytes privateKey = Bytes.wrap(PrivateKeyGenerator.generate().bytes());
    Files.writeString(file, privateKey.toHexString());
    final PrivateKeySource privKeySource = new FilePrivateKeySource(file.toString());

    assertThat(privKeySource.getOrGeneratePrivateKeyBytes()).isEqualTo(privateKey);
  }
}
