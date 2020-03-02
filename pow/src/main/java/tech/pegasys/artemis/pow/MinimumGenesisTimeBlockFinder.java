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

import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.artemis.pow.api.Eth1EventsChannel;
import tech.pegasys.artemis.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

public class MinimumGenesisTimeBlockFinder {

  final Eth1Provider eth1Provider;

  public MinimumGenesisTimeBlockFinder(Eth1Provider eth1Provider) {
    this.eth1Provider = eth1Provider;
  }

  /**
   * Find first block in history that has timestamp greater than MIN_GENESIS_TIME
   *
   * @param estimationBlock estimationBlock that will be used for estimation
   * @return min genesis time block
   */
  public SafeFuture<EthBlock.Block> findMinGenesisTimeBlockInHistory(
      EthBlock.Block estimationBlock) {
    UnsignedLong estimatedBlockNumber =
        getEstimatedMinGenesisTimeBlockNumber(estimationBlock, Constants.SECONDS_PER_ETH1_BLOCK);
    return eth1Provider
        .getEth1BlockFuture(estimatedBlockNumber)
        .thenCompose(
            block -> {
              int comparison = compareBlockTimestampToMinGenesisTime(block);
              if (comparison > 0) {
                // If block timestamp is greater than min genesis time
                // explore blocks downwards
                return exploreBlocksDownwards(block);
              } else if (comparison < 0) {
                // If block timestamp is less than min genesis time
                // explore blocks upwards
                return exploreBlocksUpwards(block);
              } else {
                return SafeFuture.completedFuture(block);
              }
            });
  }

  private SafeFuture<EthBlock.Block> exploreBlocksDownwards(EthBlock.Block previousBlock) {
    if (previousBlock.getNumber().equals(BigInteger.ZERO)) {
      throw new RuntimeException(
          "Reached Eth1Genesis before reaching a valid min Eth2 genesis time, "
              + "MIN_GENESIS_TIME constant must be wrong");
    }
    UnsignedLong previousBlockNumber = UnsignedLong.valueOf(previousBlock.getNumber());
    UnsignedLong newBlockNumber = previousBlockNumber.minus(UnsignedLong.ONE);
    SafeFuture<EthBlock.Block> blockFuture = eth1Provider.getEth1BlockFuture(newBlockNumber);
    return blockFuture.thenCompose(
        block -> {
          int comparison = compareBlockTimestampToMinGenesisTime(block);
          if (comparison > 0) {
            // If exploring downwards and block timestamp > min genesis time,
            // then block must still be downwards.
            return exploreBlocksDownwards(block);
          } else if (comparison < 0) {
            // If exploring downwards and block timestamp < min genesis time,
            // then previous block must have been the min genesis time block.
            return SafeFuture.completedFuture(previousBlock);
          } else {
            return SafeFuture.completedFuture(block);
          }
        });
  }

  private SafeFuture<EthBlock.Block> exploreBlocksUpwards(EthBlock.Block previousBlock) {
    UnsignedLong previousBlockNumber = UnsignedLong.valueOf(previousBlock.getNumber());
    UnsignedLong newBlockNumber = previousBlockNumber.plus(UnsignedLong.ONE);
    SafeFuture<EthBlock.Block> blockFuture = eth1Provider.getEth1BlockFuture(newBlockNumber);
    return blockFuture.thenCompose(
        block -> {
          int comparison = compareBlockTimestampToMinGenesisTime(block);
          if (comparison >= 0) {
            // If exploring upwards and block timestamp >= min genesis time,
            // then current block must be the min genesis time block.
            return SafeFuture.completedFuture(block);
          } else {
            // If exploring upwards and block timestamp < min genesis time,
            // then previous block must have been the min genesis time block.
            return exploreBlocksUpwards(block);
          }
        });
  }

  // TODO: this function changes a tiny bit in 10.1
  private static UnsignedLong calculateCandidateGenesisTimestamp(BigInteger eth1Timestamp) {
    UnsignedLong timestamp = UnsignedLong.valueOf(eth1Timestamp);
    return timestamp
        .minus(timestamp.mod(UnsignedLong.valueOf(Constants.SECONDS_PER_DAY)))
        .plus(UnsignedLong.valueOf(2 * Constants.SECONDS_PER_DAY));
  }

  /**
   * Given that blockTimestamp is greater than min genesis time, find the min genesis time block
   *
   * @param block that is going to be used for estimation
   * @param secondsPerEth1Block seconds per Eth1 Block
   * @return estimated block number of min genesis time block
   */
  private static UnsignedLong getEstimatedMinGenesisTimeBlockNumber(
      EthBlock.Block block, UnsignedLong secondsPerEth1Block) {
    UnsignedLong blockNumber = UnsignedLong.valueOf(block.getNumber());
    UnsignedLong blockTimestamp = UnsignedLong.valueOf(block.getTimestamp());
    UnsignedLong timeDiff =
        calculateCandidateGenesisTimestamp(blockTimestamp.bigIntegerValue())
            .minus(Constants.MIN_GENESIS_TIME);
    UnsignedLong blockNumberDiff = timeDiff.dividedBy(secondsPerEth1Block);
    return blockNumber.minus(blockNumberDiff);
  }

  public static int compareBlockTimestampToMinGenesisTime(EthBlock.Block block) {
    return calculateCandidateGenesisTimestamp(block.getTimestamp())
        .compareTo(Constants.MIN_GENESIS_TIME);
  }

  public static Boolean isBlockBeforeMinGenesis(EthBlock.Block block) {
    int comparison = compareBlockTimestampToMinGenesisTime(block);
    // If block timestamp is greater than min genesis time,
    // min genesis block must have been in history
    return comparison >= 0;
  }

  public static EthBlock.Block notifyMinGenesisTimeBlockReached(
      Eth1EventsChannel eth1EventsChannel, EthBlock.Block block) {
    eth1EventsChannel.onMinGenesisTimeBlock(
        new MinGenesisTimeBlockEvent(
            UnsignedLong.valueOf(block.getTimestamp()),
            UnsignedLong.valueOf(block.getNumber()),
            Bytes32.fromHexString(block.getHash())));
    return block;
  }
}
