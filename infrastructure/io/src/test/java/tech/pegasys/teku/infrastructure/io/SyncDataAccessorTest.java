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

package tech.pegasys.teku.infrastructure.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class SyncDataAccessorTest {

  @Test
  public void shouldFailToMoveFile(@TempDir Path tempDir) throws IOException {
    Set<PosixFilePermission> perms = new HashSet<>();
    perms.add(PosixFilePermission.OWNER_READ);
    perms.add(PosixFilePermission.OWNER_EXECUTE);

    Files.setPosixFilePermissions(tempDir, perms);

    assertThatThrownBy(
            () -> {
              SyncDataAccessor.create(tempDir);
            })
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Cannot write to folder");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldMoveFile(final boolean useAtomicMove, @TempDir Path tempDir)
      throws IOException {
    final Path filePath = Paths.get(tempDir.toString() + "/myfile.tmp");
    assertThat(Files.exists(filePath)).isFalse();
    final SyncDataAccessor syncDataAccessor = new SyncDataAccessor(useAtomicMove);
    syncDataAccessor.syncedWrite(filePath, Bytes.fromHexString("0x41"));
    assertThat(Files.exists(filePath)).isTrue();

    String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
    assertThat(content).isEqualTo("A");
  }
}
