/*
 * Copyright ConsenSys Software Inc., 2022
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.google.common.base.Throwables;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.teku.beacon.pow.contract.DepositContract;
import tech.pegasys.teku.beacon.pow.contract.DepositContract.DepositEventEventResponse;
import tech.pegasys.teku.beacon.pow.exception.Eth1RequestException;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.ethereum.pow.api.InvalidDepositEventsException;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.Constants;

public class DepositFetcher {

  private static final Logger LOG = LogManager.getLogger();

  private final Eth1Provider eth1Provider;
  private final Eth1EventsChannel eth1EventsChannel;
  private final DepositEventsAccessor depositEventsAccessor;
  private final Eth1BlockFetcher eth1BlockFetcher;
  private final AsyncRunner asyncRunner;
  private final int maxBlockRange;

  public DepositFetcher(
      final Eth1Provider eth1Provider,
      final Eth1EventsChannel eth1EventsChannel,
      final DepositEventsAccessor depositEventsAccessor,
      final Eth1BlockFetcher eth1BlockFetcher,
      final AsyncRunner asyncRunner,
      final int maxBlockRange) {
    this.eth1Provider = eth1Provider;
    this.eth1EventsChannel = eth1EventsChannel;
    this.depositEventsAccessor = depositEventsAccessor;
    this.eth1BlockFetcher = eth1BlockFetcher;
    this.asyncRunner = asyncRunner;
    this.maxBlockRange = maxBlockRange;
  }

  // Inclusive on both sides
  public synchronized SafeFuture<Void> fetchDepositsInRange(
      BigInteger fromBlockNumber, BigInteger toBlockNumber) {
    checkArgument(
        fromBlockNumber.compareTo(toBlockNumber) <= 0,
        "From block number (%s) must be less than or equal to block number (%s)",
        fromBlockNumber,
        toBlockNumber);
    LOG.trace(
        "Attempting to fetch deposit events for block numbers in the range ({}, {})",
        fromBlockNumber,
        toBlockNumber);

    final DepositFetchState fetchState = new DepositFetchState(fromBlockNumber, toBlockNumber);
    return sendNextBatchRequest(fetchState);
  }

  private SafeFuture<Void> sendNextBatchRequest(final DepositFetchState fetchState) {
    final BigInteger nextBatchEnd = fetchState.getNextBatchEnd();
    LOG.debug(
        "Requesting deposits between {} and {}. Batch size: {}",
        fetchState.nextBatchStart,
        nextBatchEnd,
        fetchState.batchSize);
    return processDepositsInBatch(fetchState.nextBatchStart, nextBatchEnd)
        .exceptionallyCompose(
            (err) -> {
              LOG.debug(
                  "Failed to request deposit events for block numbers in the range ({}, {}). Retrying.",
                  fetchState.nextBatchStart,
                  nextBatchEnd,
                  err);

              final Throwable rootCause = Throwables.getRootCause(err);
              if (rootCause instanceof InvalidDepositEventsException) {
                STATUS_LOG.eth1DepositEventsFailure(rootCause);
              } else if (Eth1RequestException.shouldTryWithSmallerRange(err)) {
                STATUS_LOG.eth1FetchDepositsRequiresSmallerRange(fetchState.batchSize);
                fetchState.reduceBatchSize();
              }

              return asyncRunner.runAfterDelay(
                  () -> sendNextBatchRequest(fetchState),
                  Constants.ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT);
            })
        .thenCompose(
            __ -> {
              fetchState.moveToNextBatch();
              LOG.trace("Batch request completed. Done? {}", fetchState.isDone());
              if (fetchState.isDone()) {
                return SafeFuture.COMPLETE;
              } else {
                return sendNextBatchRequest(fetchState);
              }
            });
  }

  private SafeFuture<Void> processDepositsInBatch(
      final BigInteger fromBlockNumber, final BigInteger toBlockNumber) {
    return depositEventsAccessor
        .depositEventInRange(
            DefaultBlockParameter.valueOf(fromBlockNumber),
            DefaultBlockParameter.valueOf(toBlockNumber))
        .thenApply(this::groupDepositEventResponsesByBlockHash)
        .thenCompose(
            eventResponsesByBlockHash ->
                postDepositEvents(
                    getListOfEthBlockFutures(eventResponsesByBlockHash.keySet()),
                    eventResponsesByBlockHash,
                    fromBlockNumber,
                    toBlockNumber));
  }

  private SafeFuture<Void> postDepositEvents(
      List<SafeFuture<EthBlock.Block>> blockRequests,
      Map<BlockNumberAndHash, List<DepositContract.DepositEventEventResponse>> depositEventsByBlock,
      BigInteger fromBlock,
      BigInteger toBlock) {
    LOG.trace("Posting deposit events for {} blocks", depositEventsByBlock.size());
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
    return DepositsFromBlockEvent.create(
        UInt64.valueOf(block.getNumber()),
        Bytes32.fromHexString(block.getHash()),
        UInt64.valueOf(block.getTimestamp()),
        groupedDepositEventResponse.stream().map(DepositEventEventResponse::toDeposit));
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

  private class DepositFetchState {
    // Both inclusive
    BigInteger nextBatchStart;

    final BigInteger lastBlock;
    int batchSize = maxBlockRange;

    public DepositFetchState(final BigInteger fromBlockNumber, final BigInteger toBlockNumber) {
      this.nextBatchStart = fromBlockNumber;
      this.lastBlock = toBlockNumber;
    }

    public void moveToNextBatch() {
      nextBatchStart = getNextBatchEnd().add(BigInteger.ONE);
      if (batchSize < maxBlockRange) {
        // Grow the batch size slowly as we may be past a large blob of logs that caused trouble
        // +1 to guarantee it grows by at least 1
        batchSize = Math.min(maxBlockRange, (int) (batchSize * 1.1 + 1));
      }
    }

    private BigInteger getNextBatchEnd() {
      return lastBlock.min(nextBatchStart.add(BigInteger.valueOf(batchSize)));
    }

    public boolean isDone() {
      return nextBatchStart.compareTo(lastBlock) >= 0;
    }

    public void reduceBatchSize() {
      batchSize = Math.max(1, batchSize / 2);
      LOG.debug("Reduced batch size to {}", batchSize);
    }
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
