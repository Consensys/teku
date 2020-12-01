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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

public class MinimumGenesisTimeBlockFinderTest {

  private final Eth1Provider eth1Provider = mock(Eth1Provider.class);

  private MinimumGenesisTimeBlockFinder minimumGenesisTimeBlockFinder =
      new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.empty());

  @BeforeAll
  static void setUp() {
    // Setup so genesis time for a block will be blockTime + 2
    Constants.GENESIS_DELAY = UInt64.valueOf(2);
  }

  @AfterAll
  static void tearDown() {
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
  void shouldFindMinGenesisTimeWhenDeployBlockSpecified() {
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
  void shouldFindMinGenesisTimeWhenDeployBlockSpecified_deployedAfterMinGenesisBlock() {
    final UInt64 deployBlock = UInt64.valueOf(6); // Block number
    minimumGenesisTimeBlockFinder =
        new MinimumGenesisTimeBlockFinder(eth1Provider, Optional.of(deployBlock));

    final long[] timestamps = {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000};
    final Block[] blocks = withBlockTimestamps(timestamps);

    when(eth1Provider.getEth1Block(argThat((UInt64 argument) -> argument.isLessThan(deployBlock))))
        .thenReturn(SafeFuture.completedFuture(null));
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

  private void assertMinGenesisBlock(
      final Block[] blocks, final long minGenesisTime, final Block expectedMinGenesisTimeBlock) {
    Constants.MIN_GENESIS_TIME = UInt64.valueOf(minGenesisTime);
    final SafeFuture<Block> result =
        minimumGenesisTimeBlockFinder.findMinGenesisTimeBlockInHistory(
            blocks[blocks.length - 1].getNumber());
    assertThat(result).isCompletedWithValue(expectedMinGenesisTimeBlock);
  }

  private Block[] withBlockTimestamps(final long... timestamps) {
    final EthBlock.Block[] blocks = new EthBlock.Block[timestamps.length];
    for (int blockNumber = 0; blockNumber < timestamps.length; blockNumber++) {
      blocks[blockNumber] = block(blockNumber, timestamps[blockNumber]);
    }
    return blocks;
  }

  private EthBlock.Block block(final long blockNumber, final long timestamp) {
    final Block block = mock(Block.class);
    when(block.getTimestamp()).thenReturn(BigInteger.valueOf(timestamp));
    when(block.getNumber()).thenReturn(BigInteger.valueOf(blockNumber));
    when(block.toString()).thenReturn("Block " + blockNumber + " at timestamp " + timestamp);
    when(eth1Provider.getEth1Block(UInt64.valueOf(blockNumber)))
        .thenReturn(SafeFuture.completedFuture(block));
    return block;
  }
}
