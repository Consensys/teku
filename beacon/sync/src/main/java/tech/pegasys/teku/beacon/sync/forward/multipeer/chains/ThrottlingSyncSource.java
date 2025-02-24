/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.beacon.sync.forward.multipeer.chains;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.RateTracker;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;

public class ThrottlingSyncSource implements SyncSource {
  private static final Logger LOG = LogManager.getLogger();

  private static final long TIMEOUT_SECONDS = 60;
  public static final Duration PEER_REQUEST_DELAY = Duration.ofSeconds(3);

  private final AsyncRunner asyncRunner;
  private final SyncSource delegate;

  private final RateTracker blocksRateTracker;
  private final Optional<Integer> maybeMaxBlobsPerBlock;
  private final RateTracker blobSidecarsRateTracker;
  private final RateTracker executionPayloadEnvelopesRateTracker;

  public ThrottlingSyncSource(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final SyncSource delegate,
      final int maxBlocksPerMinute,
      final Optional<Integer> maybeMaxBlobsPerBlock,
      final Optional<Integer> maybeMaxBlobSidecarsPerMinute) {
    this.asyncRunner = asyncRunner;
    this.delegate = delegate;
    this.blocksRateTracker = RateTracker.create(maxBlocksPerMinute, TIMEOUT_SECONDS, timeProvider);
    this.maybeMaxBlobsPerBlock = maybeMaxBlobsPerBlock;
    this.blobSidecarsRateTracker =
        maybeMaxBlobSidecarsPerMinute
            .map(
                maxBlobSidecarsPerMinute ->
                    RateTracker.create(maxBlobSidecarsPerMinute, TIMEOUT_SECONDS, timeProvider))
            .orElse(RateTracker.NOOP);
    this.executionPayloadEnvelopesRateTracker =
        spec.isMilestoneSupported(SpecMilestone.EIP7732)
            ? RateTracker.create(maxBlocksPerMinute, TIMEOUT_SECONDS, timeProvider)
            : RateTracker.NOOP;
  }

  @Override
  public SafeFuture<Void> requestBlocksByRange(
      final UInt64 startSlot,
      final UInt64 count,
      final RpcResponseListener<SignedBeaconBlock> listener) {
    return blocksRateTracker
        .approveObjectsRequest(count.longValue())
        .map(
            requestApproval -> {
              LOG.debug("Sending request for {} blocks", count);
              final RpcResponseListenerWithCount<SignedBeaconBlock> listenerWithCount =
                  new RpcResponseListenerWithCount<>(listener);
              return delegate
                  .requestBlocksByRange(startSlot, count, listenerWithCount)
                  .alwaysRun(
                      () ->
                          // adjust for slots with empty blocks
                          blocksRateTracker.adjustObjectsRequest(
                              requestApproval, listenerWithCount.count.get()));
            })
        .orElseGet(
            () -> {
              LOG.debug(
                  "Rate limiting request for {} blocks. Retry in {} seconds",
                  count,
                  PEER_REQUEST_DELAY.toSeconds());
              return asyncRunner.runAfterDelay(
                  () -> requestBlocksByRange(startSlot, count, listener), PEER_REQUEST_DELAY);
            });
  }

  @Override
  public SafeFuture<Void> requestBlobSidecarsByRange(
      final UInt64 startSlot, final UInt64 count, final RpcResponseListener<BlobSidecar> listener) {
    final UInt64 blobSidecarsCount = maybeMaxBlobsPerBlock.map(count::times).orElse(UInt64.ZERO);
    return blobSidecarsRateTracker
        .approveObjectsRequest(blobSidecarsCount.longValue())
        .map(
            requestApproval -> {
              LOG.debug("Sending request for approximately {} blob sidecars", blobSidecarsCount);
              final RpcResponseListenerWithCount<BlobSidecar> listenerWithCount =
                  new RpcResponseListenerWithCount<>(listener);
              return delegate
                  .requestBlobSidecarsByRange(startSlot, count, listenerWithCount)
                  .alwaysRun(
                      () ->
                          // adjust for slots with empty blocks and slots with blobs <
                          // maxBlobsPerBlock
                          blobSidecarsRateTracker.adjustObjectsRequest(
                              requestApproval, listenerWithCount.count.get()));
            })
        .orElseGet(
            () -> {
              LOG.debug(
                  "Rate limiting request for approximately {} blob sidecars. Retry in {} seconds",
                  blobSidecarsCount,
                  PEER_REQUEST_DELAY.toSeconds());
              return asyncRunner.runAfterDelay(
                  () -> requestBlobSidecarsByRange(startSlot, count, listener), PEER_REQUEST_DELAY);
            });
  }

  @Override
  public SafeFuture<Void> requestExecutionPayloadEnvelopesByRange(
      final UInt64 startSlot,
      final UInt64 count,
      final RpcResponseListener<SignedExecutionPayloadEnvelope> listener) {
    return executionPayloadEnvelopesRateTracker
        .approveObjectsRequest(count.longValue())
        .map(
            requestApproval -> {
              LOG.debug("Sending request for {} execution payload envelopes", count);
              final RpcResponseListenerWithCount<SignedExecutionPayloadEnvelope> listenerWithCount =
                  new RpcResponseListenerWithCount<>(listener);
              return delegate
                  .requestExecutionPayloadEnvelopesByRange(startSlot, count, listenerWithCount)
                  .alwaysRun(
                      () ->
                          // adjust for slots with empty execution payloads
                          executionPayloadEnvelopesRateTracker.adjustObjectsRequest(
                              requestApproval, listenerWithCount.count.get()));
            })
        .orElseGet(
            () -> {
              LOG.debug(
                  "Rate limiting request for {} execution payload envelopes. Retry in {} seconds",
                  count,
                  PEER_REQUEST_DELAY.toSeconds());
              return asyncRunner.runAfterDelay(
                  () -> requestExecutionPayloadEnvelopesByRange(startSlot, count, listener),
                  PEER_REQUEST_DELAY);
            });
  }

  @Override
  public SafeFuture<Void> disconnectCleanly(final DisconnectReason reason) {
    return delegate.disconnectCleanly(reason);
  }

  @Override
  public void adjustReputation(final ReputationAdjustment adjustment) {
    delegate.adjustReputation(adjustment);
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  @VisibleForTesting
  static class RpcResponseListenerWithCount<T> implements RpcResponseListener<T> {

    private final AtomicInteger count = new AtomicInteger(0);

    private final RpcResponseListener<T> delegate;

    private RpcResponseListenerWithCount(final RpcResponseListener<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public SafeFuture<?> onResponse(final T response) {
      count.incrementAndGet();
      return delegate.onResponse(response);
    }
  }
}
