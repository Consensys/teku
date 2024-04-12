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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DebugDataDumper {
  private static final Logger LOG = LogManager.getLogger();

  private static final String GOSSIP_MESSAGES_DIR = "gossip_messages";
  private static final String DECODING_ERROR_SUB_DIR = "decoding_error";
  private static final String REJECTED_SUB_DIR = "rejected";
  private static final String INVALID_BLOCK_DIR = "invalid_blocks";

  private boolean enabled;
  private final Path directory;

  public DebugDataDumper(final Path directory, final boolean enabled) {
    this.enabled = enabled;
    this.directory = directory;
    if (!enabled) {
      return;
    }

    final Path gossipMessagesPath = this.directory.resolve(GOSSIP_MESSAGES_DIR);
    createDirectory(gossipMessagesPath, GOSSIP_MESSAGES_DIR, "gossip messages");
    createDirectory(
        gossipMessagesPath.resolve(DECODING_ERROR_SUB_DIR),
        DECODING_ERROR_SUB_DIR,
        "gossip messages with decoding errors");
    createDirectory(
        gossipMessagesPath.resolve(REJECTED_SUB_DIR), REJECTED_SUB_DIR, "rejected gossip messages");
    createDirectory(this.directory.resolve(INVALID_BLOCK_DIR), INVALID_BLOCK_DIR, "invalid blocks");
  }

  public void saveGossipMessageDecodingError(
      final String topic, final Optional<UInt64> arrivalTimestamp, final Bytes originalMessage) {
    if (!enabled) {
      return;
    }
    final String fileName = String.format("%s.ssz", formatTimestamp(arrivalTimestamp));
    final Path topicPath =
        Path.of(GOSSIP_MESSAGES_DIR).resolve(DECODING_ERROR_SUB_DIR).resolve(topic);
    saveBytesToFile(
        "gossip message with decoding error", topicPath.resolve(fileName), originalMessage);
  }

  public void saveGossipRejectedMessageToFile(
      final String topic, final Optional<UInt64> arrivalTimestamp, final Bytes decodedMessage) {
    if (!enabled) {
      return;
    }
    final String fileName = String.format("%s.ssz", formatTimestamp(arrivalTimestamp));
    final Path topicPath = Path.of(GOSSIP_MESSAGES_DIR).resolve(REJECTED_SUB_DIR).resolve(topic);
    saveBytesToFile("rejected gossip message", topicPath.resolve(fileName), decodedMessage);
  }

  public void saveInvalidBlockToFile(
      final UInt64 slot, final Bytes32 blockRoot, final Bytes blockSsz) {
    if (!enabled) {
      return;
    }
    final String fileName = String.format("%s_%s.ssz", slot, blockRoot.toUnprefixedHexString());
    saveBytesToFile("invalid block", Path.of(INVALID_BLOCK_DIR).resolve(fileName), blockSsz);
  }

  @VisibleForTesting
  protected void saveBytesToFile(
      final String description, final Path relativeFilePath, final Bytes bytes) {
    final Path path = directory.resolve(relativeFilePath);
    try {
      Files.write(path, bytes.toArray());
      LOG.info("Saved {} bytes to file. Location: {}", description, relativeFilePath);
    } catch (NoSuchFileException e) {
      if (!path.getParent().toFile().mkdirs()) {
        LOG.error("Failed to save {} bytes to file.", description, e);
        return;
      }
      saveAfterCreatingTopicDirectory(description, relativeFilePath, bytes);
    } catch (IOException e) {
      LOG.error("Failed to save {} bytes to file.", description, e);
    }
  }

  private void saveAfterCreatingTopicDirectory(
      final String description, final Path relativeFilePath, final Bytes bytes) {
    final Path path = directory.resolve(relativeFilePath);
    try {
      Files.write(path, bytes.toArray());
      LOG.info("Saved {} bytes to file. Location: {}", description, relativeFilePath);
    } catch (IOException e) {
      LOG.error("Failed to save {} bytes to file.", description, e);
      if (!path.getParent().toFile().exists()) {
        LOG.error(
            "{} directory does not exist. Disabling saving debug data to file.",
            relativeFilePath.getParent());
      }
    }
  }

  private void createDirectory(
      final Path path, final String directoryName, final String description) {
    if (!enabled) {
      return;
    }
    if (path.toFile().mkdirs()) {
      LOG.debug("{} directory has been created to save {}.", directoryName, description);
    } else {
      if (!path.toFile().exists()) {
        this.enabled = false;
        LOG.error(
            "Unable to create {} directory to save {}. Disabling saving debug data to file.",
            directoryName,
            description);
      }
    }
  }

  @VisibleForTesting
  static String formatTimestamp(final Optional<UInt64> arrivalTimestamp) {
    if (arrivalTimestamp.isEmpty()) {
      return "unknown";
    }

    final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS");
    final Date date = new Date(arrivalTimestamp.get().longValue());
    return df.format(date);
  }

  @VisibleForTesting
  boolean isEnabled() {
    return enabled;
  }
}
