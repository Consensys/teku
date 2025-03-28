/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.beacon.pow;

import static tech.pegasys.teku.beacon.pow.MinimumGenesisTimeBlockFinder.calculateCandidateGenesisTimestamp;
import static tech.pegasys.teku.beacon.pow.MinimumGenesisTimeBlockFinder.notifyMinGenesisTimeBlockReached;

import com.google.common.base.Throwables;
import java.math.BigInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.ethereum.pow.api.InvalidDepositEventsException;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.config.SpecConfig;

public class DepositProcessingController {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecConfig config;
  private final Eth1Provider eth1Provider;
  private final Eth1EventsChannel eth1EventsChannel;
  private final AsyncRunner asyncRunner;
  private final DepositFetcher depositFetcher;
  private final Eth1HeadTracker headTracker;

  private long newBlockSubscription;
  private boolean active = false;

  // BlockByBlock mode is used to request deposit events and block information for each block
  private boolean isBlockByBlockModeOn = false;

  private BigInteger latestSuccessfullyQueriedBlock = BigInteger.ZERO;
  private BigInteger latestCanonicalBlockNumber = BigInteger.ZERO;

  private final Eth1BlockFetcher eth1BlockFetcher;

  public DepositProcessingController(
      final SpecConfig config,
      final Eth1Provider eth1Provider,
      final Eth1EventsChannel eth1EventsChannel,
      final AsyncRunner asyncRunner,
      final DepositFetcher depositFetcher,
      final Eth1BlockFetcher eth1BlockFetcher,
      final Eth1HeadTracker headTracker) {
    this.config = config;
    this.eth1Provider = eth1Provider;
    this.eth1EventsChannel = eth1EventsChannel;
    this.asyncRunner = asyncRunner;
    this.depositFetcher = depositFetcher;
    this.eth1BlockFetcher = eth1BlockFetcher;
    this.headTracker = headTracker;
  }

  public synchronized void switchToBlockByBlockMode() {
    LOG.debug("Switching to blockByBlock mode");
    isBlockByBlockModeOn = true;
  }

  // inclusive of start block
  public synchronized void startSubscription(final BigInteger subscriptionStartBlock) {
    LOG.debug("Starting subscription at block {}", subscriptionStartBlock);
    latestSuccessfullyQueriedBlock = subscriptionStartBlock.subtract(BigInteger.ONE);
    newBlockSubscription = headTracker.subscribe(this::onNewCanonicalBlockNumber);
  }

  public void stopIfSubscribed() {
    headTracker.unsubscribe(newBlockSubscription);
  }

  // inclusive
  public synchronized SafeFuture<Void> fetchDepositsInRange(
      final BigInteger fromBlockNumber, final BigInteger toBlockNumber) {
    return depositFetcher.fetchDepositsInRange(fromBlockNumber, toBlockNumber);
  }

  private synchronized void onNewCanonicalBlockNumber(final UInt64 newCanonicalBlockNumber) {
    this.latestCanonicalBlockNumber = newCanonicalBlockNumber.bigIntegerValue();
    fetchLatestSubscriptionDeposits();
  }

  private void fetchLatestSubscriptionDeposits() {
    if (isBlockByBlockModeOn) {
      fetchLatestDepositsOneBlockAtATime();
    } else {
      fetchLatestSubscriptionDepositsOverRange();
    }
  }

  private synchronized void fetchLatestSubscriptionDepositsOverRange() {
    if (isActiveOrAlreadyQueriedLatestCanonicalBlock()) {
      return;
    }
    active = true;

    final BigInteger fromBlock = latestSuccessfullyQueriedBlock.add(BigInteger.ONE);
    final BigInteger toBlock = latestCanonicalBlockNumber;

    depositFetcher
        .fetchDepositsInRange(fromBlock, toBlock)
        .finish(
            __ -> onSubscriptionDepositRequestSuccessful(toBlock),
            (err) -> onSubscriptionDepositRequestFailed(err, fromBlock, toBlock));
  }

  private synchronized void fetchLatestDepositsOneBlockAtATime() {
    final BigInteger nextBlockNumber;

    synchronized (DepositProcessingController.this) {
      if (isActiveOrAlreadyQueriedLatestCanonicalBlock()) {
        return;
      }
      active = true;

      nextBlockNumber = latestSuccessfullyQueriedBlock.add(BigInteger.ONE);
    }

    depositFetcher
        .fetchDepositsInRange(nextBlockNumber, nextBlockNumber)
        .thenCompose(__ -> eth1Provider.getGuaranteedEth1Block(UInt64.valueOf(nextBlockNumber)))
        .thenAccept(
            block -> {
              final BigInteger blockNumber = block.getNumber();
              LOG.trace("Successfully fetched block {} for min genesis checking", blockNumber);
              if (MinimumGenesisTimeBlockFinder.compareBlockTimestampToMinGenesisTime(config, block)
                  >= 0) {
                notifyMinGenesisTimeBlockReached(eth1EventsChannel, block);
                isBlockByBlockModeOn = false;
                LOG.debug(
                    "Minimum genesis time block reached, switching back to fetching deposits by range");
              } else {
                LOG.trace(
                    "Seconds until min genesis block {}",
                    config
                        .getMinGenesisTime()
                        .minus(calculateCandidateGenesisTimestamp(config, block.getTimestamp())));
              }
            })
        .finish(
            __ -> onSubscriptionDepositRequestSuccessful(nextBlockNumber),
            (err) -> onSubscriptionDepositRequestFailed(err, nextBlockNumber));
  }

  /**
   * Avoid having multiple queries running concurrently or queries that attempt to go backwards.
   *
   * @return true if request to fetch deposits should be ignored.
   */
  private boolean isActiveOrAlreadyQueriedLatestCanonicalBlock() {
    return active || latestCanonicalBlockNumber.compareTo(latestSuccessfullyQueriedBlock) <= 0;
  }

  private synchronized void onSubscriptionDepositRequestSuccessful(
      final BigInteger requestToBlock) {
    active = false;
    latestSuccessfullyQueriedBlock = requestToBlock;
    if (latestCanonicalBlockNumber.compareTo(latestSuccessfullyQueriedBlock) > 0) {
      fetchLatestSubscriptionDeposits();
    } else {
      // We've caught up with deposits all the way up to the follow distance
      eth1BlockFetcher.onInSync(UInt64.valueOf(latestCanonicalBlockNumber));
    }
  }

  private synchronized void onSubscriptionDepositRequestFailed(
      final Throwable err, final BigInteger fromBlock) {
    onSubscriptionDepositRequestFailed(err, fromBlock, fromBlock);
  }

  private synchronized void onSubscriptionDepositRequestFailed(
      final Throwable err, final BigInteger fromBlock, final BigInteger toBlock) {
    active = false;

    if (Throwables.getRootCause(err) instanceof InvalidDepositEventsException) {
      throw new RuntimeException(err);
    }

    LOG.warn(
        "Failed to fetch deposit events for block numbers in the range ({}, {}). Retrying.",
        fromBlock,
        toBlock,
        err);

    asyncRunner
        .getDelayedFuture(Constants.ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT)
        .finish(
            this::fetchLatestSubscriptionDeposits,
            (error) -> LOG.warn("Unable to execute delayed request. Dropping request", error));
  }
}
