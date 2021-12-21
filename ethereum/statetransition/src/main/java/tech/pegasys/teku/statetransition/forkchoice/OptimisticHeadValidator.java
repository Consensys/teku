/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.forkchoice;

import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * Periodically retries execution any chain heads that are only optimistically validated.
 *
 * <p>This ensures that any forks which are unable to be verified because the EL is syncing are
 * retried. It also handles the case at startup where all non-finalized nodes are considered
 * optimistically sync'd.
 *
 * <p>We only need to verify the chain head since a VALID response for a block also indicates all
 * ancestors are valid.
 */
public class OptimisticHeadValidator extends Service {

  private static final Logger LOG = LogManager.getLogger();

  static final Duration RECHECK_INTERVAL = Duration.ofMinutes(1);
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final ForkChoice forkChoice;
  private final ExecutionEngineChannel executionEngine;
  private Optional<Cancellable> cancellable = Optional.empty();

  public OptimisticHeadValidator(
      final AsyncRunner asyncRunner,
      final ForkChoice forkChoice,
      final RecentChainData recentChainData,
      final ExecutionEngineChannel executionEngine) {
    this.asyncRunner = asyncRunner;
    this.forkChoice = forkChoice;
    this.recentChainData = recentChainData;
    this.executionEngine = executionEngine;
  }

  @Override
  protected synchronized SafeFuture<?> doStart() {
    cancellable =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                this::verifyOptimisticHeads,
                RECHECK_INTERVAL,
                error -> LOG.error("Failed to validate optimistic chain heads", error)));
    // Run immediately on start
    verifyOptimisticHeads();
    return SafeFuture.COMPLETE;
  }

  @Override
  protected synchronized SafeFuture<?> doStop() {
    cancellable.ifPresent(Cancellable::cancel);
    cancellable = Optional.empty();
    return SafeFuture.COMPLETE;
  }

  private void verifyOptimisticHeads() {
    SafeFuture.allOf(
            recentChainData.getOptimisticChainHeads().keySet().stream()
                .map(this::reexecuteBlockPayload)
                .toArray(SafeFuture[]::new))
        .reportExceptions();
  }

  private SafeFuture<Void> reexecuteBlockPayload(final Bytes32 blockRoot) {
    return recentChainData
        .retrieveBlockByRoot(blockRoot)
        .thenCompose(
            maybeBlock ->
                maybeBlock
                    .flatMap(block -> block.getBody().getOptionalExecutionPayload())
                    .map(executionPayload -> reexecutePayload(blockRoot, executionPayload))
                    .orElse(SafeFuture.COMPLETE))
        .exceptionally(
            error -> {
              LOG.warn("Failed to verify execution payload for block {}", blockRoot, error);
              return null;
            });
  }

  private SafeFuture<Void> reexecutePayload(
      final Bytes32 blockRoot, final ExecutionPayload executionPayload) {
    if (executionPayload.isDefault()) {
      return SafeFuture.COMPLETE;
    }
    UInt64 latestFinalizedBlockSlot = recentChainData.getStore().getLatestFinalizedBlockSlot();
    return executionEngine
        .executePayload(executionPayload)
        .thenAccept(
            result ->
                forkChoice.onExecutionPayloadResult(blockRoot, result, latestFinalizedBlockSlot));
  }
}
