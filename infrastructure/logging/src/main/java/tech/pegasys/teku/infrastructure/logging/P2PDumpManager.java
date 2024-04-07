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

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class P2PDumpManager {
  private static final Logger LOG = LogManager.getLogger();

  private static final String INVALID_BLOCK_DIR = "invalid_blocks";

  public static Optional<String> saveInvalidBlockToFile(
      final Path directory, final UInt64 slot, final Bytes32 blockRoot, final Bytes blockSsz) {
    // Check directory exists
    final Path invalidBlocksDir = directory.resolve(INVALID_BLOCK_DIR);
    if (invalidBlocksDir.toFile().mkdir()) {
      LOG.info(INVALID_BLOCK_DIR + " directory has been created to save invalid blocks.");
    }

    final String fileName = String.format("slot%s_root%s.ssz", slot, blockRoot);
    final Path path = invalidBlocksDir.resolve(fileName);

    try {
      // Create file and save ssz
      if (!path.toFile().createNewFile()) {
        final String errorMessage =
            String.format(
                "Unable to create new file to save invalid block. Slot: %s, Block Root: %s",
                slot, blockRoot);
        throw new FileAlreadyExistsException(errorMessage);
      }
      final Path writtenPath = Files.write(path, blockSsz.toArray());
      return Optional.of(writtenPath.toString());

    } catch (IOException e) {
      final String errorMessage =
          String.format(
              "Failed to save invalid block bytes to file. Slot: %s, Block Root: %s",
              slot, blockRoot);
      LOG.error(errorMessage, e);
      return Optional.empty(); // TODO What to do if error? Is returning empty enough?
    }
  }
}
