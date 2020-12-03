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

package tech.pegasys.teku.pow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.exception.FailedToFindMinGenesisBlockException;
import tech.pegasys.teku.util.config.Constants;

public class MinimumGenesisTimeBlockFinderTest {

  private final Eth1Provider eth1Provider = mock(Eth1Provider.class);

  private MinimumGenesisTimeBlockFinder minimumGenesisTimeBlockFinder =
      new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.empty());

  @BeforeAll
  public static void setUp() {
    // Setup so genesis time for a block will be blockTime + 2
    Constants.GENESIS_DELAY = UInt64.valueOf(2);
  }

  @AfterAll
  public static void tearDown() {
    Constants.setConstants("minimal");
  }

  @Test
  public void shouldFindMinGenesisTime() {
    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);
    final int minGenesisTime = 3500;
    final Block expectedMinGenesisTimeBlock = blocks[4];
    assertMinGenesisBlock(blocks, minGenesisTime, expectedMinGenesisTimeBlock);
  }

  @Test
  public void shouldFindMinGenesisTimeWhenDeployBlockSpecified() {
    final UInt64 deployBlock = UInt64.valueOf(2); // Block number
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.of(deployBlock));

    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);

    when(eth1Provider.getEth1Block(argThat((UInt64 argument) -> argument.isLessThan(deployBlock))))
        .thenReturn(SafeFuture.completedFuture(null));
    final int minGenesisTime = 3500;
    final Block expectedMinGenesisTimeBlock = blocks[4];
    assertMinGenesisBlock(blocks, minGenesisTime, expectedMinGenesisTimeBlock);
  }

  @Test
  public void shouldFindMinGenesisTimeWhenDeployBlockSpecified_deployedAfterMinGenesisBlock() {
    final UInt64 deployBlock = UInt64.valueOf(6); // Block number
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.of(deployBlock));

    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);

    when(eth1Provider.getEth1Block(argThat((UInt64 argument) -> argument.isLessThan(deployBlock))))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    final int minGenesisTime = 3500;
    final Block expectedMinGenesisTimeBlock = blocks[6];
    assertMinGenesisBlock(blocks, minGenesisTime, expectedMinGenesisTimeBlock);
  }

  @Test
  public void shouldFindMinGenesisTimeBlockAtVeryStartOfChain() {
    final Block[] blocks = withBlockTimestamps(4000, 5000, 6000, 7000, 8000);
    final int minGenesisTime = 3000;
    final Block expectedMinGenesisTimeBlock = blocks[0];
    assertMinGenesisBlock(blocks, minGenesisTime, expectedMinGenesisTimeBlock);
  }

  @Test
  public void shouldFindMinGenesisTimeBlockAtHeadOfChain() {
    final Block[] blocks = withBlockTimestamps(4000, 5000, 6000, 7000, 8000);
    final int minGenesisTime = 8000;
    final Block expectedMinGenesisTimeBlock = blocks[blocks.length - 1];
    assertMinGenesisBlock(blocks, minGenesisTime, expectedMinGenesisTimeBlock);
  }

  @Test
  public void shouldFindMinGenesisTimeWithExactMatch() {
    final Block[] blocks = withBlockTimestamps(1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000);
    final int minGenesisTime = 3002;
    final Block expectedMinGenesisTimeBlock = blocks[2];
    assertMinGenesisBlock(blocks, minGenesisTime, expectedMinGenesisTimeBlock);
  }

  @Test
  public void
      findMinGenesisTimeBlockInHistory_withMissingBlocks_targetBlockInRecentAvailableRange() {
    final UInt64 deployBlock = UInt64.valueOf(4); // Block number
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.of(deployBlock));

    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);
    // Make historical blocks unavailable
    withUnavailableBlocks(Arrays.copyOfRange(blocks, 0, 6));

    final int minGenesisTime = 6500;
    final Block expectedMinGenesisTimeBlock = blocks[7];
    assertMinGenesisBlock(blocks, minGenesisTime, expectedMinGenesisTimeBlock);
  }

  @Test
  public void
      findMinGenesisTimeBlockInHistory_withMissingBlocks_withMinimalMinGenesisBlockAtBoundaryOfAvailableBlocks() {
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.empty());

    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);
    // Make historical blocks unavailable
    withUnavailableBlocks(Arrays.copyOfRange(blocks, 0, 6));

    final int minGenesisTime = 6000 + Constants.GENESIS_DELAY.intValue();

    final Block expectedMinGenesisTimeBlock = blocks[6];
    assertMinGenesisBlock(blocks, minGenesisTime, expectedMinGenesisTimeBlock);
  }

  @Test
  public void
      findMinGenesisTimeBlockInHistory_withMissingBlocks_withNonMinimalMinGenesisBlockAtBoundaryOfAvailableBlocks() {
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.empty());

    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);
    // Make historical blocks unavailable
    withUnavailableBlocks(Arrays.copyOfRange(blocks, 1, 6));

    final int minGenesisTime = 5500;

    final SafeFuture<Block> result = findMinGenesis(blocks, minGenesisTime);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseInstanceOf(FailedToFindMinGenesisBlockException.class)
        .hasMessageContaining(
            "Failed to retrieve min genesis block.  Check that your eth1 node is fully synced.");
  }

  @Test
  public void findMinGenesisTimeBlockInHistory_withMissingBlocks_minGenesisIsInMissingRange() {
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.empty());

    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);
    // Make historical blocks unavailable
    withUnavailableBlocks(Arrays.copyOfRange(blocks, 1, 6));

    final int minGenesisTime = 2500;

    final SafeFuture<Block> result = findMinGenesis(blocks, minGenesisTime);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseInstanceOf(FailedToFindMinGenesisBlockException.class)
        .hasMessageContaining(
            "Failed to retrieve min genesis block.  Check that your eth1 node is fully synced.");
  }

  @Test
  public void
      findMinGenesisTimeBlockInHistory_withMissingBlocks_withDeployBlockAtEarliestAvailableBlock() {
    final UInt64 deployBlock = UInt64.valueOf(6); // Block number
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.of(deployBlock));

    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);
    // Make historical blocks unavailable
    withUnavailableBlocks(Arrays.copyOfRange(blocks, 1, 6));

    final int minGenesisTime = 5500;
    final Block expectedMinGenesisTimeBlock = blocks[6];
    assertMinGenesisBlock(blocks, minGenesisTime, expectedMinGenesisTimeBlock);
  }

  @Test
  public void findMinGenesisTimeBlockInHistory_withMissingBlocks__withDeployBlockUnavailable() {
    final UInt64 deployBlock = UInt64.valueOf(5); // Block number
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.of(deployBlock));

    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);
    // Make historical blocks unavailable
    withUnavailableBlocks(Arrays.copyOfRange(blocks, 1, 6));

    final int minGenesisTime = 5500;

    final SafeFuture<Block> result = findMinGenesis(blocks, minGenesisTime);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseInstanceOf(FailedToFindMinGenesisBlockException.class)
        .hasMessageContaining(
            "Failed to retrieve min genesis block.  Check that your eth1 node is fully synced.");
  }

  @Test
  public void confirmOrFindMinGenesisBlock_withCorrectMinGenesisBlock() {
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.empty());

    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);
    Constants.MIN_GENESIS_TIME = UInt64.valueOf(3500);

    final SafeFuture<Block> res = minimumGenesisTimeBlockFinder.confirmMinGenesisBlock(blocks[4]);
    assertThat(res).isCompletedWithValue(blocks[4]);
  }

  @Test
  public void confirmOrFindMinGenesisBlock_withCandidateAtEth1Genesis() {
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.empty());

    final long[] timestamps = {1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);
    Constants.MIN_GENESIS_TIME = UInt64.valueOf(500);

    final SafeFuture<Block> res = minimumGenesisTimeBlockFinder.confirmMinGenesisBlock(blocks[0]);
    assertThat(res).isCompletedWithValue(blocks[0]);
  }

  @Test
  public void confirmOrFindMinGenesisBlock_withCandidateAtDeployBlock() {
    final int deployBlock = 3;
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.of(UInt64.valueOf(deployBlock)));

    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);
    Constants.MIN_GENESIS_TIME = UInt64.valueOf(500);

    final SafeFuture<Block> res = minimumGenesisTimeBlockFinder.confirmMinGenesisBlock(blocks[3]);
    assertThat(res).isCompletedWithValue(blocks[3]);
  }

  @Test
  public void confirmOrFindMinGenesisBlock_withParentUnavailable() {
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.empty());

    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);
    withUnavailableBlocks(Arrays.copyOfRange(blocks, 0, 7));
    Constants.MIN_GENESIS_TIME = UInt64.valueOf(6500);

    final SafeFuture<Block> res = minimumGenesisTimeBlockFinder.confirmMinGenesisBlock(blocks[7]);
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get)
        .hasCauseInstanceOf(FailedToFindMinGenesisBlockException.class)
        .hasMessageContaining(
            "Failed to retrieve min genesis block.  Check that your eth1 node is fully synced.");
  }

  @Test
  public void confirmOrFindMinGenesisBlock_withCandidateBlockTooRecent() {
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.empty());

    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);
    withUnavailableBlocks(Arrays.copyOfRange(blocks, 0, 4));
    Constants.MIN_GENESIS_TIME = UInt64.valueOf(3500);

    final SafeFuture<Block> res = minimumGenesisTimeBlockFinder.confirmMinGenesisBlock(blocks[7]);
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get)
        .hasCauseInstanceOf(FailedToFindMinGenesisBlockException.class)
        .hasMessageContaining(
            "Failed to retrieve min genesis block.  Check that your eth1 node is fully synced.");
  }

  private void assertMinGenesisBlock(
      final Block[] blocks, final long minGenesisTime, final Block expectedMinGenesisTimeBlock) {
    Constants.MIN_GENESIS_TIME = UInt64.valueOf(minGenesisTime);
    final SafeFuture<Block> result = findMinGenesis(blocks, minGenesisTime);
    assertThat(result).isCompletedWithValue(expectedMinGenesisTimeBlock);
  }

  private SafeFuture<Block> findMinGenesis(final Block[] blocks, final long minGenesisTime) {
    Constants.MIN_GENESIS_TIME = UInt64.valueOf(minGenesisTime);
    return minimumGenesisTimeBlockFinder.findMinGenesisTimeBlockInHistory(
        blocks[blocks.length - 1].getNumber());
  }

  private Block[] withBlockTimestamps(final long... timestamps) {
    final EthBlock.Block[] blocks = new EthBlock.Block[timestamps.length];
    for (int blockNumber = 0; blockNumber < timestamps.length; blockNumber++) {
      blocks[blockNumber] = block(blockNumber, timestamps[blockNumber]);
    }
    return blocks;
  }

  private void withUnavailableBlocks(final Block[] blocks) {
    for (Block block : blocks) {
      when(eth1Provider.getEth1BlockWithRetry(UInt64.valueOf(block.getNumber())))
          .thenReturn(SafeFuture.completedFuture(Optional.empty()));
      when(eth1Provider.getEth1BlockWithRetry(block.getHash()))
          .thenReturn(SafeFuture.failedFuture(new NoSuchElementException()));
    }
  }

  private EthBlock.Block block(final long blockNumber, final long timestamp) {
    final String blockHash = Long.toString(blockNumber, 10);
    final String parentHash = Long.toString(blockNumber - 1, 10);

    // Create mock block
    final Block block = mock(Block.class);
    when(block.getHash()).thenReturn(blockHash);
    when(block.getParentHash()).thenReturn(parentHash);
    when(block.getTimestamp()).thenReturn(BigInteger.valueOf(timestamp));
    when(block.getNumber()).thenReturn(BigInteger.valueOf(blockNumber));
    when(block.toString()).thenReturn("Block " + blockNumber + " at timestamp " + timestamp);

    // Setup eth1 provider to return block
    when(eth1Provider.getEth1BlockWithRetry(UInt64.valueOf(blockNumber)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(eth1Provider.getEth1BlockWithRetry(blockHash))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    return block;
  }
}
