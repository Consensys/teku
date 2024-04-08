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
import java.time.Instant;
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
  void saveGossipMessageDecodingError_shouldSaveToFile(@TempDir Path tempDir) {
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final String timestamp = Instant.now().toString();
    final Optional<String> file =
        P2PDumpManager.saveGossipMessageDecodingError(
            tempDir, "test_topic", timestamp, messageBytes);
    assertThat(file).isPresent();

    final String filename = String.format("%s_%s.ssz", timestamp, "test_topic");
    final Path expectedFile = tempDir.resolve("gossip_decoding_error_messages").resolve(filename);
    assertThat(file.get()).isEqualTo(expectedFile.toString());
    checkBytesSavedToFile(expectedFile, messageBytes);
  }

  @Test
  void saveGossipMessageDecodingError_failsToCreateFile(@TempDir Path tempDir) throws IOException {
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final String timestamp = Instant.now().toString();
    final Path decodingErrorMessagesDir = tempDir.resolve("gossip_decoding_error_messages");

    // Make invalid_blocks directory
    assertThat(decodingErrorMessagesDir.toFile().mkdir()).isTrue();

    // Make file with expected name
    final String filename = String.format("%s_%s.ssz", timestamp, "test_topic");
    assertThat(decodingErrorMessagesDir.resolve(filename).toFile().createNewFile()).isTrue();

    // Should not be able to create file when exists
    final Optional<String> file =
        P2PDumpManager.saveGossipMessageDecodingError(
            tempDir, "test_topic", timestamp, messageBytes);
    assertThat(file).isEmpty();
    checkBytesSavedToFile(decodingErrorMessagesDir.resolve(filename), Bytes.EMPTY);
  }

  @Test
  void saveGossipRejectedMessageToFile_shouldSaveToFile(@TempDir Path tempDir) {
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final String timestamp = Instant.now().toString();
    final Optional<String> file =
        P2PDumpManager.saveGossipRejectedMessageToFile(
            tempDir, "test_topic", timestamp, messageBytes);
    assertThat(file).isPresent();

    final String filename = String.format("%s_%s.ssz", timestamp, "test_topic");
    final Path expectedFile = tempDir.resolve("rejected_gossip_messages").resolve(filename);
    assertThat(file.get()).isEqualTo(expectedFile.toString());
    checkBytesSavedToFile(expectedFile, messageBytes);
  }

  @Test
  void saveGossipRejectedMessageToFile_failsToCreateFile(@TempDir Path tempDir) throws IOException {
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final String timestamp = Instant.now().toString();
    final Path rejectedGossipMessagesDir = tempDir.resolve("rejected_gossip_messages");

    // Make invalid_blocks directory
    assertThat(rejectedGossipMessagesDir.toFile().mkdir()).isTrue();

    // Make file with expected name
    final String filename = String.format("%s_%s.ssz", timestamp, "test_topic");
    assertThat(rejectedGossipMessagesDir.resolve(filename).toFile().createNewFile()).isTrue();

    // Should not be able to create file when exists
    final Optional<String> file =
        P2PDumpManager.saveGossipRejectedMessageToFile(
            tempDir, "test_topic", timestamp, messageBytes);
    assertThat(file).isEmpty();
    checkBytesSavedToFile(rejectedGossipMessagesDir.resolve(filename), Bytes.EMPTY);
  }

  @Test
  void saveInvalidBlockToFile_shouldSaveToFile(@TempDir Path tempDir) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final Optional<String> file =
        P2PDumpManager.saveInvalidBlockToFile(
            tempDir, block.getSlot(), block.getRoot(), block.sszSerialize());
    assertThat(file).isPresent();

    final String filename =
        String.format(
            "slot%s_root%s.ssz", block.getSlot(), block.getRoot().toUnprefixedHexString());
    final Path expectedFile = tempDir.resolve("invalid_blocks").resolve(filename);
    assertThat(file.get()).isEqualTo(expectedFile.toString());
    checkBytesSavedToFile(expectedFile, block.sszSerialize());
  }

  @Test
  void saveInvalidBlockToFile_failsToCreateFile(@TempDir Path tempDir) throws IOException {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final Path invalidBlocksDir = tempDir.resolve("invalid_blocks");

    // Make invalid_blocks directory
    assertThat(invalidBlocksDir.toFile().mkdir()).isTrue();

    // Make file with expected name
    final String filename =
        String.format(
            "slot%s_root%s.ssz", block.getSlot(), block.getRoot().toUnprefixedHexString());
    assertThat(invalidBlocksDir.resolve(filename).toFile().createNewFile()).isTrue();

    // Should not be able to create file when exists
    final Optional<String> file =
        P2PDumpManager.saveInvalidBlockToFile(
            tempDir, block.getSlot(), block.getRoot(), block.sszSerialize());
    assertThat(file).isEmpty();
    checkBytesSavedToFile(invalidBlocksDir.resolve(filename), Bytes.EMPTY);
  }

  private void checkBytesSavedToFile(final Path path, final Bytes expectedBytes) {
    try {
      final Bytes bytes = Bytes.wrap(Files.readAllBytes(path));
      assertThat(bytes).isEqualTo(expectedBytes);
    } catch (IOException e) {
      fail();
    }
  }
}
