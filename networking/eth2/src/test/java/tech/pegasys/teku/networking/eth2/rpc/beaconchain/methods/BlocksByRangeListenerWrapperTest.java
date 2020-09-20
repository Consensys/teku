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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;

public class BlocksByRangeListenerWrapperTest {
  private DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private BlocksByRangeListenerWrapper listenerWrapper;
  private Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private ResponseStreamListener<SignedBeaconBlock> listener = mock(ResponseStreamListener.class);

  @BeforeEach
  void setUp() {
    when(listener.onResponse(any())).thenReturn(SafeFuture.completedFuture(null));
  }

  @Test
  void blockSlotSmallerThanFromSlot() {
    UInt64 START_SLOT = UInt64.valueOf(1);
    UInt64 COUNT = UInt64.valueOf(4);
    UInt64 STEP = UInt64.valueOf(2);
    listenerWrapper = new BlocksByRangeListenerWrapper(peer, listener, START_SLOT, COUNT, STEP);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(0);

    SafeFuture<?> result = listenerWrapper.onResponse(block1);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlocksByRangeResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlocksByRangeResponseInvalidResponseException.InvalidResponseType
                .BLOCK_SLOT_NOT_IN_RANGE
                .describe());
  }

  @Test
  void blockSlotIsCorrect() {
    UInt64 START_SLOT = UInt64.valueOf(1);
    UInt64 COUNT = UInt64.valueOf(4);
    UInt64 STEP = UInt64.valueOf(2);
    listenerWrapper = new BlocksByRangeListenerWrapper(peer, listener, START_SLOT, COUNT, STEP);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(3);
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(7);
    final SignedBeaconBlock block4 = dataStructureUtil.randomSignedBeaconBlock(9);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(block1).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(block2).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(block3).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(block4).join());
  }

  @Test
  void blockSlotDoesNotMatchStep() {
    UInt64 START_SLOT = UInt64.valueOf(1);
    UInt64 COUNT = UInt64.valueOf(4);
    UInt64 STEP = UInt64.valueOf(2);
    listenerWrapper = new BlocksByRangeListenerWrapper(peer, listener, START_SLOT, COUNT, STEP);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    listener.onResponse(block1).join();

    SafeFuture<?> result = listenerWrapper.onResponse(block2);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlocksByRangeResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlocksByRangeResponseInvalidResponseException.InvalidResponseType
                .BLOCK_SLOT_DOES_NOT_MATCH_STEP
                .describe());
  }

  @Test
  void blockSlotGreaterThanToSlot() {
    UInt64 START_SLOT = UInt64.valueOf(1);
    UInt64 COUNT = UInt64.valueOf(4);
    UInt64 STEP = UInt64.valueOf(2);
    // end slot is 9
    listenerWrapper = new BlocksByRangeListenerWrapper(peer, listener, START_SLOT, COUNT, STEP);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(3);
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(7);
    final SignedBeaconBlock block4 = dataStructureUtil.randomSignedBeaconBlock(9);
    final SignedBeaconBlock block5 = dataStructureUtil.randomSignedBeaconBlock(11);
    listenerWrapper.onResponse(block1).join();
    listenerWrapper.onResponse(block2).join();
    listenerWrapper.onResponse(block3).join();
    listenerWrapper.onResponse(block4).join();

    SafeFuture<?> result = listenerWrapper.onResponse(block5);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlocksByRangeResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlocksByRangeResponseInvalidResponseException.InvalidResponseType
                .BLOCK_SLOT_NOT_IN_RANGE
                .describe());
  }

  @Test
  void blockParentRootDoesNotMatch() {
    UInt64 START_SLOT = UInt64.valueOf(1);
    UInt64 COUNT = UInt64.valueOf(4);
    UInt64 STEP = UInt64.valueOf(1);
    // end slot is 9
    listenerWrapper = new BlocksByRangeListenerWrapper(peer, listener, START_SLOT, COUNT, STEP);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);

    listenerWrapper.onResponse(block1).join();

    SafeFuture<?> result = listenerWrapper.onResponse(block2);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlocksByRangeResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlocksByRangeResponseInvalidResponseException.InvalidResponseType
                .BLOCK_PARENT_ROOT_DOES_NOT_MATCH
                .describe());
  }

  @Test
  void blockSlotGreaterThanPreviousBlockSlot() {
    UInt64 START_SLOT = UInt64.valueOf(1);
    UInt64 COUNT = UInt64.valueOf(4);
    UInt64 STEP = UInt64.valueOf(1);
    // end slot is 9
    listenerWrapper = new BlocksByRangeListenerWrapper(peer, listener, START_SLOT, COUNT, STEP);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(1);

    listenerWrapper.onResponse(block1).join();

    SafeFuture<?> result = listenerWrapper.onResponse(block2);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlocksByRangeResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlocksByRangeResponseInvalidResponseException.InvalidResponseType
                .BLOCK_SLOT_NOT_GREATER_THAN_PREVIOUS_BLOCK_SLOT
                .describe());
  }
}
