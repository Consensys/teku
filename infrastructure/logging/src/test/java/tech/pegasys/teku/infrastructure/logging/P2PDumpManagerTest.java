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

package tech.pegasys.teku.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class P2PDumpManagerTest {
  final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  void saveInvalidBlockToFile_shouldSaveToFile(@TempDir Path tempDir) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final Optional<String> file =
        P2PDumpManager.saveInvalidBlockToFile(
            tempDir, block.getSlot(), block.getRoot(), block.sszSerialize());
    assertThat(file).isPresent();

    final Path expectedFile =
        tempDir
            .resolve("invalid_blocks")
            .resolve(String.format("slot%s_root%s.ssz", block.getSlot(), block.getRoot()));
    assertThat(file.get()).isEqualTo(expectedFile.toString());
    checkBlockSavedToFile(expectedFile, block.sszSerialize());
  }

  @Test
  void saveInvalidBlockToFile_failsToCreateFile(@TempDir Path tempDir) throws IOException {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final Path invalidBlocksDir = tempDir.resolve("invalid_blocks");

    // Make invalid_blocks directory
    assertThat(invalidBlocksDir.toFile().mkdir()).isTrue();

    // Make file with expected name
    final String fileName = String.format("slot%s_root%s.ssz", block.getSlot(), block.getRoot());
    assertThat(invalidBlocksDir.resolve(fileName).toFile().createNewFile()).isTrue();

    // Should not be able to create file when exists
    final Optional<String> file =
        P2PDumpManager.saveInvalidBlockToFile(
            tempDir, block.getSlot(), block.getRoot(), block.sszSerialize());
    assertThat(file).isEmpty();
    checkBlockSavedToFile(invalidBlocksDir.resolve(fileName), Bytes.EMPTY);
  }

  private void checkBlockSavedToFile(final Path path, final Bytes expectedBytes) {
    try {
      final Bytes bytes = Bytes.wrap(Files.readAllBytes(path));
      assertThat(bytes).isEqualTo(expectedBytes);
    } catch (IOException e) {
      fail();
    }
  }
}
