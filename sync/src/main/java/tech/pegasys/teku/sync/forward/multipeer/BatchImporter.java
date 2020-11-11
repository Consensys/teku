/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.sync.forward.multipeer;

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.sync.forward.multipeer.batches.Batch;

public class BatchImporter {
  private static final Logger LOG = LogManager.getLogger();

  private final BlockImporter blockImporter;
  private final AsyncRunner asyncRunner;

  public BatchImporter(final BlockImporter blockImporter, final AsyncRunner asyncRunner) {
    this.blockImporter = blockImporter;
    this.asyncRunner = asyncRunner;
  }

  /**
   * Import the blocks in the specified batch.
   *
   * <p>Guaranteed to return immediately and perform the import on worker threads.
   *
   * @param batch the batch to import
   * @return a future reporting the result of the import
   */
  public SafeFuture<BatchImportResult> importBatch(final Batch batch) {
    // Copy the data from batch as we're going to use them from off the event thread.
    final List<SignedBeaconBlock> blocks = new ArrayList<>(batch.getBlocks());
    final Optional<SyncSource> source = batch.getSource();

    checkState(!blocks.isEmpty(), "Batch has no blocks to import");
    return asyncRunner.runAsync(
        () -> {
          SafeFuture<BlockImportResult> importResult =
              importBlock(blocks.get(0), source.orElseThrow());
          for (int i = 1; i < blocks.size(); i++) {
            final SignedBeaconBlock block = blocks.get(i);
            importResult =
                importResult.thenCompose(
                    previousResult -> {
                      if (previousResult.isSuccessful()) {
                        return importBlock(block, source.orElseThrow());
                      } else {
                        return SafeFuture.completedFuture(previousResult);
                      }
                    });
          }
          return importResult.thenApply(
              lastBlockImportResult -> {
                if (lastBlockImportResult.isSuccessful()) {
                  return BatchImportResult.IMPORTED_ALL_BLOCKS;
                }
                LOG.debug(
                    "Failed to import batch {}: {}",
                    batch,
                    lastBlockImportResult.getFailureReason(),
                    lastBlockImportResult.getFailureCause().orElse(null));
                return BatchImportResult.IMPORT_FAILED;
              });
        });
  }

  private SafeFuture<BlockImportResult> importBlock(
      final SignedBeaconBlock block, final SyncSource source) {
    return blockImporter
        .importBlock(block)
        .thenApply(
            result -> {
              if (result.getFailureReason()
                  == BlockImportResult.FailureReason.FAILED_WEAK_SUBJECTIVITY_CHECKS) {
                LOG.warn(
                    "Disconnecting source ({}) for sending block that failed weak subjectivity checks: {}",
                    source,
                    result);
                source.disconnectCleanly(DisconnectReason.REMOTE_FAULT).reportExceptions();
              }
              return result;
            });
  }

  public enum BatchImportResult {
    IMPORTED_ALL_BLOCKS,
    IMPORT_FAILED;

    public boolean isFailure() {
      return this == IMPORT_FAILED;
    }
  }
}
