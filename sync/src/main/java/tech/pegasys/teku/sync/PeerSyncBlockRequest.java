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

package tech.pegasys.teku.sync;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;

public class PeerSyncBlockRequest implements ResponseStreamListener<SignedBeaconBlock> {

  private final SafeFuture<Void> readyForNextRequest;
  private final UnsignedLong lastRequestedSlot;
  private final ResponseStreamListener<SignedBeaconBlock> blockResponseListener;
  private Optional<UnsignedLong> maybeSlotOfLastBlock = Optional.empty();

  public PeerSyncBlockRequest(
      final SafeFuture<Void> readyForNextRequest,
      final UnsignedLong lastRequestedSlot,
      final ResponseStreamListener<SignedBeaconBlock> blockResponseListener) {
    this.readyForNextRequest = readyForNextRequest;
    this.lastRequestedSlot = lastRequestedSlot;
    this.blockResponseListener = blockResponseListener;
  }

  @Override
  public SafeFuture<?> onResponse(final SignedBeaconBlock response) {
    UnsignedLong newBlockSlot = response.getSlot();
    if (maybeSlotOfLastBlock.isPresent()) {
      UnsignedLong slotOfLastBlock = maybeSlotOfLastBlock.get();
      if (newBlockSlot.compareTo(slotOfLastBlock) <= 0) {
        throw new OutOfOrderException(slotOfLastBlock, newBlockSlot);
      }
    }

    maybeSlotOfLastBlock = Optional.of(response.getSlot());
    return blockResponseListener.onResponse(response);
  }

  public SafeFuture<Void> getReadyForNextRequest() {
    return readyForNextRequest;
  }

  public UnsignedLong getActualEndSlot() {
    // The peer must return at least one block if it has it, so if no blocks were returned they
    // must all of have been empty.
    return maybeSlotOfLastBlock.orElse(lastRequestedSlot.minus(UnsignedLong.ONE));
  }
}
