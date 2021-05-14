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

import static tech.pegasys.teku.util.config.Constants.ETH1_FOLLOW_DISTANCE;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_ETH1_BLOCK;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

public class Eth1HeadTracker {
  private static final Logger LOG = LogManager.getLogger();
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AsyncRunner asyncRunner;
  private final Eth1Provider eth1Provider;
  private Optional<UInt64> headAtFollowDistance = Optional.empty();
  private final AtomicBoolean reachedHead = new AtomicBoolean(false);
  private UInt64 eth1FollowDistance = ETH1_FOLLOW_DISTANCE;

  private final Subscribers<HeadUpdatedSubscriber> subscribers = Subscribers.create(true);

  public Eth1HeadTracker(final AsyncRunner asyncRunner, final Eth1Provider eth1Provider) {
    this.asyncRunner = asyncRunner;
    this.eth1Provider = eth1Provider;
  }

  public void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    pollLatestHead();
  }

  private void pollLatestHead() {
    if (!running.get()) {
      return;
    }
    eth1Provider
        .getLatestEth1Block()
        .thenAccept(this::onLatestBlockHead)
        .exceptionally(
            error -> {
              LOG.debug("Failed to get latest Eth1 chain head. Will retry.", error);
              return null;
            })
        .always(
            () ->
                asyncRunner
                    .runAfterDelay(
                        this::pollLatestHead,
                        Duration.ofSeconds(Constants.SECONDS_PER_ETH1_BLOCK.longValue()))
                    .finish(
                        () -> {},
                        error ->
                            LOG.error("Scheduling next check of Eth1 chain head failed", error)));
  }

  private void onLatestBlockHead(final Block headBlock) {
    final UInt64 headBlockNumber = UInt64.valueOf(headBlock.getNumber());
    if (headBlockNumber.compareTo(ETH1_FOLLOW_DISTANCE) < 0) {
      LOG.debug("Not processing Eth1 blocks because chain has not reached minimum follow distance");
      return;
    }
    eth1FollowDistance =
        adjustEth1FollowDistanceByTimestamp(
            headBlockNumber, UInt64.valueOf(headBlock.getTimestamp()), eth1FollowDistance);
    final UInt64 newHeadAtFollowDistance = headBlockNumber.minus(eth1FollowDistance);
    if (headAtFollowDistance
        .map(current -> current.compareTo(newHeadAtFollowDistance) < 0)
        .orElse(true)) {
      if (reachedHead.compareAndSet(false, true)) {
        reachedHead.set(true);
      }
      headAtFollowDistance = Optional.of(newHeadAtFollowDistance);
      LOG.debug("ETH1 block at follow distance updated to {}", newHeadAtFollowDistance);
      subscribers.deliver(HeadUpdatedSubscriber::onHeadUpdated, newHeadAtFollowDistance);
    }
  }

  private synchronized UInt64 adjustEth1FollowDistanceByTimestamp(
      final UInt64 headBlockNumber,
      final UInt64 headBlockTimestamp,
      final UInt64 eth1FollowDistance) {
    if (eth1FollowDistance.equals(UInt64.ZERO)) {
      return eth1FollowDistance;
    }

    final UInt64 timestampAtFollowDistance =
        headBlockTimestamp.min(
            headBlockTimestamp.minus(ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK)));

    UInt64 newEth1FollowDistance = eth1FollowDistance;

    Block block =
        eth1Provider.getGuaranteedEth1Block(headBlockNumber.minus(newEth1FollowDistance)).join();
    Block prevBlock = block;
    while (newEth1FollowDistance.isGreaterThan(UInt64.ZERO)
        && UInt64.valueOf(block.getTimestamp()).isLessThanOrEqualTo(timestampAtFollowDistance)) {
      newEth1FollowDistance = newEth1FollowDistance.decrement();
      prevBlock = block;
      block =
          eth1Provider.getGuaranteedEth1Block(headBlockNumber.minus(newEth1FollowDistance)).join();
    }

    if (!newEth1FollowDistance.equals(eth1FollowDistance)) {
      LOG.debug(
          "ETH1_FOLLOW_DISTANCE adjusted from {} to {}, block at follow distance boundary: number {} timestamp {}",
          eth1FollowDistance,
          newEth1FollowDistance,
          prevBlock.getNumber(),
          prevBlock.getTimestamp());
    }

    return newEth1FollowDistance;
  }

  public long subscribe(final HeadUpdatedSubscriber subscriber) {
    return subscribers.subscribe(subscriber);
  }

  public void unsubscribe(final long subscriberId) {
    subscribers.unsubscribe(subscriberId);
  }

  public void stop() {
    running.set(false);
  }

  public interface HeadUpdatedSubscriber {
    void onHeadUpdated(final UInt64 canonicalHead);
  }
}
