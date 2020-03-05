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

package tech.pegasys.artemis.pow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

public class MinimumGenesisTimeBlockFinderTest {

  private Eth1Provider eth1Provider;

  private MinimumGenesisTimeBlockFinder minimumGenesisTimeBlockFinder;

  @BeforeEach
  void setUp() {
    eth1Provider = mock(Eth1Provider.class);

    minimumGenesisTimeBlockFinder = new MinimumGenesisTimeBlockFinder(eth1Provider);

    Constants.MIN_GENESIS_DELAY = 1;
    // calculateCandidateGenesisTimestamp will return
    // blockTime + 2
  }

  @AfterAll
  static void tearDown() {
    Constants.setConstants("minimal");
  }

  @Test
  void minGenesisBlock_belowEstimatedBlock() {
    Constants.SECONDS_PER_ETH1_BLOCK = UnsignedLong.valueOf(5);

    EthBlock.Block estimationBlock = mockBlockForEth1Provider("0xbf", 1000, 1000);

    setMinGenesisTime(500);

    // 1002 - 502 = 500, 500 / 5 = 100, the estimated genesis block number should be:
    // 1000 - 100 = 900

    mockBlockForEth1Provider("0x11", 900, 600);

    // since the estimated block still had higher timestamp than min genesis, we should explore
    // downwards

    mockBlockForEth1Provider("0x08", 899, 510);

    // since the second requested block still had higher timestamp than min genesis, we should
    // explore downwards

    mockBlockForEth1Provider("0x00", 898, 490);

    // since the last requested block now had lower timestamp than min genesis, we should publish
    // the block
    // right before this as the first valid block

    EthBlock.Block minGenesisTimeBlock =
        minimumGenesisTimeBlockFinder.findMinGenesisTimeBlockInHistory(estimationBlock).join();

    assertThatIsBlock(minGenesisTimeBlock, "0x08", 899, 510);
  }

  @Test
  void minGenesisBlock_AboveEstimatedBlock() {
    Constants.SECONDS_PER_ETH1_BLOCK = UnsignedLong.valueOf(5);

    //    mockLatestCanonicalBlock(1000);
    EthBlock.Block estimationBlock = mockBlockForEth1Provider("0xbf", 1000, 1000);

    setMinGenesisTime(500);

    // 1002 - 502 = 500, 500 / 5 = 100, the estimated genesis block number should be:
    // 1000 - 100 = 900

    mockBlockForEth1Provider("0x08", 900, 400);

    // since the estimated block still had lower timestamp than min genesis, we should explore
    // upwards

    mockBlockForEth1Provider("0x08", 901, 450);

    // since the second requested block still had lower timestamp than min genesis, we should
    // explore upwards

    mockBlockForEth1Provider("0x08", 902, 510);

    // since the last requested block now had higher timestamp than min genesis, we should publish
    // the block

    EthBlock.Block minGenesisTimeBlock =
        minimumGenesisTimeBlockFinder.findMinGenesisTimeBlockInHistory(estimationBlock).join();

    assertThatIsBlock(minGenesisTimeBlock, "0x08", 902, 510);
  }

  @Test
  void minGenesisBlock_EstimatedBlockIsTheValidBlock() {
    Constants.SECONDS_PER_ETH1_BLOCK = UnsignedLong.valueOf(5);

    //    mockLatestCanonicalBlock(1000);
    EthBlock.Block estimationBlock = mockBlockForEth1Provider("0xbf", 1000, 1000);

    setMinGenesisTime(502);

    // 1002 - 502 = 500, 500 / 5 = 100, the estimated genesis block number should be:
    // 1000 - 100 = 900

    mockBlockForEth1Provider("0x08", 900, 500);

    // since the genesis time calculated from the , we should publish the block

    EthBlock.Block minGenesisTimeBlock =
        minimumGenesisTimeBlockFinder.findMinGenesisTimeBlockInHistory(estimationBlock).join();

    assertThatIsBlock(minGenesisTimeBlock, "0x08", 900, 500);
  }

  private EthBlock.Block mockBlockForEth1Provider(
      String blockHash, long blockNumber, long timestamp) {
    EthBlock.Block block = mock(EthBlock.Block.class);
    when(block.getTimestamp()).thenReturn(BigInteger.valueOf(timestamp));
    when(block.getNumber()).thenReturn(BigInteger.valueOf(blockNumber));
    when(block.getHash()).thenReturn(blockHash);
    when(eth1Provider.getEth1BlockFuture(UnsignedLong.valueOf(blockNumber)))
        .thenReturn(SafeFuture.completedFuture(block));
    return block;
  }

  private void assertThatIsBlock(
      EthBlock.Block block,
      final String expectedBlockHash,
      final long expectedBlockNumber,
      final long expectedTimestamp) {
    assertThat(block.getTimestamp().longValue()).isEqualTo(expectedTimestamp);
    assertThat(block.getNumber().longValue()).isEqualTo(expectedBlockNumber);
    assertThat(block.getHash()).isEqualTo(expectedBlockHash);
  }

  private void setMinGenesisTime(long time) {
    Constants.MIN_GENESIS_TIME = UnsignedLong.valueOf(time);
  }
}
