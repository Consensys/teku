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

package tech.pegasys.teku.networking.eth2.peers;

import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment;

/**
 * Represents an external source of blocks to sync. Typically a peer, but this provides the minimal
 * interface required by the sync system.
 */
public interface SyncSource {
  SafeFuture<Void> requestBlocksByRange(
      UInt64 startSlot,
      UInt64 count,
      UInt64 step,
      ResponseStreamListener<SignedBeaconBlock> listener);

  void adjustReputation(final ReputationAdjustment adjustment);

  SafeFuture<Void> disconnectCleanly(DisconnectReason reason);
}
