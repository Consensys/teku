/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.forkchoice;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.executionengine.ExecutePayloadResult;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

class OptimisticHeadValidatorTest {
  private final Spec spec = TestSpecFactory.createMinimalMerge();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final ExecutionEngineChannel executionEngine = mock(ExecutionEngineChannel.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final ForkChoice forkChoice = mock(ForkChoice.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final OptimisticHeadValidator validator =
      new OptimisticHeadValidator(asyncRunner, forkChoice, recentChainData, executionEngine);

  @BeforeEach
  void setUp() {
    when(recentChainData.getStore()).thenReturn(store);
    assertThat(validator.start()).isCompleted();
  }

  @Test
  void shouldExecutePayloadsFromOptimisticChainHeads() {
    final ExecutionPayload payload1 = dataStructureUtil.randomExecutionPayload();
    final BeaconBlock block1 = dataStructureUtil.blockBuilder(1).executionPayload(payload1).build();
    final BeaconBlock block2 = dataStructureUtil.blockBuilder(2).build();

    when(recentChainData.getOptimisticChainHeads())
        .thenReturn(Map.of(block1.getRoot(), block1.getSlot(), block2.getRoot(), block2.getSlot()));

    when(recentChainData.retrieveBlockByRoot(block1.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block1)));
    when(recentChainData.retrieveBlockByRoot(block2.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block2)));

    when(executionEngine.executePayload(
            block1.getBody().getOptionalExecutionPayload().orElseThrow()))
        .thenReturn(SafeFuture.completedFuture(ExecutePayloadResult.VALID));

    final UInt64 latestFinalizedBlockSlot = UInt64.ONE;

    when(store.getLatestFinalizedBlockSlot()).thenReturn(latestFinalizedBlockSlot);

    asyncRunner.executeQueuedActions();

    verify(forkChoice)
        .onExecutionPayloadResult(
            block1.getRoot(), ExecutePayloadResult.VALID, latestFinalizedBlockSlot);
    verifyNoMoreInteractions(forkChoice);

    // Should not execute default payloads
    verify(executionEngine, never())
        .executePayload(block2.getBody().getOptionalExecutionPayload().orElseThrow());
  }

  @Test
  void shouldPeriodicallyReverify() {
    when(recentChainData.getOptimisticChainHeads()).thenReturn(emptyMap());

    // Should run immediately at startup
    asyncRunner.executeDueActions();
    verify(recentChainData).getOptimisticChainHeads();

    // Too soon to retry
    timeProvider.advanceTimeBy(OptimisticHeadValidator.RECHECK_INTERVAL.minusSeconds(1));
    asyncRunner.executeDueActions();
    verify(recentChainData, times(1)).getOptimisticChainHeads();

    // And then retry when interval is reached
    timeProvider.advanceTimeBySeconds(1);
    asyncRunner.executeQueuedActions();
    verify(recentChainData, times(2)).getOptimisticChainHeads();
  }
}
