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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.p2p.peer.Peer;

public class BlocksByRangeListenerWrapper implements ResponseStreamListener<SignedBeaconBlock> {

  private final Peer peer;
  private final ResponseStreamListener<SignedBeaconBlock> blockResponseListener;
  private final UnsignedLong startSlot;
  private final UnsignedLong endSlot;
  private final UnsignedLong step;

  private Optional<UnsignedLong> maybeSlotOfLastBlock = Optional.empty();

  public BlocksByRangeListenerWrapper(
      Peer peer,
      ResponseStreamListener<SignedBeaconBlock> blockResponseListener,
      UnsignedLong startSlot,
      UnsignedLong count,
      UnsignedLong step) {
    this.peer = peer;
    this.blockResponseListener = blockResponseListener;
    this.startSlot = startSlot;
    this.step = step;
    this.endSlot = startSlot.plus(step.times(count));
  }

  @Override
  public SafeFuture<?> onResponse(SignedBeaconBlock response) {
    UnsignedLong blockSlot = response.getSlot();

    if (!blockSlotIsInRange(blockSlot)
        || !blockSlotMatchesTheStep(blockSlot)
        || !blockSlotGreaterThanPreviousBlockSlot(blockSlot)) {
      throw new BlocksByRangeResponseOutOfOrderException(peer, startSlot, endSlot);
    }

    maybeSlotOfLastBlock = Optional.of(blockSlot);
    return blockResponseListener.onResponse(response);
  }

  private boolean blockSlotIsInRange(UnsignedLong blockSlot) {
    return blockSlot.compareTo(startSlot) >= 0 && blockSlot.compareTo(endSlot) <= 0;
  }

  private boolean blockSlotMatchesTheStep(UnsignedLong blockSlot) {
    return blockSlot.minus(startSlot).mod(step).equals(UnsignedLong.ZERO);
  }

  private boolean blockSlotGreaterThanPreviousBlockSlot(UnsignedLong blockSlot) {
    if (maybeSlotOfLastBlock.isEmpty()) {
      return true;
    }

    UnsignedLong lastBlockSlot = maybeSlotOfLastBlock.get();
    return blockSlot.compareTo(lastBlockSlot) > 0;
  }
}
