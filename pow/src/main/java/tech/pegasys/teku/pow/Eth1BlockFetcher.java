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

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;

import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.util.time.TimeProvider;

public class Eth1BlockFetcher {
  private static final Logger LOG = LogManager.getLogger();

  private final Eth1Provider eth1Provider;
  private final TimeProvider timeProvider;
  private final UnsignedLong cacheDuration;
  private final Eth1EventsChannel eth1EventsChannel;
  private final NavigableSet<UnsignedLong> blocksToRequest = new TreeSet<>();
  private boolean requestInProgress = false;
  private boolean active = false;

  public Eth1BlockFetcher(
      final Eth1EventsChannel eth1EventsChannel,
      final Eth1Provider eth1Provider,
      final TimeProvider timeProvider,
      final UnsignedLong cacheDuration) {
    this.eth1EventsChannel = eth1EventsChannel;
    this.eth1Provider = eth1Provider;
    this.timeProvider = timeProvider;
    this.cacheDuration = cacheDuration;
  }

  public synchronized void onInSync(final UnsignedLong latestCanonicalBlockNumber) {
    if (active) {
      return;
    }
    active = true;
    LOG.debug("Beginning back-fill of Eth1 blocks");
    backfillEth1Blocks(latestCanonicalBlockNumber);
  }

  public void fetch(final BigInteger fromBlock, final BigInteger toBlock) {
    synchronized (this) {
      if (!active) {
        // No point starting to request data if we haven't yet reached the start of the chain
        // We'll just wind up downloading a heap of blocks we then throw away
        return;
      }
      for (BigInteger block = fromBlock;
          block.compareTo(toBlock) <= 0;
          block = block.add(BigInteger.ONE)) {
        blocksToRequest.add(UnsignedLong.valueOf(block));
      }
    }
    requestNextBlockIfRequired();
  }

  private void requestNextBlockIfRequired() {
    UnsignedLong blockToRequest;
    synchronized (this) {
      if (requestInProgress || blocksToRequest.isEmpty()) {
        return;
      }
      blockToRequest = blocksToRequest.last();
      blocksToRequest.remove(blockToRequest);
      requestInProgress = true;
    }
    requestBlock(blockToRequest)
        .always(
            () -> {
              synchronized (Eth1BlockFetcher.this) {
                requestInProgress = false;
              }
              requestNextBlockIfRequired();
            });
  }

  private SafeFuture<Void> requestBlock(final UnsignedLong blockNumberToRequest) {
    // Note: Not using guaranteed requests here - if the Eth1 chain is temporarily unavailable
    // we may miss some blocks but that's better than potentially getting stuck retrying a block
    LOG.debug("Requesting block {}", blockNumberToRequest);
    return eth1Provider
        .getEth1Block(blockNumberToRequest)
        .thenAccept(
            block -> {
              if (isAboveLowerBound(UnsignedLong.valueOf(block.getTimestamp()))) {
                postBlock(block);
              } else {
                // Every block before the one we just fetched must be outside of the range
                synchronized (Eth1BlockFetcher.this) {
                  // All blocks at or before this number must be before the cache period
                  blocksToRequest.headSet(blockNumberToRequest, true).clear();
                }
              }
            })
        .exceptionallyCompose(
            error -> {
              LOG.warn("Failed to retrieve block {}", blockNumberToRequest);
              return SafeFuture.COMPLETE;
            });
  }

  private void postBlock(final Block block) {
    eth1EventsChannel.onEth1Block(
        Bytes32.fromHexString(block.getHash()), UnsignedLong.valueOf(block.getTimestamp()));
  }

  private void backfillEth1Blocks(final UnsignedLong nextBlockToRequest) {
    // Walk backwards from blockNumber until we reach the start of the voting period
    eth1Provider
        .getGuaranteedEth1Block(nextBlockToRequest)
        .finish(
            block -> {
              if (isAboveLowerBound(UnsignedLong.valueOf(block.getTimestamp()))) {
                postBlock(block);
                if (!nextBlockToRequest.equals(ZERO)) {
                  backfillEth1Blocks(nextBlockToRequest.minus(ONE));
                }
              } else {
                LOG.debug("Completed back-fill of Eth1 blocks");
              }
            },
            error -> LOG.error("Unexpected error while back-filling ETH1 blocks", error));
  }

  private boolean isAboveLowerBound(UnsignedLong timestamp) {
    return timestamp.compareTo(getCacheRangeLowerBound(timeProvider.getTimeInSeconds())) >= 0;
  }

  private UnsignedLong getCacheRangeLowerBound(UnsignedLong currentTime) {
    return currentTime.compareTo(cacheDuration) > 0 ? currentTime.minus(cacheDuration) : ZERO;
  }
}
