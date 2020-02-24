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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconStateHandler.ROOT_PARAMETER;
import static tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconStateHandler.SLOT_PARAMETER;
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
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.storage.HistoricalChainData;

public class BeaconStateHandlerTest {
  private static EventBus localEventBus;
  private static BeaconState beaconState;
  private static ChainStorageClient storageClient;
  private static Bytes32 blockRoot;
  private static UnsignedLong slot;
  private static CombinedChainDataClient combinedChainDataClient;
  private static HistoricalChainData historicalChainData = mock(HistoricalChainData.class);

  private final JsonProvider jsonProvider = new JsonProvider();
  private final Context context = mock(Context.class);
  private final String missingRoot = Bytes32.leftPad(Bytes.fromHexString("0xff")).toHexString();

  @BeforeAll
  public static void setup() {
    localEventBus = new EventBus();
    beaconState = DataStructureUtil.randomBeaconState(11233);
    storageClient = ChainStorageClient.memoryOnlyClient(localEventBus);
    storageClient.initializeFromGenesis(beaconState);
    combinedChainDataClient = new CombinedChainDataClient(storageClient, historicalChainData);
    blockRoot = storageClient.getBestBlockRoot();
    slot = storageClient.getBlockState(blockRoot).get().getSlot();
  }

  @Test
  public void shouldReturnNotFoundWhenQueryAgainstMissingRootObject() throws Exception {
    final Map<String, List<String>> params = Map.of(ROOT_PARAMETER, List.of(missingRoot));
    final BeaconStateHandler handler =
        new BeaconStateHandler(storageClient, combinedChainDataClient, jsonProvider);

    when(context.queryParamMap()).thenReturn(params);
    when(historicalChainData.getFinalizedStateAtBlock(Bytes32.fromHexString(missingRoot)))
        .thenReturn(completedFuture(Optional.empty()));

    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnBeaconStateObjectWhenQueryByRoot() throws Exception {
    final Map<String, List<String>> params =
        Map.of(ROOT_PARAMETER, List.of(blockRoot.toHexString()));
    BeaconStateHandler handler =
        new BeaconStateHandler(storageClient, combinedChainDataClient, jsonProvider);

    when(context.queryParamMap()).thenReturn(params);

    handler.handle(context);

    verify(context).result(jsonProvider.objectToJSON(beaconState));
  }

  @Test
  public void shouldReturnBeaconStateObjectWhenQueryBySlot() throws Exception {
    final Map<String, List<String>> params = Map.of(SLOT_PARAMETER, List.of(slot.toString()));
    BeaconStateHandler handler =
        new BeaconStateHandler(storageClient, combinedChainDataClient, jsonProvider);

    when(context.queryParamMap()).thenReturn(params);

    handler.handle(context);

    verify(context).result(jsonProvider.objectToJSON(beaconState));
  }

  @Test
  public void shouldReturnNotFoundWhenQueryByMissingSlot() throws Exception {
    final Map<String, List<String>> params = Map.of(SLOT_PARAMETER, List.of("11223344"));
    BeaconStateHandler handler =
        new BeaconStateHandler(storageClient, combinedChainDataClient, jsonProvider);

    when(context.queryParamMap()).thenReturn(params);

    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }
}
