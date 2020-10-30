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

package tech.pegasys.teku.api.blockselector;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlockSelectorFactoryTest {
  private final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
  private final DataStructureUtil data = new DataStructureUtil();
  final SignedBeaconBlock block = data.randomSignedBeaconBlock(100);

  private final BlockSelectorFactory blockSelectorFactory = new BlockSelectorFactory(client);

  @Test
  public void headSelector_shouldGetBestBlock() throws ExecutionException, InterruptedException {
    when(client.getBestBlock()).thenReturn(Optional.of(block));
    List<SignedBeaconBlock> blockList = blockSelectorFactory.headSelector().getBlock().get();
    verify(client).getBestBlock();
    assertThat(blockList).containsExactly(block);
  }

  @Test
  public void finalizedSelector_shouldGetFinalizedBlock()
      throws ExecutionException, InterruptedException {
    when(client.getFinalizedBlock()).thenReturn(Optional.of(block));
    List<SignedBeaconBlock> blockList = blockSelectorFactory.finalizedSelector().getBlock().get();
    verify(client).getFinalizedBlock();
    assertThat(blockList).containsExactly(block);
  }

  @Test
  public void genesisSelector_shouldGetSlotZero() throws ExecutionException, InterruptedException {
    when(client.getBlockAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    List<SignedBeaconBlock> blockList = blockSelectorFactory.genesisSelector().getBlock().get();
    verify(client).getBlockAtSlotExact(UInt64.ZERO);
    assertThat(blockList).containsExactly(block);
  }

  @Test
  public void blockRootSelector_shouldGetBlockByBlockRoot()
      throws ExecutionException, InterruptedException {
    when(client.getBlockByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    List<SignedBeaconBlock> blockList =
        blockSelectorFactory.forBlockRoot(block.getRoot()).getBlock().get();
    verify(client).getBlockByBlockRoot(block.getRoot());
    assertThat(blockList).containsExactly(block);
  }

  @Test
  public void slotSelector_shouldGetBlockAtSlotExact()
      throws ExecutionException, InterruptedException {
    when(client.getBlockAtSlotExact(block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    List<SignedBeaconBlock> blockList =
        blockSelectorFactory.forSlot(block.getSlot()).getBlock().get();
    verify(client).getBlockAtSlotExact(block.getSlot());
    assertThat(blockList).containsExactly(block);
  }

  @Test
  public void defaultBlockSelector_shouldThrowBadRequestException() {
    assertThrows(BadRequestException.class, () -> blockSelectorFactory.defaultBlockSelector("a"));
  }
}
