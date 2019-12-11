/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.storage;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.events.GetBlockBySlotRequest;
import tech.pegasys.artemis.storage.events.GetBlockBySlotResponse;

class HistoricalChainDataTest {
  private static final Optional<BeaconBlock> BLOCK =
      Optional.of(DataStructureUtil.randomBeaconBlock(1, 100));
  private final EventBus eventBus = mock(EventBus.class);
  private final HistoricalChainData historicalChainData = new HistoricalChainData(eventBus);

  @Test
  public void shouldRegisterWithEventBus() {
    verify(eventBus).register(historicalChainData);
  }

  @Test
  public void shouldRetrieveBlockBySlot() {
    final CompletableFuture<Optional<BeaconBlock>> result = historicalChainData.getBlockBySlot(ONE);
    verify(eventBus).post(new GetBlockBySlotRequest(ONE));
    assertThat(result).isNotDone();

    historicalChainData.onResponse(new GetBlockBySlotResponse(ONE, BLOCK));
    assertThat(result).isCompletedWithValue(BLOCK);
  }

  @Test
  public void shouldResolveWithEmptyOptionalWhenBlockNotAvailable() {
    final CompletableFuture<Optional<BeaconBlock>> result = historicalChainData.getBlockBySlot(ONE);

    historicalChainData.onResponse(new GetBlockBySlotResponse(ONE, Optional.empty()));
    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void shouldIgnoreBlocksThatDoNotMatchOutstandingRequests() {
    historicalChainData.onResponse(new GetBlockBySlotResponse(ONE, BLOCK));
  }

  @Test
  public void shouldResolveMultipleRequestsForTheSameSlotWithFirstAvailableData() {
    final CompletableFuture<Optional<BeaconBlock>> result1 =
        historicalChainData.getBlockBySlot(ONE);
    final CompletableFuture<Optional<BeaconBlock>> result2 =
        historicalChainData.getBlockBySlot(ONE);

    assertThat(result1).isNotDone();
    assertThat(result2).isNotDone();

    historicalChainData.onResponse(new GetBlockBySlotResponse(ONE, BLOCK));

    assertThat(result1).isCompletedWithValue(BLOCK);
    assertThat(result2).isCompletedWithValue(BLOCK);
  }
}
