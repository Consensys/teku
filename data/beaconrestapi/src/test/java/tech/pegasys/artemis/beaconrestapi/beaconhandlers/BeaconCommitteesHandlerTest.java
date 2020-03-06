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

package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import static com.google.common.primitives.UnsignedLong.ZERO;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.CACHE_NONE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.CACHE_ONE_HOUR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.async.SafeFuture;

@ExtendWith(MockitoExtension.class)
public class BeaconCommitteesHandlerTest {
  private static BeaconState beaconState;
  private static Bytes32 blockRoot;
  private static UnsignedLong slot;
  private static UnsignedLong epoch;
  private static CombinedChainDataClient combinedChainDataClient;
  private static HistoricalChainData historicalChainData = mock(HistoricalChainData.class);
  private String EMPTY_LIST = "[]";

  private final JsonProvider jsonProvider = new JsonProvider();
  private final Context context = mock(Context.class);
  @Mock private ChainDataProvider provider;

  @Captor private ArgumentCaptor<SafeFuture<String>> args;

  @BeforeAll
  public static void setup() {
    final EventBus localEventBus = new EventBus();
    final ChainStorageClient storageClient = ChainStorageClient.memoryOnlyClient(localEventBus);
    beaconState = DataStructureUtil.randomBeaconState(11233);
    storageClient.initializeFromGenesis(beaconState);
    combinedChainDataClient = new CombinedChainDataClient(storageClient, historicalChainData);
    blockRoot = storageClient.getBestBlockRoot();
    slot = storageClient.getBlockState(blockRoot).get().getSlot();
    epoch = slot.dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
  }

  @Test
  public void shouldReturnEmptyListWhenStateAtSlotIsNotFound() throws Exception {
    final BeaconCommitteesHandler handler = new BeaconCommitteesHandler(provider, jsonProvider);

    when(context.queryParamMap()).thenReturn(Map.of(EPOCH, List.of("0")));
    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getCommitteesAtEpoch(ZERO)).thenReturn(SafeFuture.completedFuture(List.of()));
    handler.handle(context);

    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(provider).getCommitteesAtEpoch(ZERO);
    SafeFuture<String> future = args.getValue();
    assertEquals(future.get(), EMPTY_LIST);
  }

  @Test
  public void shouldReturnBadRequestWhenNoEpochIsSupplied() throws Exception {
    ChainDataProvider provider = new ChainDataProvider(null, combinedChainDataClient);
    final BeaconCommitteesHandler handler = new BeaconCommitteesHandler(provider, jsonProvider);
    handler.handle(context);

    verify(context, never()).header(anyString(), anyString());
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnEmptyListWhenAFutureEpochIsRequested() throws Exception {
    final BeaconCommitteesHandler handler = new BeaconCommitteesHandler(provider, jsonProvider);
    final UnsignedLong futureEpoch = slot.plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH));

    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getCommitteesAtEpoch(futureEpoch))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    when(context.queryParamMap()).thenReturn(Map.of(EPOCH, List.of(futureEpoch.toString())));
    handler.handle(context);

    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), EMPTY_LIST);
  }

  @Test
  public void shouldReturnListOfCommitteeAssignments() throws Exception {
    ChainStorageClient client = mock(ChainStorageClient.class);
    Store store = mock(Store.class);
    CombinedChainDataClient combinedClient =
        new CombinedChainDataClient(client, historicalChainData);
    ChainDataProvider provider = new ChainDataProvider(client, combinedClient);
    final BeaconCommitteesHandler handler = new BeaconCommitteesHandler(provider, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(EPOCH, List.of(epoch.toString())));
    when(client.getFinalizedEpoch()).thenReturn(ZERO);
    when(store.getBlockState(blockRoot)).thenReturn(beaconState);
    when(client.getStateBySlot(any())).thenReturn(Optional.of(beaconState));
    when(client.getStore()).thenReturn(store);
    when(client.getBestBlockRoot()).thenReturn(blockRoot);

    handler.handle(context);

    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_ONE_HOUR);
    SafeFuture<String> future = args.getValue();
    String data = future.get();

    assertEquals(SLOTS_PER_EPOCH, StringUtils.countMatches(data, "\"committee\":"));
  }

  @Test
  public void shouldReturnNoContentIfStoreNotDefined() throws Exception {
    when(context.queryParamMap()).thenReturn(Map.of(EPOCH, List.of(epoch.toString())));
    ChainStorageClient client = mock(ChainStorageClient.class);
    CombinedChainDataClient combinedClient =
        new CombinedChainDataClient(client, historicalChainData);
    ChainDataProvider provider = new ChainDataProvider(client, combinedClient);
    final BeaconCommitteesHandler handler = new BeaconCommitteesHandler(provider, jsonProvider);
    when(client.getStore()).thenReturn(null);

    handler.handle(context);

    verify(context).status(SC_NO_CONTENT);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
  }
}
