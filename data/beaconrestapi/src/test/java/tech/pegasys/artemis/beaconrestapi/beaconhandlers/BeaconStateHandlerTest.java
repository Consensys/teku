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
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;

public class BeaconStateHandlerTest {

  private final ChainStorageClient mockStorageClient = mock(ChainStorageClient.class);
  private final Store mockStore = mock(Store.class);
  private final Bytes32 blockRoot = Bytes32.random();
  private final Context mockContext = mock(Context.class);
  private final BeaconState mockBeaconState = mock(BeaconState.class);

  @Test
  public void shouldReturnNotFoundWhenQueryAgainstMissingRootObject() throws Exception {
    BeaconStateHandler handler = new BeaconStateHandler(mockStorageClient);

    when(mockStorageClient.getStore()).thenReturn(mockStore);
    when(mockStore.getBlockState(blockRoot)).thenReturn(null);
    when(mockContext.queryParam("root")).thenReturn(blockRoot.toHexString());

    handler.handle(mockContext);

    verify(mockContext).status(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnBeaconStateObjectWhenFound() throws Exception {
    BeaconStateHandler handler = new BeaconStateHandler(mockStorageClient);

    when(mockStorageClient.getStore()).thenReturn(mockStore);
    when(mockStore.getBlockState(blockRoot)).thenReturn(mockBeaconState);
    when(mockContext.queryParam("root")).thenReturn(blockRoot.toHexString());

    handler.handle(mockContext);

    verify(mockContext).result(JsonProvider.objectToJSON(mockBeaconState));
  }
}
