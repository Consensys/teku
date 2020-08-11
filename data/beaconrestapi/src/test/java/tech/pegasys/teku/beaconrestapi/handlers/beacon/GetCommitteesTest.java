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

package tech.pegasys.teku.beaconrestapi.handlers.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_FINALIZED;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

public class GetCommitteesTest {
  private final StorageSystem storageSystem =
      InMemoryStorageSystem.createEmptyLatestStorageSystem(StateStorageMode.ARCHIVE);
  private UInt64 slot;
  private UInt64 epoch;
  private CombinedChainDataClient combinedChainDataClient;

  private final JsonProvider jsonProvider = new JsonProvider();
  private final Context context = mock(Context.class);
  private final ChainDataProvider provider = mock(ChainDataProvider.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  @BeforeEach
  public void setup() {
    slot = UInt64.valueOf(SLOTS_PER_EPOCH * 3);
    storageSystem.chainUpdater().initializeGenesis();
    SignedBlockAndState bestBlock = storageSystem.chainUpdater().advanceChain(slot);
    storageSystem.chainUpdater().updateBestBlock(bestBlock);

    combinedChainDataClient = storageSystem.combinedChainDataClient();
    epoch = slot.dividedBy(UInt64.valueOf(SLOTS_PER_EPOCH));
  }

  @Test
  public void shouldReturnBadRequestWhenNoEpochIsSupplied() throws Exception {
    ChainDataProvider provider = new ChainDataProvider(null, combinedChainDataClient);
    final GetCommittees handler = new GetCommittees(provider, jsonProvider);

    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenEpochIsTooLarge() throws Exception {
    when(context.queryParamMap())
        .thenReturn(Map.of(EPOCH, List.of(Constants.FAR_FUTURE_EPOCH.toString())));
    ChainDataProvider provider = new ChainDataProvider(null, combinedChainDataClient);
    final GetCommittees handler = new GetCommittees(provider, jsonProvider);

    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldHandleFutureEpoch() throws Exception {
    final GetCommittees handler = new GetCommittees(provider, jsonProvider);

    final UInt64 futureEpoch = epoch.plus(UInt64.ONE);
    final UInt64 epochSlot = compute_start_slot_at_epoch(futureEpoch);
    when(context.queryParamMap()).thenReturn(Map.of(EPOCH, List.of(futureEpoch.toString())));
    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.isFinalized(epochSlot)).thenReturn(false);
    when(provider.getCommitteesAtEpoch(futureEpoch))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    handler.handle(context);

    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(provider).getCommitteesAtEpoch(futureEpoch);
    SafeFuture<String> future = args.getValue();
    verify(context).status(SC_NOT_FOUND);
    assertThat(future.get()).isNull();
  }

  @Test
  public void shouldHandleMissingFinalizedState() throws Exception {
    final GetCommittees handler = new GetCommittees(provider, jsonProvider);

    when(context.queryParamMap()).thenReturn(Map.of(EPOCH, List.of("0")));
    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.isFinalized(ZERO)).thenReturn(true);
    when(provider.getCommitteesAtEpoch(ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    handler.handle(context);

    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_FINALIZED);
    verify(provider).getCommitteesAtEpoch(ZERO);
    SafeFuture<String> future = args.getValue();
    verify(context).status(SC_GONE);
    assertThat(future.get()).isNull();
  }
}
