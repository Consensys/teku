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

import io.javalin.http.Context;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;

public class BeaconStateHandlerTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  private final ChainStorageClient storageClient = mock(ChainStorageClient.class);
  private final Store store = mock(Store.class);
  private final Bytes32 blockRoot = Bytes32.random();
  private final Context context = mock(Context.class);
  private final BeaconState beaconState = DataStructureUtil.randomBeaconState(11233);

  @Test
  public void shouldReturnNotFoundWhenQueryAgainstMissingRootObject() throws Exception {
    BeaconStateHandler handler = new BeaconStateHandler(storageClient, jsonProvider);

    when(storageClient.getStore()).thenReturn(store);
    when(store.getBlockState(blockRoot)).thenReturn(null);
    when(context.queryParam("root")).thenReturn(blockRoot.toHexString());

    handler.handle(context);

    verify(context).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnBeaconStateObjectWhenFound() throws Exception {
    BeaconStateHandler handler = new BeaconStateHandler(storageClient, jsonProvider);

    when(storageClient.getStore()).thenReturn(store);
    when(store.getBlockState(blockRoot)).thenReturn(beaconState);
    when(context.queryParam("root")).thenReturn(blockRoot.toHexString());

    handler.handle(context);

    verify(context).result(jsonProvider.objectToJSON(beaconState));
  }
}
