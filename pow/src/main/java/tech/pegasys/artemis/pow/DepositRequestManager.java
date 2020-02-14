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
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;

import com.google.common.primitives.UnsignedLong;
import io.reactivex.disposables.Disposable;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.artemis.pow.api.DepositEventChannel;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.contract.DepositContract.DepositEventEventResponse;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

public class DepositRequestManager {

  private final Eth1Provider eth1Provider;
  private final DepositEventChannel depositEventChannel;
  private final DepositContract depositContract;
  private static final Logger LOG = LogManager.getLogger();
  private final AsyncRunner asyncRunner;

  private volatile Disposable newBlockSubscription;
  private boolean active = false;
  private BigInteger latestCanonicalBlockNumber = BigInteger.ZERO;
  private BigInteger latestSuccessfullyQueriedBlockNumber = BigInteger.ZERO;

  public DepositRequestManager(
      Eth1Provider eth1Provider,
      AsyncRunner asyncRunner,
      DepositEventChannel depositEventChannel,
      DepositContract depositContract) {
    this.eth1Provider = eth1Provider;
    this.asyncRunner = asyncRunner;
    this.depositEventChannel = depositEventChannel;
    this.depositContract = depositContract;
  }

  public void start() {
    newBlockSubscription =
        eth1Provider
            .getLatestBlockFlowable()
            .map(EthBlock.Block::getNumber)
            .map(number -> number.subtract(ETH1_FOLLOW_DISTANCE.bigIntegerValue()))
            .subscribe(this::updateLatestCanonicalBlockNumber, this::onSubscriptionFailed);
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
            this::start,
            (error) ->
                LOG.warn(
                    "Unable to subscribe to the Eth1Node. Node won't have access to new deposits.",
                    error));
  }

  public void stop() {
    newBlockSubscription.dispose();
  }

  private synchronized void updateLatestCanonicalBlockNumber(
      BigInteger latestCanonicalBlockNumber) {
    if (latestCanonicalBlockNumber.compareTo(this.latestCanonicalBlockNumber) <= 0) {
      return;
    }
    this.latestCanonicalBlockNumber = latestCanonicalBlockNumber;
    getLatestDeposits();
  }

  private void getLatestDeposits() {
    final BigInteger latestCanonicalBlockNumberAtRequestStart;
    final BigInteger latestSuccessfullyQueriedBlockNumberAtRequestStart;
    synchronized (DepositRequestManager.this) {
      if (active || latestCanonicalBlockNumber.equals(latestSuccessfullyQueriedBlockNumber)) {
        return;
      }
      active = true;

      latestCanonicalBlockNumberAtRequestStart = this.latestCanonicalBlockNumber;
      latestSuccessfullyQueriedBlockNumberAtRequestStart =
          this.latestSuccessfullyQueriedBlockNumber;
    }

    LOG.trace(
        "Attempting to fetch deposit events for block numbers in the range ({}, {})",
        latestSuccessfullyQueriedBlockNumberAtRequestStart,
        latestCanonicalBlockNumberAtRequestStart);

    DefaultBlockParameter fromBlock =
        DefaultBlockParameter.valueOf(latestSuccessfullyQueriedBlockNumberAtRequestStart);
    DefaultBlockParameter toBlock =
        DefaultBlockParameter.valueOf(latestCanonicalBlockNumberAtRequestStart);

    fetchAndPublishDepositEventsInBlockRange(fromBlock, toBlock)
        .finish(
            () -> onDepositRequestSuccessful(latestCanonicalBlockNumberAtRequestStart),
            (err) -> onDepositRequestFailed(err, latestCanonicalBlockNumberAtRequestStart));
  }

  private synchronized void onDepositRequestSuccessful(
      BigInteger latestCanonicalBlockNumberAtRequestStart) {
    this.latestSuccessfullyQueriedBlockNumber =
        latestCanonicalBlockNumberAtRequestStart.add(BigInteger.ONE);
    active = false;
    if (latestCanonicalBlockNumber.compareTo(latestSuccessfullyQueriedBlockNumber) > 0) {
      getLatestDeposits();
    }
  }

  private synchronized void onDepositRequestFailed(
      Throwable err, BigInteger latestCanonicalBlockNumberAtRequestStart) {
    active = false;
    LOG.warn(
        "Failed to fetch deposit events for block numbers in the range ({}, {})",
        latestSuccessfullyQueriedBlockNumber,
        latestCanonicalBlockNumberAtRequestStart,
        err);

    asyncRunner
        .getDelayedFuture(Constants.ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT, TimeUnit.SECONDS)
        .finish(
            this::getLatestDeposits,
            (error) -> LOG.warn("Unable to execute delayed request. Dropping request", error));
  }

  private SafeFuture<Void> fetchAndPublishDepositEventsInBlockRange(
      DefaultBlockParameter fromBlock, DefaultBlockParameter toBlock) {
    SafeFuture<List<DepositContract.DepositEventEventResponse>> eventsFuture =
        depositContract.depositEventEventsInRange(fromBlock, toBlock);

    return eventsFuture
        .thenApply(this::groupDepositEventResponsesByBlockHash)
        .thenCompose(
            groupedDepositEventResponsesByBlockHash ->
                publishNextDepositEvent(
                    getMapOfEthBlockFutures(groupedDepositEventResponsesByBlockHash.keySet()),
                    groupedDepositEventResponsesByBlockHash));
  }

  private SafeFuture<Void> publishNextDepositEvent(
      List<SafeFuture<Block>> blockRequests,
      Map<BlockNumberAndHash, List<DepositEventEventResponse>> depositEventsByBlock) {

    // First process completed requests using iteration.
    // Avoid StackOverflowException when there is a long string of requests already completed.
    while (!blockRequests.isEmpty() && blockRequests.get(0).isDone()) {
      final Block block = blockRequests.remove(0).join();
      publishEventForBlock(block, depositEventsByBlock);
    }

    // All requests have completed and been processed.
    if (blockRequests.isEmpty()) {
      return SafeFuture.completedFuture(null);
    }

    // Reached a block request that isn't complete so wait for it and recurse back into this method.
    return blockRequests
        .get(0)
        .thenCompose(block -> publishNextDepositEvent(blockRequests, depositEventsByBlock));
  }

  private void publishEventForBlock(
      final Block block,
      final Map<BlockNumberAndHash, List<DepositEventEventResponse>> depositEventsByBlock) {
    final List<DepositEventEventResponse> deposits =
        depositEventsByBlock.get(new BlockNumberAndHash(block.getNumber(), block.getHash()));
    checkNotNull(deposits, "Did not find any deposits for block {}", block.getNumber());
    publishDeposits(createDepositFromBlockEvent(block, deposits));
  }

  private DepositsFromBlockEvent createDepositFromBlockEvent(
      final Block block, final List<DepositEventEventResponse> groupedDepositEventResponse) {
    return new DepositsFromBlockEvent(
        UnsignedLong.valueOf(block.getNumber()),
        Bytes32.fromHexString(block.getHash()),
        UnsignedLong.valueOf(block.getTimestamp()),
        groupedDepositEventResponse.stream()
            .map(Deposit::new)
            .sorted(Comparator.comparing(Deposit::getMerkle_tree_index))
            .collect(toList()));
  }

  private List<SafeFuture<Block>> getMapOfEthBlockFutures(
      Set<BlockNumberAndHash> neededBlockHashes) {
    return neededBlockHashes.stream()
        .map(blockInfo -> eth1Provider.getEth1BlockFuture(blockInfo.getHash()))
        .collect(toList());
  }

  private SortedMap<BlockNumberAndHash, List<DepositContract.DepositEventEventResponse>>
      groupDepositEventResponsesByBlockHash(
          List<DepositContract.DepositEventEventResponse> events) {
    return events.stream()
        .collect(
            groupingBy(
                event ->
                    new BlockNumberAndHash(event.log.getBlockNumber(), event.log.getBlockHash()),
                TreeMap::new,
                toList()));
  }

  private void publishDeposits(DepositsFromBlockEvent event) {
    depositEventChannel.notifyDepositsFromBlock(event);
  }

  private static class BlockNumberAndHash implements Comparable<BlockNumberAndHash> {
    private static final Comparator<BlockNumberAndHash> COMPARATOR =
        Comparator.comparing(BlockNumberAndHash::getNumber)
            .thenComparing(BlockNumberAndHash::getHash);

    private final BigInteger number;
    private final String hash;

    private BlockNumberAndHash(final BigInteger number, final String hash) {
      this.number = number;
      this.hash = hash;
    }

    public BigInteger getNumber() {
      return number;
    }

    public String getHash() {
      return hash;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final BlockNumberAndHash that = (BlockNumberAndHash) o;
      return Objects.equals(number, that.number) && Objects.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
      return Objects.hash(number, hash);
    }

    @Override
    public int compareTo(final BlockNumberAndHash o) {
      return COMPARATOR.compare(this, o);
    }
  }
}
