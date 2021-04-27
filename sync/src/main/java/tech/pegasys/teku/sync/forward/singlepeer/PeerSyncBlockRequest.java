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

package tech.pegasys.teku.sync.forward.singlepeer;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class PeerSyncBlockRequest implements RpcResponseListener<SignedBeaconBlock> {

  private final SafeFuture<Void> readyForNextRequest;
  private final UInt64 lastRequestedSlot;
  private final RpcResponseListener<SignedBeaconBlock> blockResponseListener;
  private Optional<UInt64> slotOfLastBlock = Optional.empty();

  public PeerSyncBlockRequest(
      final SafeFuture<Void> readyForNextRequest,
      final UInt64 lastRequestedSlot,
      final RpcResponseListener<SignedBeaconBlock> blockResponseListener) {
    this.readyForNextRequest = readyForNextRequest;
    this.lastRequestedSlot = lastRequestedSlot;
    this.blockResponseListener = blockResponseListener;
  }

  @Override
  public SafeFuture<?> onResponse(final SignedBeaconBlock response) {
    slotOfLastBlock = Optional.of(response.getSlot());
    return blockResponseListener.onResponse(response);
  }

  public SafeFuture<Void> getReadyForNextRequest() {
    return readyForNextRequest;
  }

  public UInt64 getActualEndSlot() {
    // The peer must return at least one block if it has it, so if no blocks were returned they
    // must all of have been empty.
    return slotOfLastBlock.orElse(lastRequestedSlot.minus(UInt64.ONE));
  }
}
