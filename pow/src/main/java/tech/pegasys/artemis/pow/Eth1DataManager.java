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

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;
import static tech.pegasys.artemis.util.config.Constants.ETH1_REQUEST_BUFFER;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_ETH1_BLOCK;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.math.LongMath;
import com.google.common.primitives.UnsignedLong;
import java.math.RoundingMode;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.artemis.pow.event.CacheEth1BlockEvent;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.time.TimeProvider;

/*

Eth1Data management strays from the spec to enable a cache for quick access to
Eth1Data without having spiky batches of requests. Below is the definition of how we
define such a robust cache that is the superset of the range defined by the spec.

Definitions:
  t: current time
  Request Buffer: the time it will take for potential eth1 data request to complete

Constants:
  Eth1 Follow Distance Time = Seconds Per Eth1 Block * Eth1 Follow Distance
  Slots Per Eth1 Voting Period Time = Seconds Per Slot * Slots Per Eth1 Voting Period

cache range =
((t - (Slots Per Eth1 Voting Period Time) - (Eth1 Follow Distance Time * 2),
(t - (Eth1 Follow Distance Time) + Request Buffer))

At startup: (implemented in this class)
  - Find the cache range
  - Search Eth1 blocks to find blocks in the cache range (pseudo-code defined below)

On every Slot Event: (implemented in this class)
  - Get the latest block number you have
  - Calculate upper bound, i.e.  (t - (Eth1 Follow Distance Time) + Request Buffer))
  - Request blocks from i = 0 to  i = to infinity, until latest block number + i’s timestamp
  is greater than the upper bound

On every VotingPeriodStart change: (implemented in Eth1DataCache)
  - Prune anything that is before than:
  ((t - (Slots Per Eth1 Voting Period Time - One Slot Time) - (Eth1 Follow Distance Time * 2)

Search Eth1 Blocks to find blocks in the cache range:
  1) Get the latest block’s time stamp and block number
  2) rcr_average = ((rcr_lower_bound + rcr_upper_bound) / 2)
  3) time_diff = latest_block_timestamp - rcr_average
  4) seconds_per_eth1_block = SECONDS_PER_ETH1_BLOCK
  5) block_number_diff = time_diff / seconds_per_eth1_block
  6) block_number = latest_block_number - block_number_diff
  7) block_timestamp = getEthBlock(block_number).timestamp
  8) if isTimestampInRCR(block_timestamp):
      - go in both directions until you’re not in the range
      - post each block to event bus
     else:
      - actual_time_diff = latest_block_timestamp - block_timestamp
      - seconds_per_eth1_block = block_number_diff / actual_time_diff
      - go back to step 5

 */

public class Eth1DataManager {

  private static final Logger LOG = LogManager.getLogger();

  private final Eth1Provider eth1Provider;
  private final DepositContractAccessor depositContractAccessor;
  private final EventBus eventBus;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;

  private AtomicReference<EthBlock.Block> latestBlockReference = new AtomicReference<>();
  private AtomicInteger cacheStartupRetry = new AtomicInteger(0);
  private AtomicBoolean cacheStartupDone = new AtomicBoolean(false);

  public Eth1DataManager(
      Eth1Provider eth1Provider,
      EventBus eventBus,
      DepositContractAccessor depositContractAccessor,
      AsyncRunner asyncRunner,
      TimeProvider timeProvider) {
    this.eth1Provider = eth1Provider;
    this.eventBus = eventBus;
    this.depositContractAccessor = depositContractAccessor;
    this.asyncRunner = asyncRunner;
    this.timeProvider = timeProvider;
  }

  public void start() {
    doCacheStartup()
        .finish(
            () -> {
              LOG.info("Eth1DataManager successfully ran cache startup logic");
              cacheStartupDone.set(true);
              eventBus.register(this);
            });
  }

  public void stop() {
    eventBus.unregister(this);
  }

  @Subscribe
  public void onTick(Date date) {

    // Fetch new Eth1 blocks every SECONDS_PER_ETH1_BLOCK seconds
    // (can't use slot events here as an approximation due to this needing to be run pre-genesis)
    if (!hasBeenApproximately(SECONDS_PER_ETH1_BLOCK, timeProvider.getTimeInSeconds())) {
      return;
    }

    EthBlock.Block latestBlock = latestBlockReference.get();
    UnsignedLong latestTimestamp = UnsignedLong.valueOf(latestBlock.getTimestamp());

    // Don't get newer blocks if the timestamp of the last block fetched is
    // still higher than the range upper bound
    if (latestTimestamp.compareTo(getCacheRangeUpperBound()) > 0) {
      return;
    }

    UnsignedLong latestBlockNumber = UnsignedLong.valueOf(latestBlock.getNumber());
    exploreBlocksInDirection(latestBlockNumber, true).reportExceptions();
  }

  private SafeFuture<Void> doCacheStartup() {
    final UnsignedLong cacheMidRangeTimestamp =
        getCacheMidRangeTimestamp(timeProvider.getTimeInSeconds());

    SafeFuture<EthBlock.Block> latestEthBlockFuture = eth1Provider.getLatestEth1BlockFuture();

    SafeFuture<UnsignedLong> latestBlockTimestampFuture =
        getBlockTimestampFuture(latestEthBlockFuture);
    SafeFuture<UnsignedLong> latestBlockNumberFuture = getBlockNumberFuture(latestEthBlockFuture);

    SafeFuture<UnsignedLong> approximatedBlockNumberDiff =
        getApproximatedBlockNumberDiffWithMidRangeBlock(
            latestBlockTimestampFuture,
            SafeFuture.completedFuture(SECONDS_PER_ETH1_BLOCK),
            cacheMidRangeTimestamp);

    SafeFuture<EthBlock.Block> blockFuture =
        getMidRangeBlock(latestBlockNumberFuture, approximatedBlockNumberDiff);

    return blockFuture
        .thenCompose(
            block -> {
              UnsignedLong timestamp = UnsignedLong.valueOf(block.getTimestamp());
              SafeFuture<EthBlock.Block> middleBlockFuture = blockFuture;
              if (!isTimestampInRange(timestamp)) {

                SafeFuture<UnsignedLong> realSecondsPerEth1BlockFuture =
                    calculateRealSecondsPerEth1BlockFuture(
                        latestBlockTimestampFuture, approximatedBlockNumberDiff, timestamp);

                SafeFuture<UnsignedLong> newBlockNumberDiffFuture =
                    getApproximatedBlockNumberDiffWithMidRangeBlock(
                        latestBlockTimestampFuture,
                        realSecondsPerEth1BlockFuture,
                        cacheMidRangeTimestamp);

                middleBlockFuture =
                    getMidRangeBlock(latestBlockNumberFuture, newBlockNumberDiffFuture);
              }
              return middleBlockFuture;
            })
        .thenCompose(
            block -> {
              latestBlockReference.set(block);
              UnsignedLong middleBlockNumber = UnsignedLong.valueOf(block.getNumber());
              postCacheEth1BlockEvent(middleBlockNumber, block).reportExceptions();
              SafeFuture<Void> exploreUpResultFuture =
                  exploreBlocksInDirection(middleBlockNumber, true);
              SafeFuture<Void> exploreDownResultFuture =
                  exploreBlocksInDirection(middleBlockNumber, false);
              return SafeFuture.allOf(exploreUpResultFuture, exploreDownResultFuture);
            })
        .exceptionallyCompose(
            err -> {
              if (cacheStartupRetry.incrementAndGet()
                  == Constants.ETH1_CACHE_STARTUP_RETRY_GIVEUP) {
                return SafeFuture.failedFuture(
                    new RuntimeException(
                        "Eth1DataManager aborted due to multiple failed startup attempts"));
              }

              LOG.debug(
                  "Eth1DataManager failed to run cache startup logic. Retry in "
                      + Constants.ETH1_CACHE_STARTUP_RETRY_TIMEOUT
                      + " seconds",
                  err);

              return asyncRunner.runAfterDelay(
                  this::doCacheStartup,
                  Constants.ETH1_CACHE_STARTUP_RETRY_TIMEOUT,
                  TimeUnit.SECONDS);
            });
  }

  private SafeFuture<Void> exploreBlocksInDirection(
      UnsignedLong blockNumber, final boolean isDirectionUp) {
    blockNumber =
        isDirectionUp ? blockNumber.plus(UnsignedLong.ONE) : blockNumber.minus(UnsignedLong.ONE);
    SafeFuture<EthBlock.Block> blockFuture = eth1Provider.getEth1BlockFuture(blockNumber);
    UnsignedLong finalBlockNumber = blockNumber;
    return blockFuture.thenCompose(
        block -> {
          if (isDirectionUp) latestBlockReference.set(block);
          UnsignedLong timestamp = UnsignedLong.valueOf(block.getTimestamp());
          postCacheEth1BlockEvent(finalBlockNumber, block).reportExceptions();
          if (isTimestampInRange(timestamp)) {
            return exploreBlocksInDirection(finalBlockNumber, isDirectionUp);
          }
          return SafeFuture.completedFuture(null);
        });
  }

  SafeFuture<UnsignedLong> calculateRealSecondsPerEth1BlockFuture(
      SafeFuture<UnsignedLong> latestBlockTimestampFuture,
      SafeFuture<UnsignedLong> blockNumberDiffFuture,
      UnsignedLong blockTimestamp) {
    return SafeFuture.allOf(latestBlockTimestampFuture, blockNumberDiffFuture)
        .thenApply(
            done -> {
              UnsignedLong blockNumberDiff = blockNumberDiffFuture.getNow(null);
              checkNotNull(blockNumberDiff);
              UnsignedLong latestBlockTimestamp = latestBlockTimestampFuture.getNow(null);
              checkNotNull(latestBlockTimestamp);

              UnsignedLong actualTimeDiff = latestBlockTimestamp.minus(blockTimestamp);
              return UnsignedLong.valueOf(
                  LongMath.divide(
                      actualTimeDiff.longValue(),
                      blockNumberDiff.longValue(),
                      RoundingMode.HALF_UP));
            });
  }

  private SafeFuture<EthBlock.Block> getMidRangeBlock(
      SafeFuture<UnsignedLong> latestBlockNumberFuture,
      SafeFuture<UnsignedLong> blockNumberDiffFuture) {
    return SafeFuture.allOf(latestBlockNumberFuture, blockNumberDiffFuture)
        .thenCompose(
            done -> {
              UnsignedLong latestBlockNumber = latestBlockNumberFuture.getNow(null);
              checkNotNull(latestBlockNumber);
              UnsignedLong blockNumberDiff = blockNumberDiffFuture.getNow(null);
              checkNotNull(blockNumberDiff);

              return eth1Provider.getEth1BlockFuture(latestBlockNumber.minus(blockNumberDiff));
            });
  }

  private boolean isTimestampInRange(UnsignedLong timestamp) {
    return timestamp.compareTo(getCacheRangeLowerBound()) >= 0
        && timestamp.compareTo(getCacheRangeUpperBound()) <= 0;
  }

  private SafeFuture<UnsignedLong> getBlockTimestampFuture(SafeFuture<EthBlock.Block> blockFuture) {
    return blockFuture.thenApply(ethBlock -> UnsignedLong.valueOf(ethBlock.getTimestamp()));
  }

  private SafeFuture<UnsignedLong> getBlockNumberFuture(SafeFuture<EthBlock.Block> blockFuture) {
    return blockFuture.thenApply(ethBlock -> UnsignedLong.valueOf(ethBlock.getNumber()));
  }

  SafeFuture<UnsignedLong> getApproximatedBlockNumberDiffWithMidRangeBlock(
      SafeFuture<UnsignedLong> latestBlockTimestampFuture,
      SafeFuture<UnsignedLong> secondsPerEth1BlockFuture,
      UnsignedLong cacheMidRangeTimestamp) {
    return SafeFuture.allOf(latestBlockTimestampFuture, secondsPerEth1BlockFuture)
        .thenApply(
            done -> {
              UnsignedLong secondsPerEth1Block = secondsPerEth1BlockFuture.getNow(null);
              checkNotNull(secondsPerEth1Block);

              UnsignedLong latestBlockTimestamp = latestBlockTimestampFuture.getNow(null);
              checkNotNull(latestBlockTimestamp);

              if (latestBlockTimestamp.compareTo(cacheMidRangeTimestamp) < 0) {
                throw new RuntimeException(
                    "Latest block timestamp is less than the cache mid-range");
              }
              UnsignedLong timeDiff = latestBlockTimestamp.minus(cacheMidRangeTimestamp);
              return UnsignedLong.valueOf(
                  LongMath.divide(
                      timeDiff.longValue(), secondsPerEth1Block.longValue(), RoundingMode.HALF_UP));
            });
  }

  private SafeFuture<Void> postCacheEth1BlockEvent(UnsignedLong blockNumber, EthBlock.Block block) {
    SafeFuture<UnsignedLong> countFuture =
        SafeFuture.of(depositContractAccessor.getDepositCount(blockNumber));
    SafeFuture<Bytes32> rootFuture =
        SafeFuture.of(depositContractAccessor.getDepositRoot(blockNumber));

    return SafeFuture.allOf(countFuture, rootFuture)
        .thenRun(
            () -> {
              Bytes32 root = rootFuture.getNow(null);
              checkNotNull(root);

              UnsignedLong count = countFuture.getNow(null);
              checkNotNull(count);

              Bytes32 eth1BlockHash = Bytes32.fromHexString(block.getHash());
              UnsignedLong eth1BlockTimestamp = UnsignedLong.valueOf(block.getTimestamp());
              UnsignedLong eth1BlockNumber = UnsignedLong.valueOf(block.getNumber());

              eventBus.post(
                  new CacheEth1BlockEvent(
                      eth1BlockNumber, eth1BlockHash, eth1BlockTimestamp, root, count));
            });
  }

  public static UnsignedLong getCacheRangeLowerBound(UnsignedLong currentTime) {
    return currentTime
        .minus(UnsignedLong.valueOf(SLOTS_PER_ETH1_VOTING_PERIOD * SECONDS_PER_SLOT))
        .minus(ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK).times(UnsignedLong.valueOf(2)));
  }

  public UnsignedLong getCacheRangeLowerBound() {
    return getCacheRangeLowerBound(timeProvider.getTimeInSeconds());
  }

  public static UnsignedLong getCacheRangeUpperBound(UnsignedLong currentTime) {
    return currentTime
        .minus(ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK))
        .plus(ETH1_REQUEST_BUFFER);
  }

  UnsignedLong getCacheRangeUpperBound() {
    return getCacheRangeUpperBound(timeProvider.getTimeInSeconds());
  }

  public static UnsignedLong getCacheMidRangeTimestamp(UnsignedLong currentTime) {
    return UnsignedLong.valueOf(
        LongMath.divide(
            getCacheRangeUpperBound(currentTime)
                .plus(getCacheRangeLowerBound(currentTime))
                .longValue(),
            2,
            RoundingMode.HALF_UP));
  }

  public static boolean hasBeenApproximately(UnsignedLong seconds, UnsignedLong currentTime) {
    return currentTime.mod(seconds).equals(UnsignedLong.ZERO);
  }
}
