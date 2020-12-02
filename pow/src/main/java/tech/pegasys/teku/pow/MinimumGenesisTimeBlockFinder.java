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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.pow.exception.FailedToFindMinGenesisBlockException;
import tech.pegasys.teku.util.config.Constants;

public class MinimumGenesisTimeBlockFinder {

  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_ANCESTORS_TO_SEARCH = 500;
  private static final UInt64 TWO = UInt64.valueOf(2);

  private final Eth1Provider eth1Provider;
  private final Optional<UInt64> eth1DepositContractDeployBlock;

  public MinimumGenesisTimeBlockFinder(
      Eth1Provider eth1Provider, Optional<UInt64> eth1DepositContractDeployBlock) {
    this.eth1Provider = eth1Provider;
    this.eth1DepositContractDeployBlock = eth1DepositContractDeployBlock;
  }

  /**
   * Find first block in history that has timestamp greater than MIN_GENESIS_TIME
   *
   * @param headBlockNumber block number at current chain head (respecting follow distance)
   * @return min genesis time block
   */
  public SafeFuture<EthBlock.Block> findMinGenesisTimeBlockInHistory(
      final BigInteger headBlockNumber) {
    return binarySearchLoop(
            new SearchContext(eth1DepositContractDeployBlock.orElse(ZERO), headBlockNumber))
        .thenCompose(this::confirmOrFindMinGenesisBlock);
  }

  /**
   * The binary search may return a block that is too new (if historical blocks are unavailable
   * during the search), so walk back though chain to confirm or else pull the correct min genesis
   * block.
   *
   * @param candidateMinGenesisBlock A candidate block that may be the min genesis block
   * @return The confirmed min genesis block
   */
  @VisibleForTesting
  SafeFuture<EthBlock.Block> confirmOrFindMinGenesisBlock(
      final EthBlock.Block candidateMinGenesisBlock) {
    assertBlockIsAtOrAfterMinGenesis(candidateMinGenesisBlock);
    if (blockIsAtMinimalMinGenesisPosition(candidateMinGenesisBlock)) {
      // We've found min genesis
      return SafeFuture.completedFuture(candidateMinGenesisBlock);
    }

    final SafeFuture<EthBlock.Block> minGenesisFuture = new SafeFuture<>();

    final AtomicReference<EthBlock.Block> currentMinGenesisCandidate =
        new AtomicReference<>(candidateMinGenesisBlock);
    final AtomicInteger examinedAncestorsCount = new AtomicInteger(0);
    SafeFuture.asyncDoWhile(
            () ->
                eth1Provider
                    .getEth1BlockWithRetry(
                        currentMinGenesisCandidate.get().getParentHash(), Duration.ofSeconds(5), 3)
                    .thenApply(
                        parent -> {
                          examinedAncestorsCount.incrementAndGet();
                          if (blockIsPriorToMinGenesis(parent)) {
                            // We've confirmed the current candidate is at the boundary
                            minGenesisFuture.complete(currentMinGenesisCandidate.get());
                            return false;
                          } else if (blockIsAtMinGenesis(parent)) {
                            // We've found the min genesis block
                            minGenesisFuture.complete(parent);
                            return false;
                          } else {
                            // The parent block is after min genesis
                            // Keep searching through ancestors up to our limit
                            if (examinedAncestorsCount.get() >= MAX_ANCESTORS_TO_SEARCH) {
                              throw new IllegalStateException(
                                  String.format(
                                      "Unable to locate min genesis block after walking back through %s eth1 blocks to block #%s (%s)",
                                      examinedAncestorsCount.get(),
                                      parent.getNumber(),
                                      parent.getHash()));
                            }
                            currentMinGenesisCandidate.set(parent);
                            return true;
                          }
                        }))
        .finish(
            () ->
                minGenesisFuture.completeExceptionally(
                    new FailedToFindMinGenesisBlockException(
                        "Failed to retrieve min genesis block.  Check that your eth1 node is fully synced.")),
            (err) ->
                minGenesisFuture.completeExceptionally(
                    new FailedToFindMinGenesisBlockException(
                        "Failed to retrieve min genesis block.  Check that your eth1 node is fully synced.",
                        err)));

    return minGenesisFuture;
  }

  private boolean blockIsAtMinimalMinGenesisPosition(final EthBlock.Block block) {
    final UInt64 candidateBlockNumber = UInt64.valueOf(block.getNumber());
    return eth1DepositContractDeployBlock
            .map(b -> b.isGreaterThanOrEqualTo(candidateBlockNumber))
            .orElse(false)
        || candidateBlockNumber.equals(ZERO)
        || blockIsAtMinGenesis(block);
  }

  private SafeFuture<EthBlock.Block> binarySearchLoop(final SearchContext searchContext) {
    if (searchContext.low.compareTo(searchContext.high) <= 0) {
      final UInt64 mid = searchContext.low.plus(searchContext.high).dividedBy(TWO);
      return eth1Provider
          .getEth1Block(mid)
          .thenCompose(
              maybeMidBlock -> {
                if (maybeMidBlock.isEmpty()) {
                  // Some blocks are missing from the historical range, try to search among
                  // more recent (presumably available) blocks
                  searchContext.low = mid.plus(ONE);
                  return binarySearchLoop(searchContext);
                }

                final EthBlock.Block midBlock = maybeMidBlock.get();
                final int cmp = compareBlockTimestampToMinGenesisTime(midBlock);
                if (cmp < 0) {
                  searchContext.low = mid.plus(ONE);
                  return binarySearchLoop(searchContext);
                } else if (cmp > 0) {
                  if (mid.equals(ZERO)) {
                    // The very first eth1 block is after eth2 min genesis so just use it
                    return SafeFuture.completedFuture(midBlock);
                  }
                  searchContext.high = mid.minus(ONE);
                  return binarySearchLoop(searchContext);
                } else {
                  // This block has exactly the min genesis time
                  return SafeFuture.completedFuture(midBlock);
                }
              });
    } else {
      // Completed search
      return eth1Provider.getEth1Block(searchContext.low).thenApply(Optional::get);
    }
  }

  private void assertBlockIsAtOrAfterMinGenesis(final EthBlock.Block block) {
    checkArgument(
        blockIsAtOrAfterMinGenesis(block),
        "Invalid candidate minimum genesis block (#: %s, hash: %s, timestamp; %s) is prior to MIN_GENESIS_TIME + GENESIS_DELAY (%s)",
        block.getNumber(),
        block.getHash(),
        block.getTimestamp(),
        Constants.MIN_GENESIS_TIME.plus(Constants.GENESIS_DELAY));
  }

  private boolean blockIsAtMinGenesis(final EthBlock.Block block) {
    return compareBlockTimestampToMinGenesisTime(block) == 0;
  }

  private boolean blockIsPriorToMinGenesis(final EthBlock.Block block) {
    return compareBlockTimestampToMinGenesisTime(block) < 0;
  }

  private boolean blockIsAtOrAfterMinGenesis(final EthBlock.Block block) {
    return compareBlockTimestampToMinGenesisTime(block) >= 0;
  }

  static UInt64 calculateCandidateGenesisTimestamp(BigInteger eth1Timestamp) {
    return UInt64.valueOf(eth1Timestamp).plus(Constants.GENESIS_DELAY);
  }

  static int compareBlockTimestampToMinGenesisTime(EthBlock.Block block) {
    return calculateCandidateGenesisTimestamp(block.getTimestamp())
        .compareTo(Constants.MIN_GENESIS_TIME);
  }

  static Boolean isBlockAfterMinGenesis(EthBlock.Block block) {
    int comparison = compareBlockTimestampToMinGenesisTime(block);
    // If block timestamp is greater than min genesis time,
    // min genesis block must be in the future
    return comparison > 0;
  }

  static void notifyMinGenesisTimeBlockReached(
      Eth1EventsChannel eth1EventsChannel, EthBlock.Block block) {
    MinGenesisTimeBlockEvent event =
        new MinGenesisTimeBlockEvent(
            UInt64.valueOf(block.getTimestamp()),
            UInt64.valueOf(block.getNumber()),
            Bytes32.fromHexString(block.getHash()));
    eth1EventsChannel.onMinGenesisTimeBlock(event);
    LOG.debug("Notifying BeaconChainService of MinGenesisTimeBlock: {}", event);
  }

  private static class SearchContext {
    private UInt64 low;
    private UInt64 high;

    public SearchContext(final UInt64 minSearchBlock, final BigInteger headBlockNumber) {
      this.low = minSearchBlock;
      this.high = UInt64.valueOf(headBlockNumber);
    }
  }
}
