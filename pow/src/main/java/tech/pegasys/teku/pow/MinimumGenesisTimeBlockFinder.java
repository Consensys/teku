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
import java.util.Optional;
import org.apache.logging.log4j.Level;
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
        .thenCompose(this::confirmMinGenesisBlock);
  }

  /**
   * The binary search may return a block that is too new (if historical blocks are unavailable
   * during the search), so pull the candidate's parent block to confirm that we actually found the
   * min genesis block.
   *
   * @param candidate A candidate block that may be the min genesis block
   * @return The confirmed min genesis block
   */
  @VisibleForTesting
  SafeFuture<EthBlock.Block> confirmMinGenesisBlock(final EthBlock.Block candidate) {
    assertBlockIsAtOrAfterMinGenesis(candidate);
    if (blockIsAtMinimalMinGenesisPosition(candidate)) {
      // We've found min genesis
      traceWithBlock("Confirmed minimum genesis block at minimal position: ", candidate);
      return SafeFuture.completedFuture(candidate);
    }

    return eth1Provider
        .getEth1BlockWithRetry(candidate.getParentHash())
        .thenApply(Optional::get)
        .thenApply(
            parent -> {
              // Parent must be prior to min genesis to confirm that our candidate is the first min
              // genesis block
              if (!blockIsPriorToMinGenesis(parent)) {
                throw new IllegalStateException(
                    String.format(
                        "Candidate min genesis block (#%s, %s) is too recent.  It's parent's timestamp (%s) must be prior to %s.",
                        candidate.getNumber(),
                        candidate.getHash(),
                        parent.getTimestamp(),
                        calculateMinGenesisTimeThreshold()));
              }

              traceWithBlock("Confirmed min genesis block: ", candidate);
              return candidate;
            })
        .exceptionally(
            err -> {
              throw new FailedToFindMinGenesisBlockException(
                  "Failed to retrieve min genesis block.  Check that your eth1 node is fully synced.",
                  err);
            });
  }

  private boolean blockIsAtMinimalMinGenesisPosition(final EthBlock.Block block) {
    final UInt64 candidateBlockNumber = UInt64.valueOf(block.getNumber());
    return eth1DepositContractDeployBlock.map(b -> b.equals(candidateBlockNumber)).orElse(false)
        || candidateBlockNumber.equals(ZERO)
        || blockIsAtMinGenesis(block);
  }

  private SafeFuture<EthBlock.Block> binarySearchLoop(final SearchContext searchContext) {
    if (searchContext.low.compareTo(searchContext.high) <= 0) {
      final UInt64 mid = searchContext.low.plus(searchContext.high).dividedBy(TWO);
      return eth1Provider
          .getEth1BlockWithRetry(mid)
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
      return eth1Provider.getEth1BlockWithRetry(searchContext.low).thenApply(Optional::get);
    }
  }

  private void assertBlockIsAtOrAfterMinGenesis(final EthBlock.Block block) {
    checkArgument(
        blockIsAtOrAfterMinGenesis(block),
        "Invalid candidate minimum genesis block (#: %s, hash: %s, timestamp; %s) is prior to MIN_GENESIS_TIME (%s) - GENESIS_DELAY (%s)",
        block.getNumber(),
        block.getHash(),
        block.getTimestamp(),
        Constants.MIN_GENESIS_TIME,
        Constants.GENESIS_DELAY);
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

  private void traceWithBlock(
      final String message, final EthBlock.Block block, Object... otherArgs) {
    logWithBlock(Level.TRACE, message, block, otherArgs);
  }

  private void logWithBlock(
      final Level level, final String message, final EthBlock.Block block, Object... otherArgs) {
    final Object[] args = new Object[otherArgs.length + 3];
    System.arraycopy(otherArgs, 0, args, 0, otherArgs.length);

    args[otherArgs.length] = block.getNumber();
    args[otherArgs.length + 1] = block.getHash();
    args[otherArgs.length + 2] = block.getTimestamp();

    LOG.log(level, message + "#{} (hash = {}, timestamp = {})", args);
  }

  private UInt64 calculateMinGenesisTimeThreshold() {
    return Constants.MIN_GENESIS_TIME.minusMinZero(Constants.GENESIS_DELAY);
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
