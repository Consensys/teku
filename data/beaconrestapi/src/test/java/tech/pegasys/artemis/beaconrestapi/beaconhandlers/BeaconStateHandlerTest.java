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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.ROOT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.util.async.SafeFuture;

@ExtendWith(MockitoExtension.class)
public class BeaconStateHandlerTest {
  private static BeaconState beaconState;
  private static Bytes32 blockRoot;
  private static UnsignedLong slot;
  private static CombinedChainDataClient combinedChainDataClient;
  private static HistoricalChainData historicalChainData = mock(HistoricalChainData.class);

  private final JsonProvider jsonProvider = new JsonProvider();
  private final Context context = mock(Context.class);
  private final String missingRoot = Bytes32.leftPad(Bytes.fromHexString("0xff")).toHexString();

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
  }

  @Test
  public void shouldReturnNotFoundWhenQueryAgainstMissingRootObject() throws Exception {
    final BeaconStateHandler handler =
        new BeaconStateHandler(combinedChainDataClient, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(ROOT, List.of(missingRoot)));
    when(historicalChainData.getFinalizedStateByBlockRoot(Bytes32.fromHexString(missingRoot)))
        .thenReturn(completedFuture(Optional.empty()));

    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnBadRequestWhenNoParameterSpecified() throws Exception {
    final BeaconStateHandler handler =
        new BeaconStateHandler(combinedChainDataClient, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of());

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenBadSlotSpecified() throws Exception {
    final BeaconStateHandler handler =
        new BeaconStateHandler(combinedChainDataClient, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of("not-an-int")));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBeaconStateObjectWhenQueryByRoot() throws Exception {
    final BeaconStateHandler handler =
        new BeaconStateHandler(combinedChainDataClient, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(ROOT, List.of(blockRoot.toHexString())));

    handler.handle(context);

    verify(context).result(args.capture());
    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconState));
  }

  @Test
  public void shouldReturnBadRequestWhenEmptyRootIsSpecified() throws Exception {
    final BeaconStateHandler handler =
        new BeaconStateHandler(combinedChainDataClient, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(ROOT, List.of()));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenEmptySlotIsSpecified() throws Exception {
    final BeaconStateHandler handler =
        new BeaconStateHandler(combinedChainDataClient, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of()));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenMultipleParametersSpecified() throws Exception {
    final BeaconStateHandler handler =
        new BeaconStateHandler(combinedChainDataClient, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of(), ROOT, List.of()));

    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBeaconStateObjectWhenQueryBySlot() throws Exception {
    BeaconStateHandler handler = new BeaconStateHandler(combinedChainDataClient, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of(slot.toString())));

    handler.handle(context);

    verify(context).result(args.capture());
    SafeFuture<String> data = args.getValue();
    assertEquals(data.get(), jsonProvider.objectToJSON(beaconState));
  }

  @Test
  public void shouldReturnNotFoundWhenQueryByMissingSlot() throws Exception {
    BeaconStateHandler handler = new BeaconStateHandler(combinedChainDataClient, jsonProvider);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of("11223344")));

    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnNoContentIfStoreNotDefined() throws Exception {
    ChainStorageClient client = mock(ChainStorageClient.class);
    CombinedChainDataClient combinedClient =
        new CombinedChainDataClient(client, historicalChainData);
    final BeaconStateHandler handler = new BeaconStateHandler(combinedClient, jsonProvider);
    when(client.getStore()).thenReturn(null);
    when(context.queryParamMap()).thenReturn(Map.of(SLOT, List.of("11223344")));

    handler.handle(context);

    verify(context).status(SC_NO_CONTENT);
  }
}
