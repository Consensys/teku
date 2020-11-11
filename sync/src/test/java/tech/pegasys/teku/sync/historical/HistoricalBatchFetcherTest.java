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

package tech.pegasys.teku.sync.historical;

public class HistoricalBatchFetcherTest {
  //  private final ChainBuilder chainBuilder = ChainBuilder.createDefault();
  //
  //  @SuppressWarnings("unchecked")
  //  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
  //  private final Eth2Peer peer = mock(Eth2Peer.class);
  //
  //  public void run_returnAllBlocksOnFirstRequest() {
  //    // Create set of blocks to be retrieved
  //    chainBuilder.generateGenesis();
  //    chainBuilder.generateBlocksUpToSlot(20);
  //    final List<SignedBeaconBlock> blocks = chainBuilder.streamBlocksAndStates(10, 20)
  //      .map(SignedBlockAndState::getBlock)
  //      .collect(Collectors.toList());
  //    final SignedBeaconBlock lastBlock = chainBuilder.getLatestBlockAndState().getBlock();
  //
  //    final HistoricalBatchFetcher fetcher = new HistoricalBatchFetcher(storageUpdateChannel,
  // peer, lastBlock.getSlot(), lastBlock.getRoot(), UInt64.valueOf(blocks.size()), 5);
  //
  //  }
  //
  //  private void setupBlockByRangeResults(List<SignedBeaconBlock> ... blockSets) {
  //    for (List<SignedBeaconBlock> blocks : blockSets) {
  //      final SafeFuture<Void> blocksFuture = new SafeFuture<>();
  //      when(peer.requestBlocksByRange(eq(UInt64.valueOf(10)), eq(UInt64.valueOf(blocks.size())),
  // eq(UInt64.ONE), any()))
  //        .thenReturn(blocksFuture);
  //    }
  //
  //  }
}
