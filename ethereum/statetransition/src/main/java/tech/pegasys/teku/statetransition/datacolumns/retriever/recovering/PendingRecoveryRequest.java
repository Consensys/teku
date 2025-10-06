/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.datacolumns.retriever.recovering;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

class PendingRecoveryRequest {
  private static final Logger LOG = LogManager.getLogger();
  private final SafeFuture<DataColumnSidecar> future = new SafeFuture<>();
  private final DataColumnSlotAndIdentifier columnnId;
  private final UInt64 downloadTimeoutMillis;
  private final UInt64 taskTimeoutMillis;
  private final SafeFuture<DataColumnSidecar> downloadFuture;

  PendingRecoveryRequest(
      final DataColumnSlotAndIdentifier columnId,
      final SafeFuture<DataColumnSidecar> downloadFuture,
      final UInt64 timestamp,
      final Duration timeout,
      final Duration downloadTimeout) {
    this.downloadTimeoutMillis = timestamp.plus(downloadTimeout.toMillis());
    this.taskTimeoutMillis = timestamp.plus(timeout.toMillis());
    this.columnnId = columnId;
    this.downloadFuture = downloadFuture;

    downloadFuture
        .thenRun(this::downloadCompleted)
        .exceptionally(this::failedDownload)
        .finishError(LOG);
  }

  private Void failedDownload(final Throwable throwable) {
    LOG.debug("Failed downloading column {}", columnnId, throwable);
    return null;
  }

  SafeFuture<DataColumnSidecar> getFuture() {
    return future;
  }

  UInt64 getIndex() {
    return columnnId.columnIndex();
  }

  UInt64 getSlot() {
    return columnnId.slot();
  }

  Bytes32 getBlockRoot() {
    return columnnId.blockRoot();
  }

  boolean isFailedDownloading() {
    return downloadFuture.isCompletedExceptionally();
  }

  @VisibleForTesting
  SafeFuture<DataColumnSidecar> getDownloadFuture() {
    return downloadFuture;
  }

  void checkTimeout(final UInt64 currentTimeMillis) {
    if (currentTimeMillis.isGreaterThanOrEqualTo(downloadTimeoutMillis)
        && !downloadFuture.isDone()) {
      LOG.debug("Cancelling download of {} due to timeout", columnnId);
      downloadFuture.cancel(true);
    }
    if (currentTimeMillis.isGreaterThanOrEqualTo(taskTimeoutMillis) && !future.isDone()) {
      LOG.debug("Cancelling task {} due to timeout", columnnId);
      future.cancel(true);
    }
  }

  void cancel() {
    downloadFuture.cancel(true);
    future.cancel(true);
  }

  private void downloadCompleted() {
    LOG.trace("Successfully downloaded column {}", columnnId);
    downloadFuture.propagateTo(future);
  }

  public SlotAndBlockRoot getSlotAndBlockRoot() {
    return columnnId.getSlotAndBlockRoot();
  }
}
