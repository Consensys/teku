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
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.Constants;

public class Eth1DepositManager {

  private static final Logger LOG = LogManager.getLogger();

  private final Eth1Provider eth1Provider;
  private final AsyncRunner asyncRunner;
  private final Eth1EventsChannel eth1EventsChannel;
  private final DepositProcessingController depositProcessingController;
  private final MinimumGenesisTimeBlockFinder minimumGenesisTimeBlockFinder;

  public Eth1DepositManager(
      Eth1Provider eth1Provider,
      AsyncRunner asyncRunner,
      Eth1EventsChannel eth1EventsChannel,
      DepositProcessingController depositProcessingController,
      MinimumGenesisTimeBlockFinder minimumGenesisTimeBlockFinder) {
    this.eth1Provider = eth1Provider;
    this.asyncRunner = asyncRunner;
    this.eth1EventsChannel = eth1EventsChannel;
    this.depositProcessingController = depositProcessingController;
    this.minimumGenesisTimeBlockFinder = minimumGenesisTimeBlockFinder;
  }

  public void start() {
    getHead()
        .thenCompose(
            headBlock -> {
              if (isBlockAfterMinGenesis(headBlock)) {
                return headAfterMinGenesisMode(headBlock);
              } else {
                return headBeforeMinGenesisMode(headBlock);
              }
            })
        .finish(
            () -> LOG.info("Eth1DepositsManager successfully ran startup sequence."),
            (err) -> LOG.fatal("Eth1DepositsManager unable to run startup sequence.", err));
  }

  public void stop() {
    depositProcessingController.stopIfSubscribed();
  }

  private SafeFuture<Void> headBeforeMinGenesisMode(EthBlock.Block headBlock) {
    LOG.debug("Eth1DepositsManager initiating head before genesis mode");
    BigInteger headBlockNumber = headBlock.getNumber();
    return depositProcessingController
        .fetchDepositsFromGenesisTo(headBlockNumber)
        .thenRun(
            () -> {
              depositProcessingController.switchToBlockByBlockMode();
              depositProcessingController.startSubscription(headBlockNumber.add(BigInteger.ONE));
            });
  }

  private SafeFuture<Void> headAfterMinGenesisMode(EthBlock.Block headBlock) {
    LOG.debug("Eth1DepositsManager initiating head after genesis mode");
    return minimumGenesisTimeBlockFinder
        .findMinGenesisTimeBlockInHistory(headBlock)
        .thenCompose(this::sendDepositsUpToMinGenesis)
        .thenAccept(
            minGenesisTimeBlock -> {
              notifyMinGenesisTimeBlockReached(eth1EventsChannel, minGenesisTimeBlock);
              depositProcessingController.startSubscription(minGenesisTimeBlock.getNumber());
            });
  }

  private SafeFuture<EthBlock.Block> sendDepositsUpToMinGenesis(
      final EthBlock.Block minGenesisTimeBlock) {
    return depositProcessingController
        .fetchDepositsFromGenesisTo(minGenesisTimeBlock.getNumber())
        .thenApply(__ -> minGenesisTimeBlock);
  }

  private SafeFuture<EthBlock.Block> getHead() {
    return eth1Provider
        .getLatestEth1BlockFuture()
        .thenApply(EthBlock.Block::getNumber)
        .thenApply(number -> number.subtract(Constants.ETH1_FOLLOW_DISTANCE.bigIntegerValue()))
        .thenApply(UnsignedLong::valueOf)
        .thenCompose(eth1Provider::getGuaranteedEth1BlockFuture)
        .exceptionallyCompose(
            (err) -> {
              LOG.warn(
                  "Eth1DepositManager failed to get the head of Eth1. Retrying in {} seconds.",
                  Constants.ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT);

              return asyncRunner
                  .getDelayedFuture(Constants.ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT, TimeUnit.SECONDS)
                  .thenCompose((__) -> getHead());
            });
  }
}
