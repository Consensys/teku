/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beacon.sync.forward.multipeer;

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.Batch;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;

public class BatchImporter {

  private static final Logger LOG = LogManager.getLogger();

  private final BlockImporter blockImporter;
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final ExecutionPayloadManager executionPayloadManager;
  private final AsyncRunner asyncRunner;

  public BatchImporter(
      final BlockImporter blockImporter,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final ExecutionPayloadManager executionPayloadManager,
      final AsyncRunner asyncRunner) {
    this.blockImporter = blockImporter;
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.executionPayloadManager = executionPayloadManager;
    this.asyncRunner = asyncRunner;
  }

  /**
   * Import the blocks and blob sidecars (if any) in the specified batch.
   *
   * <p>Guaranteed to return immediately and perform the import on worker threads.
   *
   * @param batch the batch to import
   * @return a future reporting the result of the import
   */
  public SafeFuture<BatchImportResult> importBatch(final Batch batch) {
    // Copy the data from batch as we're going to use them from off the event thread.
    final List<SignedBeaconBlock> blocks = new ArrayList<>(batch.getBlocks());
    final Map<Bytes32, List<BlobSidecar>> blobSidecarsByBlockRoot =
        Map.copyOf(batch.getBlobSidecarsByBlockRoot());
    final Map<Bytes32, SignedExecutionPayloadEnvelope> executionPayloadsByBlockRoot =
        Map.copyOf(batch.getExecutionPayloadsByBlockRoot());

    final Optional<SyncSource> source = batch.getSource();

    checkState(!blocks.isEmpty(), "Batch has no blocks to import");
    return asyncRunner.runAsync(
        () -> {
          final SignedBeaconBlock firstBlock = blocks.getFirst();
          SafeFuture<SingleImportResult> importResult =
              importBlock(
                  firstBlock,
                  blobSidecarsByBlockRoot,
                  executionPayloadsByBlockRoot,
                  source.orElseThrow());
          for (int i = 1; i < blocks.size(); i++) {
            final SignedBeaconBlock block = blocks.get(i);
            importResult =
                importResult.thenCompose(
                    previousResult -> {
                      if (previousResult.isSuccessful()) {
                        return importBlock(
                            block,
                            blobSidecarsByBlockRoot,
                            executionPayloadsByBlockRoot,
                            source.orElseThrow());
                      } else {
                        return SafeFuture.completedFuture(previousResult);
                      }
                    });
          }
          return importResult.thenApply(
              lastImportResult -> {
                if (lastImportResult.isSuccessful()) {
                  return BatchImportResult.IMPORTED_ALL_BLOCKS;
                } else if (lastImportResult.failedPayloadExecution()) {
                  return BatchImportResult.EXECUTION_CLIENT_OFFLINE;
                } else if (lastImportResult.dataNotAvailable) {
                  return BatchImportResult.DATA_NOT_AVAILABLE;
                }
                LOG.debug(
                    "Failed to import batch {}: {}",
                    batch,
                    lastImportResult.failureReason(),
                    lastImportResult.failureCause().orElse(null));
                return BatchImportResult.IMPORT_FAILED;
              });
        });
  }

  private SafeFuture<SingleImportResult> importBlock(
      final SignedBeaconBlock block,
      final Map<Bytes32, List<BlobSidecar>> blobSidecarsByBlockRoot,
      final Map<Bytes32, SignedExecutionPayloadEnvelope> executionPayloadsByBlockRoot,
      final SyncSource source) {
    final Bytes32 blockRoot = block.getRoot();
    final Optional<SignedExecutionPayloadEnvelope> executionPayload =
        Optional.ofNullable(executionPayloadsByBlockRoot.get(blockRoot));
    if (!blobSidecarsByBlockRoot.containsKey(blockRoot)) {
      return importBlock(block, executionPayload, source);
    }
    final List<BlobSidecar> blobSidecars = blobSidecarsByBlockRoot.get(blockRoot);
    LOG.trace(
        "Sending {} blob sidecars to the pool during syncing for block with root {}",
        blobSidecars.size(),
        blockRoot);
    // Add blob sidecars to the pool in order for them to be available when the block is being
    // imported
    blockBlobSidecarsTrackersPool.onCompletedBlockAndBlobSidecars(block, blobSidecars);
    return importBlock(block, Optional.empty(), source);
  }

  private SafeFuture<SingleImportResult> importBlock(
      final SignedBeaconBlock block,
      final Optional<SignedExecutionPayloadEnvelope> executionPayload,
      final SyncSource source) {
    LOG.trace(
        "Importing block during syncing for slot {} and root {}", block.getSlot(), block.getRoot());
    return blockImporter
        .importBlock(block)
        .thenCompose(
            blockImportResult -> {
              if (blockImportResult.getFailureReason()
                  == BlockImportResult.FailureReason.FAILED_WEAK_SUBJECTIVITY_CHECKS) {
                LOG.warn(
                    "Disconnecting source ({}) for sending block that failed weak subjectivity checks: {}",
                    source,
                    blockImportResult);
                source.disconnectCleanly(DisconnectReason.REMOTE_FAULT).finishWarn(LOG);
              }
              if (executionPayload.isEmpty() || !blockImportResult.isSuccessful()) {
                return SafeFuture.completedFuture(
                    new SingleImportResult(
                        blockImportResult.isSuccessful(),
                        blockImportResult.hasFailedExecutingExecutionPayload(),
                        blockImportResult.isDataNotAvailable(),
                        Optional.ofNullable(blockImportResult.getFailureReason())
                            .map(Enum::name)
                            .orElse(null),
                        blockImportResult.getFailureCause()));
              }
              LOG.trace(
                  "Importing execution payload during syncing for slot {} and block root {}",
                  block.getSlot(),
                  block.getRoot());
              return executionPayloadManager
                  .importExecutionPayload(executionPayload.get())
                  .thenApply(
                      executionPayloadImportResult ->
                          new SingleImportResult(
                              executionPayloadImportResult.isSuccessful(),
                              executionPayloadImportResult.hasFailedExecution(),
                              executionPayloadImportResult.isDataNotAvailable(),
                              Optional.ofNullable(executionPayloadImportResult.getFailureReason())
                                  .map(Enum::name)
                                  .orElse(null),
                              executionPayloadImportResult.getFailureCause()));
            });
  }

  public record SingleImportResult(
      boolean isSuccessful,
      boolean failedPayloadExecution,
      boolean dataNotAvailable,
      String failureReason,
      Optional<Throwable> failureCause) {}

  public enum BatchImportResult {
    IMPORTED_ALL_BLOCKS,
    IMPORT_FAILED,
    EXECUTION_CLIENT_OFFLINE,
    DATA_NOT_AVAILABLE;

    public boolean isFailure() {
      return this == IMPORT_FAILED;
    }
  }
}
