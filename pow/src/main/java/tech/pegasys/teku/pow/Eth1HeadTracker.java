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

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.events.Subscribers;

public class Eth1HeadTracker {
  private static final Logger LOG = LogManager.getLogger();
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AsyncRunner asyncRunner;
  private final Eth1Provider eth1Provider;
  private Optional<UnsignedLong> headAtFollowDistance = Optional.empty();

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
              LOG.warn("Failed to get latest ETH1 chain head. Will retry.", error);
              return null;
            })
        .always(
            () ->
                asyncRunner
                    .runAfterDelay(
                        this::pollLatestHead, Constants.SECONDS_PER_ETH1_BLOCK, TimeUnit.SECONDS)
                    .finish(
                        () -> {},
                        error ->
                            LOG.error("Scheduling next check of ETH1 chain head failed", error)));
  }

  private void onLatestBlockHead(final Block headBlock) {
    final UnsignedLong headBlockNumber = UnsignedLong.valueOf(headBlock.getNumber());
    if (headBlockNumber.compareTo(ETH1_FOLLOW_DISTANCE) < 0) {
      LOG.debug("Not processing ETH1 blocks because chain has not reached minimum follow distance");
      return;
    }
    final UnsignedLong newHeadAtFollowDistance = headBlockNumber.minus(ETH1_FOLLOW_DISTANCE);
    if (headAtFollowDistance
        .map(current -> current.compareTo(newHeadAtFollowDistance) < 0)
        .orElse(true)) {
      headAtFollowDistance = Optional.of(newHeadAtFollowDistance);
      LOG.debug("ETH1 block at follow distance updated to {}", newHeadAtFollowDistance);
      subscribers.deliver(HeadUpdatedSubscriber::onHeadUpdated, newHeadAtFollowDistance);
    }
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
    void onHeadUpdated(final UnsignedLong canonicalHead);
  }
}
