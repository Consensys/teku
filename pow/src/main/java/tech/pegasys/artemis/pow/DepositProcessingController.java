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

import static tech.pegasys.artemis.pow.Eth1Manager.compareBlockTimestampToMinGenesisTime;
import static tech.pegasys.artemis.pow.Eth1Manager.postMinGenesisTimeBlock;
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
  private final DepositsFetcher depositsFetcher;

  private volatile Disposable newBlockSubscription;
  private boolean active = false;

  // BlockByBlock mode is used to request deposit events and block information for each block
  private boolean isBlockByBlockModeOn = false;
  private volatile BigInteger blockByBlockLastFetchedBlockNumber = BigInteger.ZERO;

  private BigInteger latestCanonicalBlockNumber = BigInteger.ZERO;

  public DepositProcessingController(
      Eth1Provider eth1Provider,
      Eth1EventsChannel eth1EventsChannel,
      AsyncRunner asyncRunner,
      DepositsFetcher depositsFetcher) {
    this.eth1Provider = eth1Provider;
    this.eth1EventsChannel = eth1EventsChannel;
    this.asyncRunner = asyncRunner;
    this.depositsFetcher = depositsFetcher;
  }

  public void switchToBlockByBlockMode() {
    isBlockByBlockModeOn = true;
  }

  // inclusive of start block
  public void startSubscription(BigInteger subscriptionStartBlock) {
    depositsFetcher.setLatestFetchedBlockNumber(subscriptionStartBlock.subtract(BigInteger.ONE));
    newBlockSubscription =
        eth1Provider
            .getLatestBlockFlowable()
            .map(EthBlock.Block::getNumber)
            .map(number -> number.subtract(ETH1_FOLLOW_DISTANCE.bigIntegerValue()))
            .subscribe(this::onNewCanonicalBlockNumber, this::onSubscriptionFailed);
  }

  public void stopSubscription() {
    newBlockSubscription.dispose();
  }

  // Inclusive
  public SafeFuture<Void> fetchDepositsFromGenesisTo(BigInteger toBlockNumber) {
    depositsFetcher.fetchDepositsInRange(BigInteger.ZERO, toBlockNumber);
  }

  private void onSubscriptionFailed(Throwable err) {
    Disposable subscription = newBlockSubscription;
    if (subscription != null) {
      subscription.dispose();
    }
    LOG.warn("New block subscription failed, retrying.", err);
    asyncRunner
        .getDelayedFuture(Constants.ETH1_SUBSCRIPTION_RETRY_TIMEOUT, TimeUnit.SECONDS)
        .finish(
            () -> startSubscription(depositsFetcher.getLatestFetchedBlockNumber()),
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
      blockByBlockLastFetchedBlockNumber = depositsFetcher.getLatestFetchedBlockNumber();
      fetchLatestDepositsOneBlockAtATime();
    } else fetchLatestSubscriptionDepositsOverRange();
  }

  private synchronized void fetchLatestSubscriptionDepositsOverRange() {
    final BigInteger toBlock;
    final BigInteger fromBlock;

    synchronized (DepositProcessingController.this) {
      if (active
          || latestCanonicalBlockNumber.equals(depositsFetcher.getLatestFetchedBlockNumber())) {
        return;
      }
      active = true;

      fromBlock = depositsFetcher.getLatestFetchedBlockNumber().add(BigInteger.ONE);
      toBlock = this.latestCanonicalBlockNumber;
    }

    depositsFetcher
        .fetchDepositsInRange(fromBlock, toBlock)
        .finish(
            this::onSubscriptionDepositRequestSuccessful,
            (err) -> onSubscriptionDepositRequestFailed(err, fromBlock, toBlock));
  }

  private synchronized void fetchLatestDepositsOneBlockAtATime() {
    final BigInteger nextBlockNumber;

    synchronized (DepositProcessingController.this) {
      if (active || latestCanonicalBlockNumber.equals(blockByBlockLastFetchedBlockNumber)) {
        return;
      }
      active = true;

      nextBlockNumber = blockByBlockLastFetchedBlockNumber.add(BigInteger.ONE);
    }

    depositsFetcher
        .fetchDepositsInRange(nextBlockNumber, nextBlockNumber)
        .thenCompose((__) -> eth1Provider.getEth1BlockFuture(UnsignedLong.valueOf(nextBlockNumber)))
        .thenAccept(
            block -> {
              if (compareBlockTimestampToMinGenesisTime(block) >= 0) {
                postMinGenesisTimeBlock(eth1EventsChannel, block);
                isBlockByBlockModeOn = false;
              }
              blockByBlockLastFetchedBlockNumber = block.getNumber();
            })
        .finish(
            this::onSubscriptionDepositRequestSuccessful,
            (err) -> onSubscriptionDepositRequestFailed(err, nextBlockNumber));
  }

  private synchronized void onSubscriptionDepositRequestSuccessful() {
    active = false;

    if (latestCanonicalBlockNumber.compareTo(depositsFetcher.getLatestFetchedBlockNumber()) > 0) {
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
