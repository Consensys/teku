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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private final boolean enabled;
  private final Path directory;

  public DebugDataDumper(final Path directory, final boolean enabled) {
    this.enabled = enabled;
    this.directory = directory;
    if (!enabled) {
      return;
    }

    final Path gossipMessagesPath = this.directory.resolve(GOSSIP_MESSAGES_DIR);
    if (gossipMessagesPath.toFile().mkdirs()) {
      LOG.info("{} directory has been created to save gossip messages.", GOSSIP_MESSAGES_DIR);
    }
    if (gossipMessagesPath.resolve(DECODING_ERROR_SUB_DIR).toFile().mkdirs()) {
      LOG.info(
          "{} directory has been created to save gossip messages with decoding errors.",
          DECODING_ERROR_SUB_DIR);
    }
    if (gossipMessagesPath.resolve(REJECTED_SUB_DIR).toFile().mkdirs()) {
      LOG.info("{} directory has been created to save rejected gossip messages.", REJECTED_SUB_DIR);
    }
    if (this.directory.resolve(INVALID_BLOCK_DIR).toFile().mkdirs()) {
      LOG.info("{} directory has been created to save invalid blocks.", INVALID_BLOCK_DIR);
    }
  }

  public void saveGossipMessageDecodingError(
      final String topic, final String arrivalTimestamp, final Bytes originalMessage) {
    if (!enabled) {
      return;
    }
    final String fileName = String.format("%s_%s.ssz", arrivalTimestamp, topic);
    final String identifiers = String.format("Topic: %s", topic);
    final Path topicPath =
        Path.of(GOSSIP_MESSAGES_DIR).resolve(DECODING_ERROR_SUB_DIR).resolve(topic);
    checkTopicDirExists(topicPath);
    saveBytesToFile(
        "gossip message with decoding error",
        identifiers,
        topicPath.resolve(fileName),
        originalMessage);
  }

  public void saveGossipRejectedMessageToFile(
      final String topic, final String arrivalTimestamp, final Bytes decodedMessage) {
    if (!enabled) {
      return;
    }
    final String fileName = String.format("%s_%s.ssz", arrivalTimestamp, topic);
    final String identifiers = String.format("Topic: %s", topic);
    final Path topicPath = Path.of(GOSSIP_MESSAGES_DIR).resolve(REJECTED_SUB_DIR).resolve(topic);
    checkTopicDirExists(topicPath);
    saveBytesToFile(
        "rejected gossip message", identifiers, topicPath.resolve(fileName), decodedMessage);
  }

  public void saveInvalidBlockToFile(
      final UInt64 slot, final Bytes32 blockRoot, final Bytes blockSsz) {
    if (!enabled) {
      return;
    }
    final String fileName =
        String.format("slot%s_root%s.ssz", slot, blockRoot.toUnprefixedHexString());
    final String identifiers = String.format("Slot: %s, Block Root: %s", slot, blockRoot);
    saveBytesToFile(
        "invalid block", identifiers, Path.of(INVALID_BLOCK_DIR).resolve(fileName), blockSsz);
  }

  private void saveBytesToFile(
      final String object,
      final String identifiers,
      final Path relativeFilePath,
      final Bytes bytes) {
    final Path path = directory.resolve(relativeFilePath);
    try {
      Files.write(path, bytes.toArray());
      LOG.info("Saved {} bytes to file. {}. Location: {}", object, identifiers, relativeFilePath);
    } catch (IOException e) {
      LOG.error("Failed to save {} bytes to file. {}", object, identifiers, e);
    }
  }

  private void checkTopicDirExists(final Path topicDir) {
    final File topicDirFile = directory.resolve(topicDir).toFile();
    if (topicDirFile.exists()) {
      return;
    }
    if (!topicDirFile.mkdirs()) {
      LOG.debug("Failed to create topic directory: {}", topicDir);
    }
  }

  @VisibleForTesting
  protected boolean isEnabled() {
    return enabled;
  }
}
