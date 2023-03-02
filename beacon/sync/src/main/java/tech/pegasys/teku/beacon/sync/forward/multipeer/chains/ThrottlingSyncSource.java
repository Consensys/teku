/*
 * Copyright ConsenSys Software Inc., 2022
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

import java.time.Duration;
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
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;

public class ThrottlingSyncSource implements SyncSource {
  private static final Logger LOG = LogManager.getLogger();
  private static final long TIME_OUT = 60;
  public static final Duration PEER_REQUEST_DELAY = Duration.ofSeconds(3);
  private final AsyncRunner asyncRunner;
  private final SyncSource delegate;

  private final RateTracker blocksRateTracker;
  private final RateTracker blobSidecarsRateTracker;

  public ThrottlingSyncSource(
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final SyncSource delegate,
      final int maxBlocksPerMinute,
      final int maxBlobSidecarsPerMinute) {
    this.asyncRunner = asyncRunner;
    this.delegate = delegate;
    this.blocksRateTracker = new RateTracker(maxBlocksPerMinute, TIME_OUT, timeProvider);
    this.blobSidecarsRateTracker =
        new RateTracker(maxBlobSidecarsPerMinute, TIME_OUT, timeProvider);
  }

  @Override
  public SafeFuture<Void> requestBlocksByRange(
      final UInt64 startSlot,
      final UInt64 count,
      final RpcResponseListener<SignedBeaconBlock> listener) {
    if (blocksRateTracker.wantToRequestObjects(count.longValue()) > 0) {
      LOG.debug("Sending request for {} blocks", count);
      return delegate.requestBlocksByRange(startSlot, count, listener);
    } else {
      return asyncRunner.runAfterDelay(
          () -> requestBlocksByRange(startSlot, count, listener), PEER_REQUEST_DELAY);
    }
  }

  @Override
  public SafeFuture<Void> requestBlobSidecarsByRange(
      final UInt64 startSlot, final UInt64 count, final RpcResponseListener<BlobSidecar> listener) {
    if (blobSidecarsRateTracker.wantToRequestObjects(count.longValue()) > 0) {
      LOG.debug("Sending request for {} blob sidecars", count);
      return delegate.requestBlobSidecarsByRange(startSlot, count, listener);
    } else {
      return asyncRunner.runAfterDelay(
          () -> requestBlobSidecarsByRange(startSlot, count, listener), PEER_REQUEST_DELAY);
    }
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
}
