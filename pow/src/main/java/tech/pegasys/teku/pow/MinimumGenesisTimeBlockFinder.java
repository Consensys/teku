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

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.math.BigInteger;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
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
        new SearchContext(eth1DepositContractDeployBlock.orElse(ZERO), headBlockNumber));
  }

  private SafeFuture<EthBlock.Block> binarySearchLoop(final SearchContext searchContext) {
    if (searchContext.low.compareTo(searchContext.high) <= 0) {
      final UInt64 mid = searchContext.low.plus(searchContext.high).dividedBy(TWO);
      return eth1Provider
          .getEth1Block(mid)
          .thenCompose(
              midBlock -> {
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
      return eth1Provider.getEth1Block(searchContext.low);
    }
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
