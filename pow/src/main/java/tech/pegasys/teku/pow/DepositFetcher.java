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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.contract.DepositContract;
import tech.pegasys.teku.pow.contract.DepositContract.DepositEventEventResponse;
import tech.pegasys.teku.pow.event.Deposit;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.Constants;

public class DepositFetcher {

  private static final Logger LOG = LogManager.getLogger();

  private final Eth1Provider eth1Provider;
  private final Eth1EventsChannel eth1EventsChannel;
  private final DepositContract depositContract;
  private final Eth1BlockFetcher eth1BlockFetcher;
  private final AsyncRunner asyncRunner;

  public DepositFetcher(
      final Eth1Provider eth1Provider,
      final Eth1EventsChannel eth1EventsChannel,
      final DepositContract depositContract,
      final Eth1BlockFetcher eth1BlockFetcher,
      final AsyncRunner asyncRunner) {
    this.eth1Provider = eth1Provider;
    this.eth1EventsChannel = eth1EventsChannel;
    this.depositContract = depositContract;
    this.eth1BlockFetcher = eth1BlockFetcher;
    this.asyncRunner = asyncRunner;
  }

  // Inclusive on both sides
  public synchronized SafeFuture<Void> fetchDepositsInRange(
      BigInteger fromBlockNumber, BigInteger toBlockNumber) {

    LOG.trace(
        "Attempting to fetch deposit events for block numbers in the range ({}, {})",
        fromBlockNumber,
        toBlockNumber);

    return getDepositEventsInRangeFromContract(fromBlockNumber, toBlockNumber)
        .thenApply(this::groupDepositEventResponsesByBlockHash)
        .thenCompose(
            eventResponsesByBlockHash ->
                postDepositEvents(
                    getListOfEthBlockFutures(eventResponsesByBlockHash.keySet()),
                    eventResponsesByBlockHash,
                    fromBlockNumber,
                    toBlockNumber));
  }

  private SafeFuture<List<DepositContract.DepositEventEventResponse>>
      getDepositEventsInRangeFromContract(BigInteger fromBlockNumber, BigInteger toBlockNumber) {

    DefaultBlockParameter fromBlock = DefaultBlockParameter.valueOf(fromBlockNumber);
    DefaultBlockParameter toBlock = DefaultBlockParameter.valueOf(toBlockNumber);

    return depositContract
        .depositEventInRange(fromBlock, toBlock)
        .exceptionallyCompose(
            (err) -> {
              LOG.debug(
                  "Failed to request deposit events for block numbers in the range ({}, {}). Retrying.",
                  fromBlockNumber,
                  toBlockNumber,
                  err);

              return asyncRunner.runAfterDelay(
                  () -> getDepositEventsInRangeFromContract(fromBlockNumber, toBlockNumber),
                  Constants.ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT,
                  TimeUnit.SECONDS);
            });
  }

  private SafeFuture<Void> postDepositEvents(
      List<SafeFuture<EthBlock.Block>> blockRequests,
      Map<BlockNumberAndHash, List<DepositContract.DepositEventEventResponse>> depositEventsByBlock,
      BigInteger fromBlock,
      BigInteger toBlock) {
    BigInteger from = fromBlock;
    // First process completed requests using iteration.
    // Avoid StackOverflowException when there is a long string of requests already completed.
    while (!blockRequests.isEmpty() && blockRequests.get(0).isDone()) {
      final EthBlock.Block block = blockRequests.remove(0).join();

      // Fetch any empty blocks between this deposit block and the previous one (or start of range)
      final BigInteger to = block.getNumber().subtract(BigInteger.ONE);
      eth1BlockFetcher.fetch(from, to);
      from = block.getNumber().add(BigInteger.ONE);

      postEventsForBlock(block, depositEventsByBlock);
    }

    // All requests have completed and been processed.
    if (blockRequests.isEmpty()) {
      // Fetch any empty blocks between the last deposit and end of the range
      eth1BlockFetcher.fetch(from, toBlock);
      return SafeFuture.COMPLETE;
    }

    BigInteger remainingRangeStart = from;
    // Reached a block request that isn't complete so wait for it and recurse back into this method.
    return blockRequests
        .get(0)
        .thenCompose(
            block ->
                postDepositEvents(
                    blockRequests, depositEventsByBlock, remainingRangeStart, toBlock));
  }

  private synchronized void postEventsForBlock(
      final EthBlock.Block block,
      final Map<BlockNumberAndHash, List<DepositContract.DepositEventEventResponse>>
          depositEventsByBlock) {
    final BigInteger blockNumber = block.getNumber();
    final List<DepositContract.DepositEventEventResponse> deposits =
        depositEventsByBlock.get(new BlockNumberAndHash(blockNumber, block.getHash()));
    checkNotNull(deposits, "Did not find any deposits for block {}", blockNumber);
    LOG.trace("Successfully fetched deposit events for block: {} ", blockNumber);
    postDeposits(createDepositFromBlockEvent(block, deposits));
  }

  private DepositsFromBlockEvent createDepositFromBlockEvent(
      final EthBlock.Block block,
      final List<DepositContract.DepositEventEventResponse> groupedDepositEventResponse) {
    return new DepositsFromBlockEvent(
        UnsignedLong.valueOf(block.getNumber()),
        Bytes32.fromHexString(block.getHash()),
        UnsignedLong.valueOf(block.getTimestamp()),
        groupedDepositEventResponse.stream()
            .map(Deposit::new)
            .sorted(Comparator.comparing(Deposit::getMerkle_tree_index))
            .collect(toList()));
  }

  private List<SafeFuture<EthBlock.Block>> getListOfEthBlockFutures(
      Set<BlockNumberAndHash> neededBlockHashes) {
    return neededBlockHashes.stream()
        .map(BlockNumberAndHash::getHash)
        .map(eth1Provider::getGuaranteedEth1Block)
        .collect(toList());
  }

  private NavigableMap<BlockNumberAndHash, List<DepositEventEventResponse>>
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

  private void postDeposits(DepositsFromBlockEvent event) {
    eth1EventsChannel.onDepositsFromBlock(event);
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
