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

package tech.pegasys.teku.sync.multipeer.batches;

import java.util.Collection;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;

public class BatchFactory {
  private final EventThread eventThread;

  public BatchFactory(final EventThread eventThread) {
    this.eventThread = eventThread;
  }

  public Batch createBatch(final TargetChain chain, final UInt64 start, final UInt64 count) {
    eventThread.checkOnEventThread();
    final Supplier<SyncSource> syncSourceProvider = () -> selectRandomPeer(chain.getPeers());
    return new SyncSourceBatch(eventThread, syncSourceProvider, chain, start, count);
  }

  private SyncSource selectRandomPeer(final Collection<SyncSource> peers) {
    // TODO: Need to handle the case where there are no peers?
    return peers.stream().skip((int) (peers.size() * Math.random())).findFirst().orElseThrow();
  }
}
