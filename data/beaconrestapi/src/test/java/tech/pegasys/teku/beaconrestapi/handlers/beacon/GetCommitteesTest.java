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

import static com.google.common.primitives.UnsignedLong.ZERO;
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
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.async.SafeFuture;

public class GetCommitteesTest {
  private static final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private static BeaconState beaconState;
  private static Bytes32 blockRoot;
  private static UnsignedLong slot;
  private static UnsignedLong epoch;
  private static CombinedChainDataClient combinedChainDataClient;
  private static StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);

  private final JsonProvider jsonProvider = new JsonProvider();
  private final Context context = mock(Context.class);
  private final ChainDataProvider provider = mock(ChainDataProvider.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  @BeforeAll
  public static void setup() {
    final EventBus localEventBus = new EventBus();
    final RecentChainData storageClient = MemoryOnlyRecentChainData.create(localEventBus);
    beaconState = dataStructureUtil.randomBeaconState();
    storageClient.initializeFromGenesis(beaconState);
    combinedChainDataClient = new CombinedChainDataClient(storageClient, historicalChainData);
    blockRoot = storageClient.getBestBlockRoot().orElseThrow();
    slot = storageClient.getBlockState(blockRoot).get().getSlot();
    epoch = slot.dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
  }

  @Test
  public void shouldReturnBadRequestWhenNoEpochIsSupplied() throws Exception {
    ChainDataProvider provider = new ChainDataProvider(null, combinedChainDataClient);
    final GetCommittees handler = new GetCommittees(provider, jsonProvider);

    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldHandleFutureEpoch() throws Exception {
    final GetCommittees handler = new GetCommittees(provider, jsonProvider);

    final UnsignedLong futureEpoch = epoch.plus(UnsignedLong.ONE);
    final UnsignedLong epochSlot = compute_start_slot_at_epoch(futureEpoch);
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
