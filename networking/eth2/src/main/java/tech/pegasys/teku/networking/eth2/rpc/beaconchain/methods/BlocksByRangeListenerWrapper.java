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

import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeResponseInvalidResponseException.InvalidResponseType.BLOCK_PARENT_ROOT_DOES_NOT_MATCH;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeResponseInvalidResponseException.InvalidResponseType.BLOCK_SLOT_DOES_NOT_MATCH_STEP;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeResponseInvalidResponseException.InvalidResponseType.BLOCK_SLOT_NOT_GREATER_THAN_PREVIOUS_BLOCK_SLOT;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeResponseInvalidResponseException.InvalidResponseType.BLOCK_SLOT_NOT_IN_RANGE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class BlocksByRangeListenerWrapper implements RpcResponseListener<SignedBeaconBlock> {

  private final Peer peer;
  private final RpcResponseListener<SignedBeaconBlock> blockResponseListener;
  private final UInt64 startSlot;
  private final UInt64 endSlot;
  private final UInt64 step;

  private Optional<Bytes32> maybeRootOfLastBlock = Optional.empty();
  private Optional<UInt64> maybeSlotOfLastBlock = Optional.empty();

  public BlocksByRangeListenerWrapper(
      Peer peer,
      RpcResponseListener<SignedBeaconBlock> blockResponseListener,
      UInt64 startSlot,
      UInt64 count,
      UInt64 step) {
    this.peer = peer;
    this.blockResponseListener = blockResponseListener;
    this.startSlot = startSlot;
    this.step = step;
    this.endSlot = startSlot.plus(step.times(count));
  }

  @Override
  public SafeFuture<?> onResponse(SignedBeaconBlock response) {
    return SafeFuture.of(
        () -> {
          UInt64 blockSlot = response.getSlot();
          if (!blockSlotIsInRange(blockSlot)) {
            throw new BlocksByRangeResponseInvalidResponseException(peer, BLOCK_SLOT_NOT_IN_RANGE);
          }

          if (!blockSlotMatchesTheStep(blockSlot)) {
            throw new BlocksByRangeResponseInvalidResponseException(
                peer, BLOCK_SLOT_DOES_NOT_MATCH_STEP);
          }

          if (!blockSlotGreaterThanPreviousBlockSlot(blockSlot)) {
            throw new BlocksByRangeResponseInvalidResponseException(
                peer, BLOCK_SLOT_NOT_GREATER_THAN_PREVIOUS_BLOCK_SLOT);
          }

          if (!blockParentRootMatches(response.getParentRoot())) {
            throw new BlocksByRangeResponseInvalidResponseException(
                peer, BLOCK_PARENT_ROOT_DOES_NOT_MATCH);
          }

          maybeSlotOfLastBlock = Optional.of(blockSlot);
          maybeRootOfLastBlock = Optional.of(response.getRoot());
          return blockResponseListener.onResponse(response);
        });
  }

  private boolean blockSlotIsInRange(UInt64 blockSlot) {
    return blockSlot.compareTo(startSlot) >= 0 && blockSlot.compareTo(endSlot) <= 0;
  }

  private boolean blockSlotMatchesTheStep(UInt64 blockSlot) {
    return blockSlot.minus(startSlot).mod(step).equals(UInt64.ZERO);
  }

  private boolean blockSlotGreaterThanPreviousBlockSlot(UInt64 blockSlot) {
    if (maybeSlotOfLastBlock.isEmpty()) {
      return true;
    }

    UInt64 lastBlockSlot = maybeSlotOfLastBlock.get();
    return blockSlot.isGreaterThan(lastBlockSlot);
  }

  private boolean blockParentRootMatches(Bytes32 blockParentRoot) {
    if (maybeRootOfLastBlock.isEmpty()) {
      return true;
    }

    if (!step.equals(UInt64.ONE)) {
      return true;
    }

    return maybeRootOfLastBlock.get().equals(blockParentRoot);
  }
}
