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

package tech.pegasys.teku.sync.multipeer.chains;

import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.RateTracker;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.util.time.TimeProvider;

public class ThrottlingSyncSource implements SyncSource {
  private static final Logger LOG = LogManager.getLogger();
  private final AsyncRunner asyncRunner;
  private final SyncSource delegate;

  private final RateTracker rateTracker;

  public ThrottlingSyncSource(
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final SyncSource delegate,
      final int maxBlocksPerMinute) {
    this.asyncRunner = asyncRunner;
    this.delegate = delegate;
    rateTracker = new RateTracker(maxBlocksPerMinute, 60, timeProvider);
  }

  @Override
  public SafeFuture<Void> requestBlocksByRange(
      final UInt64 startSlot,
      final UInt64 count,
      final UInt64 step,
      final ResponseStreamListener<SignedBeaconBlock> listener) {
    if (rateTracker.wantToRequestObjects(count.longValue()) > 0) {
      LOG.debug("Sending request for {} blocks", count);
      return delegate.requestBlocksByRange(startSlot, count, step, listener);
    } else {
      return asyncRunner.runAfterDelay(
          () -> requestBlocksByRange(startSlot, count, step, listener), 3, TimeUnit.SECONDS);
    }
  }

  @Override
  public SafeFuture<Void> disconnectCleanly(final DisconnectReason reason) {
    return delegate.disconnectCleanly(reason);
  }

  @Override
  public String toString() {
    return delegate.toString();
  }
}
