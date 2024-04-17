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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class DebugDataDumperTest {
  final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);

  @Test
  void saveGossipMessageDecodingError_shouldSaveToFile(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir);
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final Optional<UInt64> arrivalTimestamp = Optional.of(timeProvider.getTimeInMillis());
    manager.saveGossipMessageDecodingError(
        "/eth/test/topic", arrivalTimestamp, () -> messageBytes, new Throwable());

    final String fileName =
        String.format("%s.ssz", formatTimestamp(timeProvider.getTimeInMillis().longValue()));
    final Path expectedFile =
        tempDir
            .resolve("gossip_messages")
            .resolve("decoding_error")
            .resolve("_eth_test_topic")
            .resolve(fileName);
    checkBytesSavedToFile(expectedFile, messageBytes);
  }

  @Test
  void saveGossipRejectedMessageToFile_shouldSaveToFile(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir);
    final Bytes messageBytes = dataStructureUtil.stateBuilderPhase0().build().sszSerialize();
    final Optional<UInt64> arrivalTimestamp = Optional.of(timeProvider.getTimeInMillis());
    manager.saveGossipRejectedMessageToFile(
        "/eth/test/topic", arrivalTimestamp, () -> messageBytes, Optional.of("reason"));

    final String fileName =
        String.format("%s.ssz", formatTimestamp(timeProvider.getTimeInMillis().longValue()));
    final Path expectedFile =
        tempDir
            .resolve("gossip_messages")
            .resolve("rejected")
            .resolve("_eth_test_topic")
            .resolve(fileName);
    checkBytesSavedToFile(expectedFile, messageBytes);
  }

  @Test
  void saveInvalidBlockToFile_shouldSaveToFile(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    manager.saveInvalidBlockToFile(block, "reason", Optional.of(new Throwable()));

    final String fileName =
        String.format("%s_%s.ssz", block.getSlot(), block.getRoot().toUnprefixedHexString());
    final Path expectedFile = tempDir.resolve("invalid_blocks").resolve(fileName);
    checkBytesSavedToFile(expectedFile, block.sszSerialize());
  }

  @Test
  void saveBytesToFile_shouldNotThrowExceptionWhenNoDirectory(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir);
    assertDoesNotThrow(
        () -> {
          final boolean success =
              manager.saveBytesToFile(
                  "object", Path.of("invalid").resolve("file.ssz"), Bytes.EMPTY);
          assertThat(success).isTrue(); // creates directory
        });
  }

  @Test
  @DisabledOnOs(OS.WINDOWS) // Can't set permissions on Windows
  void saveBytesToFile_shouldNotEscalateWhenIOException(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir);
    final File invalidPath = tempDir.resolve("invalid").toFile();
    assertThat(invalidPath.mkdirs()).isTrue();
    assertThat(invalidPath.setWritable(false)).isTrue();
    assertDoesNotThrow(
        () -> {
          final boolean success =
              manager.saveBytesToFile(
                  "object", Path.of("invalid").resolve("file.ssz"), Bytes.EMPTY);
          assertThat(success).isFalse();
        });
  }

  @Test
  @DisabledOnOs(OS.WINDOWS) // Can't set permissions on Windows
  void constructionOfDirectories_shouldDisableWhenFailedToCreate(@TempDir Path tempDir) {
    assertThat(tempDir.toFile().setWritable(false)).isTrue();
    final DebugDataDumper manager = new DebugDataDumper(tempDir);
    assertThat(manager.isEnabled()).isFalse();
  }

  @Test
  void formatOptionalTimestamp_shouldFormatTimestamp(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir);
    final String formattedTimestamp =
        manager.formatOptionalTimestamp(Optional.of(timeProvider.getTimeInMillis()), timeProvider);
    assertThat(formattedTimestamp)
        .isEqualTo(formatTimestamp(timeProvider.getTimeInMillis().longValue()));
  }

  @Test
  void formatOptionalTimestamp_shouldGenerateTimestamp(@TempDir Path tempDir) {
    final DebugDataDumper manager = new DebugDataDumper(tempDir);
    final String formattedTimestamp =
        manager.formatOptionalTimestamp(Optional.empty(), timeProvider);
    assertThat(formattedTimestamp)
        .isEqualTo(formatTimestamp(timeProvider.getTimeInMillis().longValue()));
  }

  private void checkBytesSavedToFile(final Path path, final Bytes expectedBytes) {
    try {
      final Bytes bytes = Bytes.wrap(Files.readAllBytes(path));
      assertThat(bytes).isEqualTo(expectedBytes);
    } catch (IOException e) {
      fail();
    }
  }

  private String formatTimestamp(final long timeInMillis) {
    final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH_mm_ss.SS");
    final Date date = new Date(timeInMillis);
    return df.format(date);
  }
}
