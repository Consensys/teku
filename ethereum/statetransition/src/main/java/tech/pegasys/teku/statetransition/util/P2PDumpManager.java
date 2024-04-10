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

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class P2PDumpManager {
  private static final Logger LOG = LogManager.getLogger();

  private static final String GOSSIP_DECODING_ERROR_DIR = "gossip_decoding_error_messages";
  private static final String GOSSIP_REJECTED_DIR = "rejected_gossip_messages";
  private static final String INVALID_BLOCK_DIR = "invalid_blocks";

  private final Path directory;

  public P2PDumpManager(final Path directory) {
    this.directory = directory;
    if (this.directory.resolve(GOSSIP_DECODING_ERROR_DIR).toFile().mkdir()) {
      LOG.info(
          String.format(
              "%s directory has been created to save gossip messages with decoding errors.",
              GOSSIP_DECODING_ERROR_DIR));
    }
    if (this.directory.resolve(GOSSIP_REJECTED_DIR).toFile().mkdir()) {
      LOG.info(
          String.format(
              "%s directory has been created to save rejected gossip messages.",
              GOSSIP_REJECTED_DIR));
    }
    if (this.directory.resolve(INVALID_BLOCK_DIR).toFile().mkdir()) {
      LOG.info(
          String.format(
              "%s directory has been created to save invalid blocks.", INVALID_BLOCK_DIR));
    }
  }

  public String saveGossipMessageDecodingError(
      final String topic, final String arrivalTimestamp, final Bytes originalMessage) {
    final String fileName = String.format("%s_%s.ssz", arrivalTimestamp, topic);
    final String identifiers = String.format("Topic: %s", topic);
    return saveBytesToFile(
        "gossip message with decoding error",
        GOSSIP_DECODING_ERROR_DIR,
        fileName,
        identifiers,
        originalMessage);
  }

  public String saveGossipRejectedMessageToFile(
      final String topic, final String arrivalTimestamp, final Bytes decodedMessage) {
    final String fileName = String.format("%s_%s.ssz", arrivalTimestamp, topic);
    final String identifiers = String.format("Topic: %s", topic);
    return saveBytesToFile(
        "rejected gossip message", GOSSIP_REJECTED_DIR, fileName, identifiers, decodedMessage);
  }

  public String saveInvalidBlockToFile(
      final UInt64 slot, final Bytes32 blockRoot, final Bytes blockSsz) {
    final String fileName =
        String.format("slot%s_root%s.ssz", slot, blockRoot.toUnprefixedHexString());
    final String identifiers = String.format("Slot: %s, Block Root: %s", slot, blockRoot);
    return saveBytesToFile("invalid block", INVALID_BLOCK_DIR, fileName, identifiers, blockSsz);
  }

  private String saveBytesToFile(
      final String object,
      final String errorDirectory,
      final String fileName,
      final String identifiers,
      final Bytes bytes) {
    final Path path = directory.resolve(errorDirectory).resolve(fileName);
    try {
      // Create file and save ssz
      if (!path.toFile().createNewFile()) {
        final String errorMessage =
            String.format("Unable to create new file to save %s. %s", object, identifiers);
        throw new FileAlreadyExistsException(errorMessage);
      }
      final Path writtenPath = Files.write(path, bytes.toArray());
      return writtenPath.toString();

    } catch (IOException e) {
      final String errorMessage =
          String.format("Failed to save %s bytes to file. %s", object, identifiers);
      LOG.error(errorMessage, e);
      return "ERROR saving to file";
    }
  }
}
