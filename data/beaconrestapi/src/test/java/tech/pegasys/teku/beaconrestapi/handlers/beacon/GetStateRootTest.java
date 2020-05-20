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
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.ROOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.async.SafeFuture;

public class GetStateRootTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  public BeaconState beaconStateInternal;
  private Bytes32 blockRoot;
  private UnsignedLong slot;
  private ChainDataProvider provider = mock(ChainDataProvider.class);

  private final JsonProvider jsonProvider = new JsonProvider();
  private final Context context = mock(Context.class);

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  @BeforeEach
  public void setup() {
    final EventBus localEventBus = new EventBus();
    final RecentChainData storageClient = MemoryOnlyRecentChainData.create(localEventBus);
    beaconStateInternal = dataStructureUtil.randomBeaconState();
    storageClient.initializeFromGenesis(beaconStateInternal);
    blockRoot = storageClient.getBestBlockRoot().orElseThrow();
    slot = dataStructureUtil.randomUnsignedLong();
  }

  @BeforeEach
  public void setupProvider() {
    when(provider.isStoreAvailable()).thenReturn(true);
  }

  @Test
  public void shouldReturnBadRequestWhenNoParameterSpecified() throws Exception {
    final GetStateRoot handler = new GetStateRoot(provider, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of());

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenSingleNonSlotParameterSpecified() throws Exception {
    final GetStateRoot handler = new GetStateRoot(provider, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of("foo", List.of()));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenEmptySlotIsSpecified() throws Exception {
    final GetStateRoot handler = new GetStateRoot(provider, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of()));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenMultipleParametersSpecified() throws Exception {
    final GetStateRoot handler = new GetStateRoot(provider, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of(), ROOT, List.of()));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBeaconStateRootWhenQueryBySlot() throws Exception {
    GetStateRoot handler = new GetStateRoot(provider, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of(slot.toString())));
    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getStateRootAtSlot(slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconStateInternal.hash_tree_root())));

    handler.handle(context);

    verify(context).result(args.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconStateInternal.hash_tree_root()));
  }

  @Test
  public void shouldReturnNotFoundWhenQueryByMissingSlot() throws Exception {
    GetStateRoot handler = new GetStateRoot(provider, jsonProvider);
    UnsignedLong nonExistentSlot = UnsignedLong.valueOf(11223344);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of("11223344")));
    when(provider.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(provider.getStateRootAtSlot(nonExistentSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }
}
