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

import static tech.pegasys.artemis.pow.MinimumGenesisTimeBlockFinder.calculateCandidateGenesisTimestamp;
import static tech.pegasys.artemis.pow.MinimumGenesisTimeBlockFinder.notifyMinGenesisTimeBlockReached;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;

import com.google.common.primitives.UnsignedLong;
import io.reactivex.disposables.Disposable;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.artemis.pow.api.Eth1EventsChannel;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

public class DepositProcessingController {

  private static final Logger LOG = LogManager.getLogger();

  private final Eth1Provider eth1Provider;
  private final Eth1EventsChannel eth1EventsChannel;
  private final AsyncRunner asyncRunner;
  private final DepositFetcher depositFetcher;

  private Disposable newBlockSubscription;
  private volatile boolean active = false;

  // BlockByBlock mode is used to request deposit events and block information for each block
  private volatile boolean isBlockByBlockModeOn = false;

  private BigInteger latestSuccessfullyQueriedBlock = BigInteger.ZERO;
  private BigInteger latestCanonicalBlockNumber = BigInteger.ZERO;

  public DepositProcessingController(
      Eth1Provider eth1Provider,
      Eth1EventsChannel eth1EventsChannel,
      AsyncRunner asyncRunner,
      DepositFetcher depositFetcher) {
    this.eth1Provider = eth1Provider;
    this.eth1EventsChannel = eth1EventsChannel;
    this.asyncRunner = asyncRunner;
    this.depositFetcher = depositFetcher;
  }

  public synchronized void switchToBlockByBlockMode() {
    LOG.debug("Switching to blockByBlock mode");
    isBlockByBlockModeOn = true;
  }

  // inclusive of start block
  public synchronized void startSubscription(BigInteger subscriptionStartBlock) {
    LOG.debug("Starting subscription at block {}", subscriptionStartBlock);
    latestSuccessfullyQueriedBlock = subscriptionStartBlock.subtract(BigInteger.ONE);
    newBlockSubscription =
        eth1Provider
            .getLatestBlockFlowable()
            .map(EthBlock.Block::getNumber)
            .map(number -> number.subtract(ETH1_FOLLOW_DISTANCE.bigIntegerValue()))
            .subscribe(this::onNewCanonicalBlockNumber, this::onSubscriptionFailed);
  }

  public void stopIfSubscribed() {
    if (newBlockSubscription != null) {
      newBlockSubscription.dispose();
    }
  }

  // Inclusive
  public synchronized SafeFuture<Void> fetchDepositsFromGenesisTo(BigInteger toBlockNumber) {
    return depositFetcher.fetchDepositsInRange(BigInteger.ZERO, toBlockNumber);
  }

  private synchronized void onSubscriptionFailed(Throwable err) {
    Disposable subscription = newBlockSubscription;
    if (subscription != null) {
      subscription.dispose();
    }
    LOG.warn("New block subscription failed, retrying.", err);
    asyncRunner
        .getDelayedFuture(Constants.ETH1_SUBSCRIPTION_RETRY_TIMEOUT, TimeUnit.SECONDS)
        .finish(
            () -> startSubscription(latestSuccessfullyQueriedBlock),
            (error) ->
                LOG.warn(
                    "Unable to subscribe to the Eth1Node. Node won't "
                        + "have access to new deposits after genesis.",
                    error));
  }

  private synchronized void onNewCanonicalBlockNumber(BigInteger latestCanonicalBlockNumber) {
    if (latestCanonicalBlockNumber.compareTo(this.latestCanonicalBlockNumber) <= 0) {
      return;
    }
    this.latestCanonicalBlockNumber = latestCanonicalBlockNumber;
    fetchLatestSubscriptionDeposits();
  }

  private synchronized void fetchLatestSubscriptionDeposits() {
    if (isBlockByBlockModeOn) {
      fetchLatestDepositsOneBlockAtATime();
    } else fetchLatestSubscriptionDepositsOverRange();
  }

  private synchronized void fetchLatestSubscriptionDepositsOverRange() {
    final BigInteger toBlock;
    final BigInteger fromBlock;

    if (active || latestCanonicalBlockNumber.equals(latestSuccessfullyQueriedBlock)) {
      return;
    }
    active = true;

    fromBlock = latestSuccessfullyQueriedBlock.add(BigInteger.ONE);
    toBlock = latestCanonicalBlockNumber;

    depositFetcher
        .fetchDepositsInRange(fromBlock, toBlock)
        .finish(
            __ -> onSubscriptionDepositRequestSuccessful(toBlock),
            (err) -> onSubscriptionDepositRequestFailed(err, fromBlock, toBlock));
  }

  private synchronized void fetchLatestDepositsOneBlockAtATime() {
    final BigInteger nextBlockNumber;

    synchronized (DepositProcessingController.this) {
      if (active || latestCanonicalBlockNumber.equals(latestSuccessfullyQueriedBlock)) {
        return;
      }
      active = true;

      nextBlockNumber = latestSuccessfullyQueriedBlock.add(BigInteger.ONE);
    }

    depositFetcher
        .fetchDepositsInRange(nextBlockNumber, nextBlockNumber)
        .thenCompose(
            __ ->
                eth1Provider.getGuaranteedEth1BlockFuture(
                    UnsignedLong.valueOf(nextBlockNumber), asyncRunner))
        .thenAccept(
            block -> {
              final BigInteger blockNumber = block.getNumber();
              LOG.trace("Successfully fetched block {} for min genesis checking", blockNumber);
              LOG.trace(
                  "Seconds until min genesis block {}",
                  Constants.MIN_GENESIS_TIME.minus(
                      calculateCandidateGenesisTimestamp(block.getTimestamp())));
              if (MinimumGenesisTimeBlockFinder.compareBlockTimestampToMinGenesisTime(block) >= 0) {
                notifyMinGenesisTimeBlockReached(eth1EventsChannel, block);
                isBlockByBlockModeOn = false;
                LOG.debug("Switching back to fetching deposits by range");
              }
            })
        .finish(
            __ -> onSubscriptionDepositRequestSuccessful(nextBlockNumber),
            (err) -> onSubscriptionDepositRequestFailed(err, nextBlockNumber));
  }

  private synchronized void onSubscriptionDepositRequestSuccessful(BigInteger requestToBlock) {
    active = false;
    latestSuccessfullyQueriedBlock = requestToBlock;

    if (latestCanonicalBlockNumber.compareTo(latestSuccessfullyQueriedBlock) > 0) {
      fetchLatestSubscriptionDeposits();
    }
  }

  private synchronized void onSubscriptionDepositRequestFailed(
      Throwable err, BigInteger fromBlock) {
    onSubscriptionDepositRequestFailed(err, fromBlock, fromBlock);
  }

  private synchronized void onSubscriptionDepositRequestFailed(
      Throwable err, BigInteger fromBlock, BigInteger toBlock) {
    active = false;

    if (!fromBlock.equals(toBlock)) {
      LOG.warn(
          "Failed to fetch deposit events for block numbers in the range ({}, {})",
          fromBlock,
          toBlock,
          err);
    } else {
      LOG.warn("Failed to fetch deposit events for block: {}", fromBlock);
    }

    asyncRunner
        .getDelayedFuture(Constants.ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT, TimeUnit.SECONDS)
        .finish(
            this::fetchLatestSubscriptionDeposits,
            (error) -> LOG.warn("Unable to execute delayed request. Dropping request", error));
  }
}
