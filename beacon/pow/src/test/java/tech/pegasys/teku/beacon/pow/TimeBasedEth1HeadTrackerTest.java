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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.subscribers.ValueObserver;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigLoader;

class TimeBasedEth1HeadTrackerTest {

  private static final int FOLLOW_DISTANCE = 10;
  private static final int SECONDS_PER_BLOCK = 2;
  private static final int FOLLOW_TIME = FOLLOW_DISTANCE * SECONDS_PER_BLOCK;

  private final Spec spec =
      TestSpecFactory.createPhase0(
          SpecConfigLoader.loadConfig(
              "minimal",
              builder ->
                  builder
                      .eth1FollowDistance(UInt64.valueOf(FOLLOW_DISTANCE))
                      .secondsPerEth1Block(SECONDS_PER_BLOCK)));
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final Eth1Provider eth1Provider = mock(Eth1Provider.class);

  @SuppressWarnings("unchecked")
  private final ValueObserver<UInt64> subscriber = mock(ValueObserver.class);

  private final TimeBasedEth1HeadTracker headTracker =
      new TimeBasedEth1HeadTracker(spec, timeProvider, asyncRunner, eth1Provider);

  @BeforeEach
  void setUp() {
    headTracker.subscribe(subscriber);
    when(eth1Provider.getEth1Block(any(UInt64.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @Test
  void init_shouldNotNotifyAnyHeadIfGenesisIsNotOldEnough() {
    timeProvider.advanceTimeBySeconds(FOLLOW_TIME);
    withBlockTimestamps(5, 10, 15);
    assertThat(headTracker.init()).isCompleted();
    verify(eth1Provider).getGuaranteedLatestEth1Block();
    verify(eth1Provider).getEth1Block(UInt64.ZERO);
    verifyNoInteractions(subscriber);

    // Should wait until the genesis block is old enough
    assertThat(headTracker.getDelayUntilNextAdvance()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void init_shouldLoadCurrentHeadBlockAndUseItAsLatestWhenOldEnough() {
    timeProvider.advanceTimeBySeconds(FOLLOW_TIME + 1000);
    withBlockTimestamps(5, 10, 100);
    assertThat(headTracker.init()).isCompleted();

    verify(eth1Provider).getGuaranteedLatestEth1Block();
    verify(subscriber).onValueChanged(UInt64.valueOf(2));
  }

  @Test
  void init_shouldRetrieveBlockAtFollowDistanceIfHeadNotOldEnough() {
    timeProvider.advanceTimeBySeconds(FOLLOW_TIME + 150);
    final List<Block> blocks =
        withBlockTimestamps(1, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100);
    assertThat(headTracker.init()).isCompleted();

    verify(eth1Provider).getGuaranteedLatestEth1Block();
    final int followDistanceBlockNumber = blocks.size() - FOLLOW_DISTANCE - 1;
    verifyBlockRequested(followDistanceBlockNumber);
    verify(subscriber).onValueChanged(UInt64.valueOf(followDistanceBlockNumber));
    verifyNoMoreInteractions(subscriber);

    // Since follow distance block is old enough, next advance should begin searching forward
    assertThat(headTracker.getDelayUntilNextAdvance()).isEqualTo(Duration.ZERO);
    assertThat(headTracker.advance()).isCompleted();
    verifyBlockRequested(followDistanceBlockNumber + 1);
    verifyNoMoreInteractions(subscriber);
  }

  @Test
  void init_shouldNotNotifyBlockAtFollowDistanceIfItIsNotOldEnough() {
    timeProvider.advanceTimeBySeconds(FOLLOW_TIME + 150);
    withBlockTimestamps(1, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200);
    assertThat(headTracker.init()).isCompleted();

    verifyNoMoreInteractions(subscriber);
  }

  @Test
  void advance_shouldWalkBackwardsIfBlockInTimeRangeHasNotYetBeenFound() {
    timeProvider.advanceTimeBySeconds(FOLLOW_TIME + 150);
    withBlockTimestamps(1, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1300);
    assertThat(headTracker.init()).isCompleted();
    verifyBlockRequested(3);
    verifyNoMoreInteractions(subscriber);

    // Still haven't reached a block inside the range
    assertThat(headTracker.advance()).isCompleted();
    verifyBlockRequested(2);
    verifyNoMoreInteractions(subscriber);

    // Finally reach a block in the range
    assertThat(headTracker.advance()).isCompleted();
    verifyBlockRequested(1);
    verify(subscriber).onValueChanged(UInt64.valueOf(1));
    verifyNoMoreInteractions(subscriber);
  }

  @Test
  void advance_shouldStepForwardAfterBackwardsSearchCompletes() {
    timeProvider.advanceTimeBySeconds(FOLLOW_TIME + 150);
    withBlockTimestamps(1, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1300);
    assertThat(headTracker.init()).isCompleted();
    verifyBlockRequested(3);
    verifyNoMoreInteractions(subscriber);

    // Still haven't reached a block inside the range
    assertThat(headTracker.advance()).isCompleted();
    verifyBlockRequested(2);
    verifyNoMoreInteractions(subscriber);
    assertThat(headTracker.getDelayUntilNextAdvance()).isEqualTo(Duration.ZERO);

    // Finally reach a block in the range
    assertThat(headTracker.advance()).isCompleted();
    verifyBlockRequested(1);
    verify(subscriber).onValueChanged(UInt64.valueOf(1));
    verifyNoMoreInteractions(subscriber);

    // Should start stepping forward again, waiting for block 2 to be within the time period
    assertThat(headTracker.getDelayUntilNextAdvance()).isEqualTo(Duration.ofSeconds(50));

    timeProvider.advanceTimeBySeconds(50);
    assertThat(headTracker.advance()).isCompleted();
    // Shouldn't request block 2 again as we already have it
    verifyBlockRequested(2);
    verify(subscriber).onValueChanged(UInt64.valueOf(2));
  }

  @Test
  void advance_shouldRetrieveNextBlockIfNextCandidateHeadIsNowOldEnough() {
    timeProvider.advanceTimeBySeconds(FOLLOW_TIME + 150);
    withBlockTimestamps(1, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100);
    // Should notify new head without waiting for next request to complete
    final long nextHeadBlockNumber = advanceUtilNextBlockIsNotInRange();

    final Duration delayUntilNextBlockIncluded = headTracker.getDelayUntilNextAdvance();
    timeProvider.advanceTimeBy(delayUntilNextBlockIncluded);

    assertThat(headTracker.advance()).isCompleted();
    verify(subscriber).onValueChanged(UInt64.valueOf(nextHeadBlockNumber));

    assertThat(headTracker.getDelayUntilNextAdvance()).isEqualTo(Duration.ofSeconds(100));
  }

  @Test
  void advance_shouldDoNothingIfNextBlockNotInTimePeriodYet() {
    // Normally wouldn't be called but worth covering just in case
    timeProvider.advanceTimeBySeconds(FOLLOW_TIME + 150);
    withBlockTimestamps(1, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100);
    advanceUtilNextBlockIsNotInRange();

    assertThat(headTracker.getDelayUntilNextAdvance()).isEqualTo(Duration.ofSeconds(50));

    reset(eth1Provider, subscriber); // Ignore any previous calls.
    assertThat(headTracker.advance()).isCompleted();
    verifyNoMoreInteractions(eth1Provider);
    verifyNoMoreInteractions(subscriber);
  }

  @Test
  void getDelayUntilNextAdvance_shouldWaitBlockPeriodWhenInitialHeadIsAlreadyOldEnough() {
    // Should be targeting seconds per block after the current head block
    timeProvider.advanceTimeBySeconds(FOLLOW_TIME + 500);
    withBlockTimestamps(1, 100, 200);

    assertThat(headTracker.init()).isCompleted();
    verify(subscriber).onValueChanged(UInt64.valueOf(2));

    assertThat(headTracker.getDelayUntilNextAdvance())
        .isEqualTo(Duration.ofSeconds(SECONDS_PER_BLOCK));

    // Should get closer as time progresses
    timeProvider.advanceTimeBySeconds(1);
    assertThat(headTracker.getDelayUntilNextAdvance())
        .isEqualTo(Duration.ofSeconds(SECONDS_PER_BLOCK - 1));

    // And should reset to seconds per block after advance tries to get the next block again
    timeProvider.advanceTimeBySeconds(SECONDS_PER_BLOCK);
    assertThat(headTracker.advance()).isCompleted();
    verifyBlockRequested(3);
    verifyNoMoreInteractions(subscriber);

    assertThat(headTracker.getDelayUntilNextAdvance())
        .isEqualTo(Duration.ofSeconds(SECONDS_PER_BLOCK));
  }

  @Test
  void getDelayUntilNextAdvance_shouldWaitBlockPeriodBeforeAdvancingWhenHeadBlockReached() {
    timeProvider.advanceTimeBySeconds(FOLLOW_TIME + 150);
    final List<Block> blocks =
        withBlockTimestamps(1, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100);
    advanceUtilNextBlockIsNotInRange();
    assertThat(headTracker.getDelayUntilNextAdvance()).isEqualTo(Duration.ofSeconds(50));

    // Make all blocks old enough
    timeProvider.advanceTimeBySeconds(1000);
    while (headTracker.getDelayUntilNextAdvance().equals(Duration.ZERO)) {
      assertThat(headTracker.advance()).isCompleted();
    }
    verify(subscriber).onValueChanged(UInt64.valueOf(blocks.size() - 1));
    // Should have tried to get the next block but found it doesn't exist
    verifyBlockRequested(blocks.size());

    assertThat(headTracker.getDelayUntilNextAdvance())
        .isEqualTo(Duration.ofSeconds(SECONDS_PER_BLOCK));

    // Should get closer as time progresses
    timeProvider.advanceTimeBySeconds(1);
    assertThat(headTracker.getDelayUntilNextAdvance())
        .isEqualTo(Duration.ofSeconds(SECONDS_PER_BLOCK - 1));

    // And should reset to seconds per block after advance tries to get the next block again
    timeProvider.advanceTimeBySeconds(SECONDS_PER_BLOCK);
    assertThat(headTracker.advance()).isCompleted();
  }

  @Test
  @SuppressWarnings("unchecked")
  void verifyLateSubscriberGetsHeadEvent() {
    timeProvider.advanceTimeBySeconds(FOLLOW_TIME + 1000);
    withBlockTimestamps(5, 10, 100);
    assertThat(headTracker.init()).isCompleted();

    verify(eth1Provider).getGuaranteedLatestEth1Block();
    verify(subscriber).onValueChanged(UInt64.valueOf(2));

    final ValueObserver<UInt64> subscriber2 = mock(ValueObserver.class);
    headTracker.subscribe(subscriber2);
    verify(subscriber2).onValueChanged(UInt64.valueOf(2));
  }

  @Test
  void shouldNotLogStacktraceWhenExecutionClientGoesOffline() {
    try (LogCaptor logCaptor = LogCaptor.forClass(TimeBasedEth1HeadTracker.class)) {
      final String exceptionMessage = "Failed to connect to eth1 client";
      final String errorLogMessage = "Failed to update eth1 chain head - " + exceptionMessage;
      headTracker.onError(new ConnectException(exceptionMessage));
      assertThat(logCaptor.getErrorLogs()).containsExactly(errorLogMessage);
      assertThat(logCaptor.getThrowable(0)).isEmpty();
    }
  }

  private void verifyBlockRequested(final long blockNumber) {
    verify(eth1Provider).getEth1Block(UInt64.valueOf(blockNumber));
  }

  private long advanceUtilFirstBlockInRangeFound() {
    final AtomicReference<UInt64> firstHead = new AtomicReference<>();
    headTracker.subscribe(firstHead::set);
    assertThat(headTracker.init()).isCompleted();
    verify(eth1Provider).getGuaranteedLatestEth1Block();
    while (firstHead.get() == null) {
      assertThat(headTracker.advance()).isCompleted();
    }
    verify(subscriber).onValueChanged(firstHead.get());
    return firstHead.get().longValue();
  }

  private long advanceUtilNextBlockIsNotInRange() {
    long nextBlockNumber = advanceUtilFirstBlockInRangeFound();
    while (headTracker.getDelayUntilNextAdvance().equals(Duration.ZERO)) {
      assertThat(headTracker.advance()).isCompleted();
      nextBlockNumber++;
    }
    return nextBlockNumber;
  }

  private List<Block> withBlockTimestamps(final long... timestamps) {
    final List<Block> blocks = new ArrayList<>();
    for (int blockNumber = 0; blockNumber < timestamps.length; blockNumber++) {
      final Block block = new Block();
      block.setNumber("0x" + Integer.toHexString(blockNumber));
      block.setTimestamp("0x" + Long.toHexString(timestamps[blockNumber]));
      when(eth1Provider.getEth1Block(UInt64.valueOf(blockNumber)))
          .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

      blocks.add(block);
    }

    when(eth1Provider.getGuaranteedLatestEth1Block())
        .thenReturn(SafeFuture.completedFuture(blocks.get(blocks.size() - 1)));
    return blocks;
  }
}
