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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Throwables;
import java.math.BigInteger;
import java.net.ConnectException;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.beacon.pow.exception.Eth1RequestException;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.runloop.AsyncRunLoop;
import tech.pegasys.teku.infrastructure.async.runloop.RunLoopLogic;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.subscribers.ObservableValue;
import tech.pegasys.teku.infrastructure.subscribers.ValueObserver;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.Constants;

public class TimeBasedEth1HeadTracker implements Eth1HeadTracker, RunLoopLogic {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final TimeProvider timeProvider;
  private final AsyncRunner asyncRunner;
  private final Eth1Provider eth1Provider;

  private final ObservableValue<UInt64> headSubscription = new ObservableValue<>(true);

  private boolean searchForwards = false;
  private UInt64 nextAdvanceTimeInSeconds = UInt64.ZERO;
  private Block lastNotifiedChainHead;
  private Block nextCandidateHead;

  public TimeBasedEth1HeadTracker(
      final Spec spec,
      final TimeProvider timeProvider,
      final AsyncRunner asyncRunner,
      final Eth1Provider eth1Provider) {
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.asyncRunner = asyncRunner;
    this.eth1Provider = eth1Provider;
  }

  @Override
  public void start() {
    new AsyncRunLoop(this, asyncRunner, Constants.ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT).start();
  }

  @Override
  public SafeFuture<Void> init() {
    return eth1Provider
        .getGuaranteedLatestEth1Block()
        .thenCompose(
            headBlock -> {
              if (isOldEnough(headBlock)) {
                LOG.trace("Head block is old enough");
                waitUntilNextEth1BlockExpected();
                return SafeFuture.completedFuture(headBlock);
              } else {
                LOG.trace("Retrieving block at follow distance");
                return getBlock(
                    UInt64.valueOf(headBlock.getNumber()).minusMinZero(getEth1FollowDistance()));
              }
            })
        .thenAccept(
            block -> {
              nextCandidateHead = block;
              if (isOldEnough(block)) {
                LOG.trace("Init block is old enough");
                notifyNewHead(block);
              } else if (block.getNumber().equals(BigInteger.ZERO)) {
                // Nothing before genesis so wait for it to be old enough then search forwards
                LOG.trace("Genesis block is not old enough");
                searchForwards = true;
                waitForBlockToBeOldEnough(block);
              }
            });
  }

  @Override
  public SafeFuture<Void> advance() {
    checkState(nextCandidateHead != null, "Init has not completed successfully");
    if (!searchForwards) {
      return searchBackwards();
    } else {
      return stepForward();
    }
  }

  private SafeFuture<Void> stepForward() {
    LOG.trace("Searching forwards from block {}", nextCandidateHead.getNumber());
    if (isOldEnough(nextCandidateHead)) {
      notifyNewHead(nextCandidateHead);
      return eth1Provider
          .getEth1Block(UInt64.valueOf(nextCandidateHead.getNumber()).plus(1))
          .thenAccept(
              maybeBlock ->
                  maybeBlock.ifPresentOrElse(
                      this::waitForBlockToBeOldEnough, this::waitUntilNextEth1BlockExpected));
    }
    return SafeFuture.COMPLETE;
  }

  private SafeFuture<Void> searchBackwards() {
    LOG.trace(
        "Searching backwards from block {} with timestamp {}. Still {}s too recent",
        nextCandidateHead::getNumber,
        nextCandidateHead::getTimestamp,
        () -> UInt64.valueOf(nextCandidateHead.getTimestamp()).minusMinZero(getCutOffTime()));
    final UInt64 previousBlockNumber =
        UInt64.valueOf(nextCandidateHead.getNumber()).minusMinZero(1);
    return getBlock(previousBlockNumber)
        .thenAccept(
            block -> {
              if (isOldEnough(block)) {
                notifyNewHead(block);
                waitForBlockToBeOldEnough(nextCandidateHead);
              } else {
                nextCandidateHead = block;
              }
            });
  }

  private void waitUntilNextEth1BlockExpected() {
    LOG.trace("Waiting until next eth1 block expected");
    nextAdvanceTimeInSeconds = timeProvider.getTimeInSeconds().plus(getSecondsPerEth1Block());
  }

  private void waitForBlockToBeOldEnough(final Block block) {
    nextCandidateHead = block;
    nextAdvanceTimeInSeconds =
        UInt64.valueOf(block.getTimestamp()).plus(getFollowDistanceInSeconds());
    LOG.trace(
        "Waiting until block {} is old enough at {}", block.getNumber(), nextAdvanceTimeInSeconds);
  }

  private SafeFuture<Block> getBlock(final UInt64 blockNumber) {
    return eth1Provider
        .getEth1Block(blockNumber)
        .thenApply(
            maybeBlock -> maybeBlock.orElseThrow(() -> new BlockUnavailableException(blockNumber)));
  }

  @Override
  public Duration getDelayUntilNextAdvance() {
    return Duration.ofSeconds(
        nextAdvanceTimeInSeconds.minusMinZero(timeProvider.getTimeInSeconds()).longValue());
  }

  @Override
  public void onError(final Throwable t) {
    final Throwable rootCause = Throwables.getRootCause(t);
    if (rootCause instanceof BlockUnavailableException) {
      LOG.error(
          "Block number {} not yet available. Retrying after delay.",
          ((BlockUnavailableException) rootCause).getBlockNumber());
    } else if (rootCause instanceof Eth1RequestException && rootCause.getSuppressed().length == 0) {
      LOG.debug("Failed to update eth1 chain head - no endpoints available");
    } else if (ExceptionUtil.hasCause(t, ConnectException.class)) {
      LOG.error("Failed to update eth1 chain head - {}", t.getMessage());
    } else {
      LOG.error("Failed to update eth1 chain head", t);
    }
  }

  private boolean isOldEnough(final Block headBlock) {
    final UInt64 cutOffTime = getCutOffTime();
    final long blockTime = headBlock.getTimestamp().longValueExact();
    return cutOffTime.isGreaterThanOrEqualTo(blockTime);
  }

  private UInt64 getCutOffTime() {
    return timeProvider.getTimeInSeconds().minusMinZero(getFollowDistanceInSeconds());
  }

  private UInt64 getFollowDistanceInSeconds() {
    return getEth1FollowDistance().times(getSecondsPerEth1Block());
  }

  private int getSecondsPerEth1Block() {
    return spec.getGenesisSpecConfig().getSecondsPerEth1Block();
  }

  private UInt64 getEth1FollowDistance() {
    return spec.getGenesisSpecConfig().getEth1FollowDistance();
  }

  @Override
  public void stop() {}

  private void notifyNewHead(final Block headBlock) {
    searchForwards = true;
    if (!headBlock.equals(lastNotifiedChainHead)) {
      LOG.trace(
          "Found new latest block before follow distance at block number {}, timestamp {}",
          headBlock.getNumber(),
          headBlock.getTimestamp());
      lastNotifiedChainHead = headBlock;
      headSubscription.set(UInt64.valueOf(headBlock.getNumber()));
    }
  }

  @Override
  public long subscribe(final ValueObserver<UInt64> subscriber) {
    return headSubscription.subscribe(subscriber);
  }

  @Override
  public void unsubscribe(final long subscriberId) {
    headSubscription.unsubscribe(subscriberId);
  }

  private static class BlockUnavailableException extends RuntimeException {
    private final UInt64 blockNumber;

    private BlockUnavailableException(final UInt64 blockNumber) {
      super("Block " + blockNumber + " unavailable");
      this.blockNumber = blockNumber;
    }

    public UInt64 getBlockNumber() {
      return blockNumber;
    }
  }
}
