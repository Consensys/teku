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

import static tech.pegasys.teku.util.config.Constants.MAX_BLOCKS_PER_MINUTE;
import static tech.pegasys.teku.util.config.Constants.SYNC_BATCH_SIZE;

import java.util.HashMap;
import java.util.Map;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;

public class SyncSourceFactory {

  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final Map<Eth2Peer, SyncSource> syncSourcesByPeer = new HashMap<>();

  public SyncSourceFactory(final AsyncRunner asyncRunner, final TimeProvider timeProvider) {
    this.asyncRunner = asyncRunner;
    this.timeProvider = timeProvider;
  }

  public SyncSource getOrCreateSyncSource(final Eth2Peer peer) {
    // Limit request rate to just a little under what we'd accept
    final int maxBlocksPerMinute = MAX_BLOCKS_PER_MINUTE - SYNC_BATCH_SIZE.intValue() - 1;
    return syncSourcesByPeer.computeIfAbsent(
        peer,
        source -> new ThrottlingSyncSource(asyncRunner, timeProvider, source, maxBlocksPerMinute));
  }

  public void onPeerDisconnected(final Eth2Peer peer) {
    syncSourcesByPeer.remove(peer);
  }
}
