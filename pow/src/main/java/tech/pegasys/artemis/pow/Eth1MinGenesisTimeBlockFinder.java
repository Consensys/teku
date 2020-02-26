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

import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;

import com.google.common.primitives.UnsignedLong;
import io.reactivex.disposables.Disposable;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.artemis.pow.api.MinGenesisTimeBlockEventChannel;
import tech.pegasys.artemis.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

public class Eth1MinGenesisTimeBlockFinder {

  private static final Logger LOG = LogManager.getLogger();

  private final Eth1Provider eth1Provider;
  private final MinGenesisTimeBlockEventChannel minGenesisTimeBlockEventChannel;
  private final AsyncRunner asyncRunner;

  private volatile Disposable latestBlockDisposable;

  public Eth1MinGenesisTimeBlockFinder(
      Eth1Provider eth1Provider,
      MinGenesisTimeBlockEventChannel minGenesisTimeBlockEventChannel,
      AsyncRunner asyncRunner) {
    this.eth1Provider = eth1Provider;
    this.minGenesisTimeBlockEventChannel = minGenesisTimeBlockEventChannel;
    this.asyncRunner = asyncRunner;
  }

  public void start() {
    findAndPublishFirstValidBlock()
        .finish(
            () ->
                LOG.info(
                    "Eth1MinGenesisBlockFinder successfully found first "
                        + "(time) valid genesis block"));
  }

  public SafeFuture<Void> findAndPublishFirstValidBlock() {
    return eth1Provider
        .getLatestEth1BlockFuture()
        .thenApply(EthBlock.Block::getNumber)
        .thenApply(number -> number.subtract(Constants.ETH1_FOLLOW_DISTANCE.bigIntegerValue()))
        .thenApply(UnsignedLong::valueOf)
        .thenCompose(eth1Provider::getEth1BlockFuture)
        .thenCompose(
            block -> {
              int comparison = compareBlockTimestampToMinGenesisTime(block);
              if (comparison > 0) {
                // If block timestamp is greater than min genesis time
                // find first valid block in history
                return findFirstValidBlockInHistory(block);
              } else if (comparison < 0) {
                // If block timestamp is less than min genesis time
                // subscribe to new block events and wait for the first
                // valid block
                return waitForFirstValidBlock();
              } else {
                return SafeFuture.completedFuture(block);
              }
            })
        .thenAccept(this::publishFirstValidBlock)
        .exceptionallyCompose(
            err -> {
              if (latestBlockDisposable != null) {
                latestBlockDisposable.dispose();
              }

              LOG.debug(
                  "Eth1MinGenesisTimeBlockFinder failed to find first valid block. Retry in "
                      + Constants.ETH1_MIN_GENESIS_TIME_BLOCK_RETRY_TIMEOUT
                      + " seconds",
                  err);

              System.out.println("adding one delayed run");
              return asyncRunner.runAfterDelay(
                  this::findAndPublishFirstValidBlock,
                  Constants.ETH1_MIN_GENESIS_TIME_BLOCK_RETRY_TIMEOUT,
                  TimeUnit.SECONDS);
            });
  }

  private void publishFirstValidBlock(EthBlock.Block block) {
    minGenesisTimeBlockEventChannel.onMinGenesisTimeBlock(
        new MinGenesisTimeBlockEvent(
            UnsignedLong.valueOf(block.getTimestamp()),
            UnsignedLong.valueOf(block.getNumber()),
            Bytes32.fromHexString(block.getHash())));
  }

  /**
   * Find first valid block in history that has timestamp greater than MIN_GENESIS_TIME
   *
   * @param estimationBlock estimationBlock that will be used for estimation
   * @return first valid block in history
   */
  private SafeFuture<EthBlock.Block> findFirstValidBlockInHistory(EthBlock.Block estimationBlock) {
    UnsignedLong estimatedFirstValidBlockNumber =
        getEstimatedFirstValidBlockNumber(estimationBlock, Constants.SECONDS_PER_ETH1_BLOCK);
    return eth1Provider
        .getEth1BlockFuture(estimatedFirstValidBlockNumber)
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
            // then previous block must have been the first valid block.
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
            // then current block must be the first valid block.
            return SafeFuture.completedFuture(block);
          } else {
            // If exploring upwards and block timestamp < min genesis time,
            // then previous block must have been the first valid block.
            return exploreBlocksUpwards(block);
          }
        });
  }

  /**
   * Subscribes to latest block events and sees if the latest canonical block, i.e. the block at
   * follow distance, is the first valid block.
   *
   * @return first valid block
   */
  private SafeFuture<EthBlock.Block> waitForFirstValidBlock() {
    SafeFuture<EthBlock.Block> firstValidBlockFuture = new SafeFuture<>();

    latestBlockDisposable =
        eth1Provider
            .getLatestBlockFlowable()
            .map(EthBlock.Block::getNumber)
            .map(number -> number.subtract(ETH1_FOLLOW_DISTANCE.bigIntegerValue()))
            .map(UnsignedLong::valueOf)
            .subscribe(
                blockNumber -> onNewBlock(blockNumber, firstValidBlockFuture),
                firstValidBlockFuture::completeExceptionally);

    return firstValidBlockFuture;
  }

  private void onNewBlock(
      UnsignedLong blockNumber, SafeFuture<EthBlock.Block> firstValidBlockFuture) {

    eth1Provider
        .getEth1BlockFuture(blockNumber)
        .thenAccept(
            block -> {
              if (compareBlockTimestampToMinGenesisTime(block) >= 0) {
                firstValidBlockFuture.complete(block);
                latestBlockDisposable.dispose();
              }
            })
        .finish(() -> {}, firstValidBlockFuture::completeExceptionally);
  }

  // TODO: this function changes a tiny bit in 10.1
  static UnsignedLong calculateCandidateGenesisTimestamp(BigInteger eth1Timestamp) {
    UnsignedLong timestamp = UnsignedLong.valueOf(eth1Timestamp);
    return timestamp
        .minus(timestamp.mod(UnsignedLong.valueOf(Constants.SECONDS_PER_DAY)))
        .plus(UnsignedLong.valueOf(2 * Constants.SECONDS_PER_DAY));
  }

  /**
   * Given that blockTimestamp is greater than min genesis time, find the first valid block
   *
   * @param block that is going to be used for estimation
   * @param secondsPerEth1Block seconds per Eth1 Block
   * @return estimated block number of first valid block
   */
  private static UnsignedLong getEstimatedFirstValidBlockNumber(
      EthBlock.Block block, UnsignedLong secondsPerEth1Block) {
    UnsignedLong blockNumber = UnsignedLong.valueOf(block.getNumber());
    UnsignedLong blockTimestamp = UnsignedLong.valueOf(block.getTimestamp());
    UnsignedLong timeDiff =
        calculateCandidateGenesisTimestamp(blockTimestamp.bigIntegerValue())
            .minus(Constants.MIN_GENESIS_TIME);
    UnsignedLong blockNumberDiff = timeDiff.dividedBy(secondsPerEth1Block);
    return blockNumber.minus(blockNumberDiff);
  }

  private static int compareBlockTimestampToMinGenesisTime(EthBlock.Block block) {
    return calculateCandidateGenesisTimestamp(block.getTimestamp())
        .compareTo(Constants.MIN_GENESIS_TIME);
  }
}
