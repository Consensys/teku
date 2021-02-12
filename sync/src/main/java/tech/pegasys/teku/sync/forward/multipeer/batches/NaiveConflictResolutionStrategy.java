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

package tech.pegasys.teku.sync.forward.multipeer.batches;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;

public class NaiveConflictResolutionStrategy implements ConflictResolutionStrategy {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public void verifyBatch(final Batch batch, final SyncSource source) {
    LOG.debug("Invalidating batch {}", batch);
    // Re-download all contested batches, but no penalties are applied to peers
    // Just hope it works out better next time.
    batch.markAsInvalid();
  }

  @Override
  public void reportInvalidBatch(final Batch batch, final SyncSource source) {
    LOG.debug("Disconnecting peer {} for providing invalid batch data {}", source, batch);
    // Disconnect any peer that gives us clearly invalid data.
    // This assumes malice where a simple chain reorg may have explained it
    source.disconnectCleanly(DisconnectReason.REMOTE_FAULT).reportExceptions();
  }

  @Override
  public void reportConfirmedBatch(final Batch batch, final SyncSource source) {}
}
