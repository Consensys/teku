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

import static java.util.Optional.empty;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconBlockResponse;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.async.SafeFuture;

public class BeaconBlockHandlerTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  private final ChainStorageClient storageClient = mock(ChainStorageClient.class);
  private final HistoricalChainData historicalChainData = mock(HistoricalChainData.class);
  private final Store store = mock(Store.class);
  private final Bytes32 blockRoot = Bytes32.random();
  private final Context context = mock(Context.class);

  private final SignedBeaconBlock signedBeaconBlock =
      DataStructureUtil.randomSignedBeaconBlock(1, 1);
  private final BeaconBlockHandler handler =
      new BeaconBlockHandler(storageClient, historicalChainData, jsonProvider);

  @Test
  public void shouldReturnNotFoundWhenRootQueryAndStoreNull() throws Exception {
    final String rootKey = "0xf22e4ec2";
    final Map<String, List<String>> params = Map.of("root", List.of(rootKey));

    when(storageClient.getStore()).thenReturn(null);
    when(context.queryParamMap()).thenReturn(params);

    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnNotFoundWhenValidParamNotSpecified() throws Exception {
    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnNotFoundWhenEpochQueryAndBlockNotFound() throws Exception {
    final Map<String, List<String>> params = Map.of("epoch", List.of("1"));

    when(context.queryParamMap()).thenReturn(params);
    when(storageClient.getBlockRootBySlot(any())).thenReturn(Optional.of(blockRoot));
    when(storageClient.getStore()).thenReturn(store);
    when(store.getBlock(any())).thenReturn(null);
    when(historicalChainData.getFinalizedBlockAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(empty()));

    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnNotFoundWhenSlotQueryAndBlockNotFound() throws Exception {
    final Map<String, List<String>> params = Map.of("slot", List.of("1"));

    when(context.queryParamMap()).thenReturn(params);
    when(storageClient.getStore()).thenReturn(store);
    when(storageClient.getBlockRootBySlot(any())).thenReturn(Optional.of(blockRoot));
    when(store.getBlock(any())).thenReturn(null);
    when(historicalChainData.getFinalizedBlockAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(empty()));

    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnNotFoundWhenEpochQueryAndNoBlockRootAndBlockNotFound() throws Exception {
    final Map<String, List<String>> params = Map.of("epoch", List.of("1"));

    when(context.queryParamMap()).thenReturn(params);
    when(storageClient.getBlockRootBySlot(any())).thenReturn(empty());
    when(historicalChainData.getFinalizedBlockAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(empty()));

    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnNotFoundWhenSlotQueryAndNoBlockRootAndBlockNotFound() throws Exception {
    final Map<String, List<String>> params = Map.of("slot", List.of("1"));

    when(context.queryParamMap()).thenReturn(params);
    when(storageClient.getBlockRootBySlot(any())).thenReturn(empty());
    when(historicalChainData.getFinalizedBlockAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(empty()));

    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnBlockWhenRootParamSpecified() throws Exception {
    final Map<String, List<String>> params =
        Map.of("root", List.of(signedBeaconBlock.getParent_root().toHexString()));

    when(context.queryParamMap()).thenReturn(params);
    when(storageClient.getStore()).thenReturn(store);
    when(store.getSignedBlock(any())).thenReturn(signedBeaconBlock);

    handler.handle(context);

    final String jsonResponse =
        jsonProvider.objectToJSON(new BeaconBlockResponse(signedBeaconBlock));
    verify(context).result(jsonResponse);
  }

  @Test
  public void shouldReturnBlockWhenEpochQuery() throws Exception {
    final Map<String, List<String>> params = Map.of("epoch", List.of("1"));

    when(context.queryParamMap()).thenReturn(params);
    when(storageClient.getBlockRootBySlot(any())).thenReturn(Optional.of(blockRoot));
    when(storageClient.getStore()).thenReturn(store);
    when(store.getSignedBlock(any())).thenReturn(signedBeaconBlock);
    when(historicalChainData.getFinalizedBlockAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(empty()));

    handler.handle(context);

    final String jsonResponse =
        jsonProvider.objectToJSON(new BeaconBlockResponse(signedBeaconBlock));
    verify(context).result(jsonResponse);
  }

  @Test
  public void shouldReturnBlockWhenSlotQuery() throws Exception {
    final Map<String, List<String>> params = Map.of("slot", List.of("1"));

    when(context.queryParamMap()).thenReturn(params);
    when(storageClient.getStore()).thenReturn(store);
    when(storageClient.getBlockRootBySlot(any())).thenReturn(Optional.of(blockRoot));
    when(store.getSignedBlock(any())).thenReturn(signedBeaconBlock);
    when(historicalChainData.getFinalizedBlockAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(empty()));

    handler.handle(context);

    final String jsonResponse =
        jsonProvider.objectToJSON(new BeaconBlockResponse(signedBeaconBlock));
    verify(context).result(jsonResponse);
  }

  @Test
  public void shouldReturnBlockWhenEpochQueryAndNoBlockRoot() throws Exception {
    final Map<String, List<String>> params = Map.of("epoch", List.of("1"));

    when(context.queryParamMap()).thenReturn(params);
    when(storageClient.getBlockRootBySlot(any())).thenReturn(empty());
    when(historicalChainData.getFinalizedBlockAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(signedBeaconBlock)));

    handler.handle(context);

    final String jsonResponse =
        jsonProvider.objectToJSON(new BeaconBlockResponse(signedBeaconBlock));
    verify(context).result(jsonResponse);
  }

  @Test
  public void shouldReturnBlockWhenSlotQueryAndNoBlockRoot() throws Exception {
    final Map<String, List<String>> params = Map.of("slot", List.of("1"));

    when(context.queryParamMap()).thenReturn(params);
    when(storageClient.getBlockRootBySlot(any())).thenReturn(empty());
    when(historicalChainData.getFinalizedBlockAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(signedBeaconBlock)));

    handler.handle(context);

    final String jsonResponse =
        jsonProvider.objectToJSON(new BeaconBlockResponse(signedBeaconBlock));
    verify(context).result(jsonResponse);
  }
}
