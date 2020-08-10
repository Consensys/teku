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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.Eth1HeadTracker.HeadUpdatedSubscriber;
import tech.pegasys.teku.util.config.Constants;

class Eth1HeadTrackerTest {
  private static final long FOLLOW_DISTANCE = 1000;
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final Eth1Provider eth1Provider = mock(Eth1Provider.class);
  private final HeadUpdatedSubscriber subscriber = mock(HeadUpdatedSubscriber.class);

  private final Eth1HeadTracker tracker = new Eth1HeadTracker(asyncRunner, eth1Provider);

  @BeforeAll
  static void setConstants() {
    Constants.ETH1_FOLLOW_DISTANCE = UInt64.valueOf(FOLLOW_DISTANCE);
  }

  @AfterAll
  static void resetConstants() {
    Constants.setConstants("minimal");
  }

  @Test
  void shouldStayFollowDistanceBehindEth1ChainHead() {
    final int blockNumber = 2048;
    final long followHeadNumber = blockNumber - FOLLOW_DISTANCE;
    when(eth1Provider.getLatestEth1Block()).thenReturn(completedFuture(block(blockNumber)));
    tracker.subscribe(subscriber);
    tracker.start();

    verify(subscriber).onHeadUpdated(UInt64.valueOf(followHeadNumber));
  }

  @Test
  void shouldNotNotifyOfHeadIfChainIsTooShort() {
    when(eth1Provider.getLatestEth1Block()).thenReturn(completedFuture(block(100)));
    tracker.subscribe(subscriber);
    tracker.start();

    verifyNoInteractions(subscriber);
  }

  @Test
  void shouldNotifyWhenChainProgresses() {
    when(eth1Provider.getLatestEth1Block())
        .thenReturn(completedFuture(block(1200)))
        .thenReturn(completedFuture(block(1201)));
    tracker.subscribe(subscriber);
    tracker.start();

    verify(subscriber).onHeadUpdated(UInt64.valueOf(1200 - FOLLOW_DISTANCE));

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();

    verify(subscriber).onHeadUpdated(UInt64.valueOf(1201 - FOLLOW_DISTANCE));
  }

  @Test
  void shouldSkipBlocksWhenChainHeadMovesForwardFasterThanPolling() {
    when(eth1Provider.getLatestEth1Block())
        .thenReturn(completedFuture(block(1200)))
        .thenReturn(completedFuture(block(1501)));
    tracker.subscribe(subscriber);
    tracker.start();

    verify(subscriber).onHeadUpdated(UInt64.valueOf(1200 - FOLLOW_DISTANCE));

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();

    verify(subscriber).onHeadUpdated(UInt64.valueOf(1501 - FOLLOW_DISTANCE));
  }

  @Test
  void shouldNotNotifyIfChainHasNotProgressed() {
    when(eth1Provider.getLatestEth1Block()).thenReturn(completedFuture(block(1200)));
    tracker.subscribe(subscriber);
    tracker.start();

    verify(subscriber).onHeadUpdated(UInt64.valueOf(1200 - FOLLOW_DISTANCE));

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();

    verifyNoMoreInteractions(subscriber);
  }

  @Test
  void shouldNotNotifyIfChainGoesBackwards() {
    // ETH1 chain head can go backwards.  We ignore this because we've already processed at the
    // follow distance so just delay any further updates until the chain progresses forward again.
    when(eth1Provider.getLatestEth1Block())
        .thenReturn(completedFuture(block(1200)))
        .thenReturn(completedFuture(block(1199)));
    tracker.subscribe(subscriber);
    tracker.start();

    verify(subscriber).onHeadUpdated(UInt64.valueOf(1200 - FOLLOW_DISTANCE));

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();

    verifyNoMoreInteractions(subscriber);
  }

  @Test
  void shouldNotPollHeadOnceStopped() {
    when(eth1Provider.getLatestEth1Block()).thenReturn(completedFuture(block(2000)));

    tracker.start();

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    tracker.stop();
    asyncRunner.executeQueuedActions();
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  private Block block(final long blockNumber) {
    final Block block = new Block();
    block.setNumber("0x" + Long.toHexString(blockNumber));
    return block;
  }
}
