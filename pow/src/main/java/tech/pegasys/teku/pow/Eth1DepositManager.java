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

import static tech.pegasys.teku.pow.MinimumGenesisTimeBlockFinder.isBlockAfterMinGenesis;
import static tech.pegasys.teku.pow.MinimumGenesisTimeBlockFinder.notifyMinGenesisTimeBlockReached;

import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.schema.ReplayDepositsResult;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.Constants;

public class Eth1DepositManager {

  private static final Logger LOG = LogManager.getLogger();

  private final Eth1Provider eth1Provider;
  private final AsyncRunner asyncRunner;
  private final Eth1EventsChannel eth1EventsChannel;
  private final Eth1DepositStorageChannel eth1DepositStorageChannel;
  private final DepositProcessingController depositProcessingController;
  private final MinimumGenesisTimeBlockFinder minimumGenesisTimeBlockFinder;

  public Eth1DepositManager(
      final Eth1Provider eth1Provider,
      final AsyncRunner asyncRunner,
      final Eth1EventsChannel eth1EventsChannel,
      final Eth1DepositStorageChannel eth1DepositStorageChannel,
      final DepositProcessingController depositProcessingController,
      final MinimumGenesisTimeBlockFinder minimumGenesisTimeBlockFinder) {
    this.eth1Provider = eth1Provider;
    this.asyncRunner = asyncRunner;
    this.eth1EventsChannel = eth1EventsChannel;
    this.eth1DepositStorageChannel = eth1DepositStorageChannel;
    this.depositProcessingController = depositProcessingController;
    this.minimumGenesisTimeBlockFinder = minimumGenesisTimeBlockFinder;
  }

  public void start() {
    eth1DepositStorageChannel
        .replayDepositEvents()
        .thenCompose(
            replayDepositsResult ->
                getHead().thenCompose(headBlock -> processStart(headBlock, replayDepositsResult)))
        .finish(
            () -> LOG.info("Eth1DepositsManager successfully ran startup sequence."),
            (err) -> LOG.fatal("Eth1DepositsManager unable to run startup sequence.", err));
  }

  public void stop() {
    depositProcessingController.stopIfSubscribed();
  }

  private SafeFuture<Void> processStart(
      final EthBlock.Block headBlock, final ReplayDepositsResult replayDepositsResult) {
    BigInteger startBlockNumber = replayDepositsResult.getBlockNumber();
    if (headBlock.getNumber().compareTo(startBlockNumber) > 0) {
      if (isBlockAfterMinGenesis(headBlock)) {
        return headAfterMinGenesisMode(headBlock, replayDepositsResult);
      } else {
        return headBeforeMinGenesisMode(headBlock, startBlockNumber);
      }
    }

    if (replayDepositsResult.isPastMinGenesisBlock()) {
      depositProcessingController.startSubscription(startBlockNumber);
    } else {
      preGenesisSubscription(startBlockNumber);
    }
    return SafeFuture.COMPLETE;
  }

  private SafeFuture<Void> headBeforeMinGenesisMode(
      final EthBlock.Block headBlock, final BigInteger startBlockNumber) {
    LOG.debug("Eth1DepositsManager initiating head before genesis mode");
    BigInteger headBlockNumber = headBlock.getNumber();
    if (startBlockNumber.compareTo(headBlockNumber) < 0) {
      return depositProcessingController
          .fetchDepositsInRange(startBlockNumber, headBlockNumber)
          .thenRun(() -> preGenesisSubscription(headBlockNumber));
    }

    preGenesisSubscription(headBlockNumber);
    return SafeFuture.COMPLETE;
  }

  private void preGenesisSubscription(final BigInteger headBlockNumber) {
    depositProcessingController.switchToBlockByBlockMode();
    depositProcessingController.startSubscription(headBlockNumber.add(BigInteger.ONE));
  }

  private SafeFuture<Void> headAfterMinGenesisMode(
      final EthBlock.Block headBlock, final ReplayDepositsResult replayDepositsResult) {
    LOG.debug("Eth1DepositsManager initiating head after genesis mode");

    if (replayDepositsResult.isPastMinGenesisBlock()) {
      depositProcessingController.startSubscription(replayDepositsResult.getBlockNumber());
      return SafeFuture.COMPLETE;
    }

    return minimumGenesisTimeBlockFinder
        .findMinGenesisTimeBlockInHistory(headBlock)
        .thenCompose(block -> sendDepositsUpToMinGenesis(block, replayDepositsResult))
        .thenAccept(
            minGenesisTimeBlock -> {
              notifyMinGenesisTimeBlockReached(eth1EventsChannel, minGenesisTimeBlock);
              depositProcessingController.startSubscription(minGenesisTimeBlock.getNumber());
            });
  }

  private SafeFuture<EthBlock.Block> sendDepositsUpToMinGenesis(
      final EthBlock.Block minGenesisTimeBlock, final ReplayDepositsResult replayDepositsResult) {
    return depositProcessingController
        .fetchDepositsInRange(
            replayDepositsResult.getBlockNumber(), minGenesisTimeBlock.getNumber())
        .thenApply(__ -> minGenesisTimeBlock);
  }

  private SafeFuture<EthBlock.Block> getHead() {
    return eth1Provider
        .getLatestEth1Block()
        .thenApply(EthBlock.Block::getNumber)
        .thenApply(number -> number.subtract(Constants.ETH1_FOLLOW_DISTANCE.bigIntegerValue()))
        .thenApply(UnsignedLong::valueOf)
        .thenCompose(eth1Provider::getGuaranteedEth1Block)
        .exceptionallyCompose(
            (err) -> {
              LOG.debug(
                  "Eth1DepositManager failed to get the head of Eth1: {}. Retrying in {} seconds.",
                  err.getMessage(),
                  Constants.ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT,
                  err);

              return asyncRunner
                  .getDelayedFuture(Constants.ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT, TimeUnit.SECONDS)
                  .thenCompose((__) -> getHead());
            });
  }
}
