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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class DebugDataDumperTest {
  final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);

  @Test
  void saveGossipMessageDecodingError_shouldSaveToFile(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir, true);
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final String arrivalTimestamp = timeProvider.getTimeInMillis().toString();
    final String topic = "test_topic";
    manager.saveGossipMessageDecodingError("test_topic", arrivalTimestamp, messageBytes);

    final String fileName = String.format("%s_%s.ssz", arrivalTimestamp, topic);
    final Path expectedFile =
        tempDir
            .resolve("gossip_messages")
            .resolve("decoding_error")
            .resolve(topic)
            .resolve(fileName);
    checkBytesSavedToFile(expectedFile, messageBytes);
  }

  @Test
  void saveGossipMessageDecodingError_shouldNotSaveToFileWhenDisabled(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir, false);
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final String arrivalTimestamp = timeProvider.getTimeInMillis().toString();
    manager.saveGossipMessageDecodingError("test_topic", arrivalTimestamp, messageBytes);
    assertThat(manager.isEnabled()).isFalse();

    final String fileName = String.format("%s_%s.ssz", arrivalTimestamp, "test_topic");
    final Path expectedFile =
        tempDir.resolve("gossip_messages").resolve("decoding_error").resolve(fileName);
    checkFileNotExist(expectedFile);
  }

  @Test
  void saveGossipRejectedMessageToFile_shouldSaveToFile(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir, true);
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final String arrivalTimestamp = timeProvider.getTimeInMillis().toString();
    final String topic = "test_topic";
    manager.saveGossipRejectedMessageToFile("test_topic", arrivalTimestamp, messageBytes);

    final String fileName = String.format("%s_%s.ssz", arrivalTimestamp, topic);
    final Path expectedFile =
        tempDir.resolve("gossip_messages").resolve("rejected").resolve(topic).resolve(fileName);
    checkBytesSavedToFile(expectedFile, messageBytes);
  }

  @Test
  void saveGossipRejectedMessageToFile_shouldNotSaveToFileWhenDisabled(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir, false);
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final String arrivalTimestamp = timeProvider.getTimeInMillis().toString();
    manager.saveGossipRejectedMessageToFile("test_topic", arrivalTimestamp, messageBytes);
    assertThat(manager.isEnabled()).isFalse();

    final String fileName = String.format("%s_%s.ssz", arrivalTimestamp, "test_topic");
    final Path expectedFile =
        tempDir.resolve("gossip_messages").resolve("rejected").resolve(fileName);
    checkFileNotExist(expectedFile);
  }

  @Test
  void saveInvalidBlockToFile_shouldSaveToFile(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir, true);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    manager.saveInvalidBlockToFile(block.getSlot(), block.getRoot(), block.sszSerialize());

    final String fileName =
        String.format(
            "slot%s_root%s.ssz", block.getSlot(), block.getRoot().toUnprefixedHexString());
    final Path expectedFile = tempDir.resolve("invalid_blocks").resolve(fileName);
    checkBytesSavedToFile(expectedFile, block.sszSerialize());
  }

  @Test
  void saveInvalidBlockToFile_shouldNotSaveToFileWhenDisabled(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir, false);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    manager.saveInvalidBlockToFile(block.getSlot(), block.getRoot(), block.sszSerialize());
    assertThat(manager.isEnabled()).isFalse();

    final String fileName =
        String.format(
            "slot%s_root%s.ssz", block.getSlot(), block.getRoot().toUnprefixedHexString());
    final Path expectedFile = tempDir.resolve("invalid_blocks").resolve(fileName);
    checkFileNotExist(expectedFile);
  }

  @Test
  void saveBytesToFile_shouldNotThrowExceptionWhenUnableToSave(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir, true);
    assertDoesNotThrow(
        () ->
            manager.saveBytesToFile("object", Path.of("invalid").resolve("file.ssz"), Bytes.EMPTY));
  }

  private void checkBytesSavedToFile(final Path path, final Bytes expectedBytes) {
    try {
      final Bytes bytes = Bytes.wrap(Files.readAllBytes(path));
      assertThat(bytes).isEqualTo(expectedBytes);
    } catch (IOException e) {
      fail();
    }
  }

  private void checkFileNotExist(final Path path) {
    try {
      Bytes.wrap(Files.readAllBytes(path));
    } catch (IOException e) {
      if (e instanceof NoSuchFileException) {
        return;
      }
    }
    fail("File was found and bytes read");
  }
}
