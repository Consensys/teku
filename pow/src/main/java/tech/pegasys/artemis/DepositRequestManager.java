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

package tech.pegasys.artemis;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;

import com.google.common.primitives.UnsignedLong;
import io.reactivex.disposables.Disposable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.util.async.SafeFuture;

public class DepositRequestManager {

  private final Web3j web3j;
  private final Consumer<DepositsFromBlockEvent> depositsFromBlockEventConsumer;
  private final DepositContract depositContract;
  private static final Logger LOG = LogManager.getLogger();

  private boolean active = false;
  private boolean requestQueued = false;
  private BigInteger latestCanonicalBlockNumber;
  private BigInteger latestSuccessfullyQueriedBlockNumber = BigInteger.ZERO;
  private Disposable newBlockSubscription;

  public DepositRequestManager(
      Web3j web3j,
      Consumer<DepositsFromBlockEvent> depositsFromBlockEventConsumer,
      DepositContract depositContract) {
    this.web3j = web3j;
    this.depositsFromBlockEventConsumer = depositsFromBlockEventConsumer;
    this.depositContract = depositContract;
  }

  public void start() {
    newBlockSubscription =
        web3j
            .blockFlowable(false)
            .map(EthBlock::getBlock)
            .map(EthBlock.Block::getNumber)
            .map(number -> number.subtract(ETH1_FOLLOW_DISTANCE.bigIntegerValue()))
            .subscribe(
                this::updateLatestCanonicalBlockNumber,
                err -> {
                  LOG.warn("New block subscription failed, retrying.", err);
                  start();
                });
  }

  public void stop() {
    newBlockSubscription.dispose();
  }

  private synchronized void updateLatestCanonicalBlockNumber(
      BigInteger latestCanonicalBlockNumber) {
    this.latestCanonicalBlockNumber = latestCanonicalBlockNumber;
    if (active) {
      requestQueued = true;
    } else {
      active = true;
      getLatestDeposits();
    }
  }

  private void getLatestDeposits() {
    requestQueued = false;

    DefaultBlockParameter fromBlock =
        DefaultBlockParameter.valueOf(latestSuccessfullyQueriedBlockNumber);
    final BigInteger latestCanonicalBlockNumberAtRequestStart = this.latestCanonicalBlockNumber;
    DefaultBlockParameter toBlock =
        DefaultBlockParameter.valueOf(latestCanonicalBlockNumberAtRequestStart);

    LOG.trace(
        "Attempting to fetch deposit events for block numbers in the range ({}, {})",
        latestSuccessfullyQueriedBlockNumber,
        latestCanonicalBlockNumberAtRequestStart);

    fetchAndPublishDepositEventsInBlockRange(fromBlock, toBlock)
        .finish(
            () -> {
              this.latestSuccessfullyQueriedBlockNumber = latestCanonicalBlockNumberAtRequestStart;
              synchronized (DepositRequestManager.this) {
                active = false;
                if (requestQueued) {
                  getLatestDeposits();
                }
              }
            },
            (err) -> {
              LOG.warn(
                  "Failed to fetch deposit events for block numbers in the range ({},{}): {}",
                  latestSuccessfullyQueriedBlockNumber,
                  latestCanonicalBlockNumberAtRequestStart,
                  err);
              // TODO: add delayed processing
              getLatestDeposits();
            });
  }

  private SafeFuture<Void> fetchAndPublishDepositEventsInBlockRange(
      DefaultBlockParameter fromBlock, DefaultBlockParameter toBlock) {
    SafeFuture<List<DepositContract.DepositEventEventResponse>> eventsFuture =
        depositContract.depositEventEventsInRange(fromBlock, toBlock);

    return eventsFuture
        .thenApply(this::groupDepositEventResponsesByBlockNumber)
        .thenCompose(
            groupedDepositEventResponses -> {
              Map<String, SafeFuture<EthBlock.Block>> neededBlocksByHash =
                  getMapOfEthBlockFutures(groupedDepositEventResponses);
              return SafeFuture.allOf(neededBlocksByHash.values().toArray(SafeFuture[]::new))
                  .thenApply(
                      done ->
                          constructDepositsFromBlockEvents(
                              neededBlocksByHash, groupedDepositEventResponses));
            })
        .thenAccept(
            (depositsFromBlockEventList) ->
                depositsFromBlockEventList.stream()
                    .sorted(Comparator.comparing(DepositsFromBlockEvent::getBlockNumber))
                    .forEachOrdered(this::publishDeposits));
  }

  private List<DepositsFromBlockEvent> constructDepositsFromBlockEvents(
      Map<String, SafeFuture<EthBlock.Block>> blockFutureByBlockHash,
      List<List<DepositContract.DepositEventEventResponse>> groupedDepositEventResponses) {
    return groupedDepositEventResponses.stream()
        .map(
            groupedDepositEventResponse -> {
              String blockHash = groupedDepositEventResponse.get(0).log.getBlockHash();
              EthBlock.Block block =
                  checkNotNull(blockFutureByBlockHash.get(blockHash).getNow(null));
              return new DepositsFromBlockEvent(
                  UnsignedLong.valueOf(block.getNumber()),
                  Bytes32.fromHexString(block.getHash()),
                  UnsignedLong.valueOf(block.getTimestamp()),
                  groupedDepositEventResponse.stream()
                      .map(Deposit::new)
                      .collect(Collectors.toList()));
            })
        .collect(Collectors.toList());
  }

  private Map<String, SafeFuture<EthBlock.Block>> getMapOfEthBlockFutures(
      List<List<DepositContract.DepositEventEventResponse>> groupedDepositEventResponses) {
    return groupedDepositEventResponses.stream()
        .map(groupOfSameBlockDeposits -> groupOfSameBlockDeposits.get(0).log.getBlockHash())
        .collect(Collectors.toUnmodifiableMap(k -> k, this::getEthBlockFuture));
  }

  // TODO: pass in web3j as an argument and share this code with Eth1DataManager to avoid
  // duplication
  private SafeFuture<EthBlock.Block> getEthBlockFuture(String blockHash) {
    return SafeFuture.of(web3j.ethGetBlockByHash(blockHash, false).sendAsync())
        .thenApply(EthBlock::getBlock);
  }

  private List<List<DepositContract.DepositEventEventResponse>>
      groupDepositEventResponsesByBlockNumber(
          List<DepositContract.DepositEventEventResponse> events) {
    return new ArrayList<>(
        events.stream()
            .collect(
                Collectors.groupingBy(
                    event -> event.log.getBlockNumber(), TreeMap::new, Collectors.toList()))
            .values());
  }

  private void publishDeposits(DepositsFromBlockEvent event) {
    depositsFromBlockEventConsumer.accept(event);
  }
}
