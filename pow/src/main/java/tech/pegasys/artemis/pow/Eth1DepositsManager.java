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

import static tech.pegasys.artemis.pow.MinimumGenesisTimeBlockFinder.isBlockBeforeMinGenesis;
import static tech.pegasys.artemis.pow.MinimumGenesisTimeBlockFinder.notifyMinGenesisTimeBlockReached;

import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.artemis.pow.api.Eth1EventsChannel;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

public class Eth1DepositsManager {

  private static final Logger LOG = LogManager.getLogger();

  private final Eth1Provider eth1Provider;
  private final AsyncRunner asyncRunner;
  private final Eth1EventsChannel eth1EventsChannel;
  private final DepositProcessingController depositProcessingController;
  private final MinimumGenesisTimeBlockFinder minimumGenesisTimeBlockFinder;

  public Eth1DepositsManager(
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
              if (isBlockBeforeMinGenesis(headBlock)) {
                return headBeforeMinGenesisMode(headBlock);
              } else {
                return headAfterMinGenesisMode(headBlock);
              }
            })
        .finish(() -> LOG.trace("Eth1Manager successfully ran startup sequence."));
  }

  public void stop() {
    depositProcessingController.stopIfSubscribed();
  }

  private SafeFuture<Void> headBeforeMinGenesisMode(EthBlock.Block headBlock) {
    BigInteger headBlockNumber = headBlock.getNumber();
    return depositProcessingController
        .fetchDepositsFromGenesisTo(headBlockNumber)
        .thenRun(
            () -> {
              depositProcessingController.switchToBlockByBlockMode();
              depositProcessingController.startSubscription(headBlockNumber);
            });
  }

  private SafeFuture<Void> headAfterMinGenesisMode(EthBlock.Block headBlock) {
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
        .thenCompose(eth1Provider::getEth1BlockFuture)
        .exceptionallyCompose(
            (err) ->
                asyncRunner
                    .getDelayedFuture(
                        Constants.ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT, TimeUnit.SECONDS)
                    .thenCompose((__) -> getHead()));
  }
}
