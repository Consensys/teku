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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class P2PDumpManagerTest {
  final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);

  @Test
  void saveGossipMessageDecodingError_shouldSaveToFile(@TempDir Path tempDir) {
    final P2PDumpManager manager = new P2PDumpManager(tempDir);
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final String arrivalTimestamp = timeProvider.getTimeInMillis().toString();
    final String file =
        manager.saveGossipMessageDecodingError("test_topic", arrivalTimestamp, messageBytes);

    final String fileName = String.format("%s_%s.ssz", arrivalTimestamp, "test_topic");
    final Path expectedFile = tempDir.resolve("gossip_decoding_error_messages").resolve(fileName);
    assertThat(file).isEqualTo(expectedFile.toString());
    checkBytesSavedToFile(expectedFile, messageBytes);
  }

  @Test
  void saveGossipMessageDecodingError_failsToCreateFile(@TempDir Path tempDir) throws IOException {
    final P2PDumpManager manager = new P2PDumpManager(tempDir);
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final String arrivalTimestamp = timeProvider.getTimeInMillis().toString();

    // Make file with expected name
    final String fileName = String.format("%s_%s.ssz", arrivalTimestamp, "test_topic");
    final Path path = tempDir.resolve("gossip_decoding_error_messages").resolve(fileName);
    assertThat(path.toFile().createNewFile()).isTrue();

    // Should not be able to create file when exists
    final String file =
        manager.saveGossipMessageDecodingError("test_topic", arrivalTimestamp, messageBytes);
    assertThat(file).isEqualTo("error");
    checkBytesSavedToFile(path, Bytes.EMPTY);
  }

  @Test
  void saveGossipRejectedMessageToFile_shouldSaveToFile(@TempDir Path tempDir) {
    final P2PDumpManager manager = new P2PDumpManager(tempDir);
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final String arrivalTimestamp = timeProvider.getTimeInMillis().toString();
    final String file =
        manager.saveGossipRejectedMessageToFile("test_topic", arrivalTimestamp, messageBytes);

    final String fileName = String.format("%s_%s.ssz", arrivalTimestamp, "test_topic");
    final Path expectedFile = tempDir.resolve("rejected_gossip_messages").resolve(fileName);
    assertThat(file).isEqualTo(expectedFile.toString());
    checkBytesSavedToFile(expectedFile, messageBytes);
  }

  @Test
  void saveGossipRejectedMessageToFile_failsToCreateFile(@TempDir Path tempDir) throws IOException {
    final P2PDumpManager manager = new P2PDumpManager(tempDir);
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final String arrivalTimestamp = timeProvider.getTimeInMillis().toString();

    // Make file with expected name
    final String fileName = String.format("%s_%s.ssz", arrivalTimestamp, "test_topic");
    final Path path = tempDir.resolve("rejected_gossip_messages").resolve(fileName);
    assertThat(path.toFile().createNewFile()).isTrue();

    // Should not be able to create file when exists
    final String file =
        manager.saveGossipRejectedMessageToFile("test_topic", arrivalTimestamp, messageBytes);
    assertThat(file).isEqualTo("error");
    checkBytesSavedToFile(path, Bytes.EMPTY);
  }

  @Test
  void saveInvalidBlockToFile_shouldSaveToFile(@TempDir Path tempDir) {
    final P2PDumpManager manager = new P2PDumpManager(tempDir);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final String file =
        manager.saveInvalidBlockToFile(block.getSlot(), block.getRoot(), block.sszSerialize());

    final String fileName =
        String.format(
            "slot%s_root%s.ssz", block.getSlot(), block.getRoot().toUnprefixedHexString());
    final Path expectedFile = tempDir.resolve("invalid_blocks").resolve(fileName);
    assertThat(file).isEqualTo(expectedFile.toString());
    checkBytesSavedToFile(expectedFile, block.sszSerialize());
  }

  @Test
  void saveInvalidBlockToFile_failsToCreateFile(@TempDir Path tempDir) throws IOException {
    final P2PDumpManager manager = new P2PDumpManager(tempDir);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    // Make file with expected name
    final String fileName =
        String.format(
            "slot%s_root%s.ssz", block.getSlot(), block.getRoot().toUnprefixedHexString());
    final Path path = tempDir.resolve("invalid_blocks").resolve(fileName);
    assertThat(path.toFile().createNewFile()).isTrue();

    // Should not be able to create file when exists
    final String file =
        manager.saveInvalidBlockToFile(block.getSlot(), block.getRoot(), block.sszSerialize());
    assertThat(file).isEqualTo("error");
    checkBytesSavedToFile(path, Bytes.EMPTY);
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
