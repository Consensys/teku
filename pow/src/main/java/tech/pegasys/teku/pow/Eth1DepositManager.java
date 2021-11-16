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

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.pow.MinimumGenesisTimeBlockFinder.isBlockAfterMinGenesis;
import static tech.pegasys.teku.pow.MinimumGenesisTimeBlockFinder.notifyMinGenesisTimeBlockReached;

import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.schema.ReplayDepositsResult;
import tech.pegasys.teku.util.config.Constants;

public class Eth1DepositManager {

  private static final Logger LOG = LogManager.getLogger();

  private final Eth1Provider eth1Provider;
  private final AsyncRunner asyncRunner;
  private final ValidatingEth1EventsPublisher eth1EventsPublisher;
  private final Eth1DepositStorageChannel eth1DepositStorageChannel;
  private final DepositProcessingController depositProcessingController;
  private final MinimumGenesisTimeBlockFinder minimumGenesisTimeBlockFinder;
  private final Optional<UInt64> depositContractDeployBlock;
  private final Eth1HeadTracker headTracker;

  public Eth1DepositManager(
      final Eth1Provider eth1Provider,
      final AsyncRunner asyncRunner,
      final ValidatingEth1EventsPublisher eth1EventsPublisher,
      final Eth1DepositStorageChannel eth1DepositStorageChannel,
      final DepositProcessingController depositProcessingController,
      final MinimumGenesisTimeBlockFinder minimumGenesisTimeBlockFinder,
      final Optional<UInt64> depositContractDeployBlock,
      final Eth1HeadTracker headTracker) {
    this.eth1Provider = eth1Provider;
    this.asyncRunner = asyncRunner;
    this.eth1EventsPublisher = eth1EventsPublisher;
    this.eth1DepositStorageChannel = eth1DepositStorageChannel;
    this.depositProcessingController = depositProcessingController;
    this.minimumGenesisTimeBlockFinder = minimumGenesisTimeBlockFinder;
    this.depositContractDeployBlock = depositContractDeployBlock;
    this.headTracker = headTracker;
  }

  public void start() {
    eth1DepositStorageChannel
        .replayDepositEvents()
        .thenCompose(
            replayDepositsResult -> {
              replayDepositsResult
                  .getLastProcessedDepositIndex()
                  .map(UInt64::valueOf)
                  .ifPresent(eth1EventsPublisher::setLatestPublishedDeposit);
              return getHead()
                  .thenCompose(
                      headBlock ->
                          processStart(headBlock, replayDepositsResult).thenApply(__ -> headBlock));
            })
        .thenAccept(headBlock -> STATUS_LOG.eth1AtHead(headBlock.getNumber()))
        .exceptionally(
            (err) -> {
              throw new FatalServiceFailureException(getClass(), err);
            })
        .reportExceptions();
  }

  public void stop() {
    depositProcessingController.stopIfSubscribed();
  }

  private SafeFuture<Void> processStart(
      final EthBlock.Block headBlock, final ReplayDepositsResult replayDepositsResult) {
    Preconditions.checkArgument(headBlock != null, "eth1 headBlock should be defined");
    Preconditions.checkArgument(
        replayDepositsResult != null, "eth1 replayDepositsResult should be defined");
    BigInteger startBlockNumber = getFirstUnprocessedBlockNumber(replayDepositsResult);
    if (headBlock.getNumber().compareTo(startBlockNumber) >= 0) {
      if (isBlockAfterMinGenesis(headBlock)) {
        return headAfterMinGenesisMode(headBlock, replayDepositsResult);
      } else {
        return headBeforeMinGenesisMode(headBlock, startBlockNumber);
      }
    }

    if (replayDepositsResult.isPastMinGenesisBlock()) {
      depositProcessingController.startSubscription(startBlockNumber);
    } else {
      // preGenesisSubscription starts processing from the next block
      final BigInteger depositContractStart =
          depositContractDeployBlock.orElse(UInt64.ZERO).minusMinZero(1).bigIntegerValue();
      preGenesisSubscription(
          replayDepositsResult.getLastProcessedBlockNumber().max(depositContractStart));
    }
    return SafeFuture.COMPLETE;
  }

  private SafeFuture<Void> headBeforeMinGenesisMode(
      final EthBlock.Block headBlock, final BigInteger startBlockNumber) {
    LOG.debug("Eth1DepositsManager initiating head before genesis mode");
    BigInteger headBlockNumber = headBlock.getNumber();
    // Ensure we have processed all blocks up to and including headBlock
    // preGenesisSubscription will then pick up from the block after headBlock
    if (startBlockNumber.compareTo(headBlockNumber) <= 0) {
      return depositProcessingController
          .fetchDepositsInRange(startBlockNumber, headBlockNumber)
          .thenRun(() -> preGenesisSubscription(headBlockNumber));
    }

    preGenesisSubscription(headBlockNumber);
    return SafeFuture.COMPLETE;
  }

  private void preGenesisSubscription(final BigInteger lastProcessedBlockNumber) {
    depositProcessingController.switchToBlockByBlockMode();
    depositProcessingController.startSubscription(lastProcessedBlockNumber.add(BigInteger.ONE));
  }

  private SafeFuture<Void> headAfterMinGenesisMode(
      final EthBlock.Block headBlock, final ReplayDepositsResult replayDepositsResult) {
    LOG.debug("Eth1DepositsManager initiating head after genesis mode");

    if (replayDepositsResult.isPastMinGenesisBlock()) {
      depositProcessingController.startSubscription(
          getFirstUnprocessedBlockNumber(replayDepositsResult));
      return SafeFuture.COMPLETE;
    }

    return minimumGenesisTimeBlockFinder
        .findMinGenesisTimeBlockInHistory(headBlock.getNumber(), asyncRunner)
        .thenCompose(block -> sendDepositsUpToMinGenesis(block, replayDepositsResult))
        .thenAccept(
            minGenesisTimeBlock -> {
              notifyMinGenesisTimeBlockReached(eth1EventsPublisher, minGenesisTimeBlock);
              // Start the subscription from the block after min genesis as we've already processed
              // the min genesis block
              depositProcessingController.startSubscription(
                  minGenesisTimeBlock.getNumber().add(BigInteger.ONE));
            });
  }

  private SafeFuture<EthBlock.Block> sendDepositsUpToMinGenesis(
      final EthBlock.Block minGenesisTimeBlock, final ReplayDepositsResult replayDepositsResult) {
    return depositProcessingController
        .fetchDepositsInRange(
            getFirstUnprocessedBlockNumber(replayDepositsResult), minGenesisTimeBlock.getNumber())
        .thenApply(__ -> minGenesisTimeBlock);
  }

  private SafeFuture<EthBlock.Block> getHead() {
    final SafeFuture<UInt64> headBlockNumberFuture = new SafeFuture<>();
    final long subscriberId = headTracker.subscribe(headBlockNumberFuture::complete);

    return headBlockNumberFuture
        .thenPeek(__ -> headTracker.unsubscribe(subscriberId))
        .thenCompose(eth1Provider::getGuaranteedEth1Block)
        .exceptionallyCompose(
            (err) -> {
              LOG.debug(
                  "Eth1DepositManager failed to get the head of Eth1: {}. Retrying in {} seconds.",
                  err.getMessage(),
                  Constants.ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT.toSeconds(),
                  err);

              return asyncRunner.runAfterDelay(
                  this::getHead, Constants.ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT);
            });
  }

  private BigInteger getFirstUnprocessedBlockNumber(
      final ReplayDepositsResult replayDepositsResult) {
    return replayDepositsResult
        .getFirstUnprocessedBlockNumber()
        .max(depositContractDeployBlock.orElse(UInt64.ZERO).bigIntegerValue());
  }
}
