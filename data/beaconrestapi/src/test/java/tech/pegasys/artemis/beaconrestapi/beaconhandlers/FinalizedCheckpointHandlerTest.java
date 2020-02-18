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

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;

public class FinalizedCheckpointHandlerTest {
  private Context context = mock(Context.class);
  private ChainStorageClient client = mock(ChainStorageClient.class);
  private Store store = mock(Store.class);

  private final Checkpoint checkpoint = DataStructureUtil.randomCheckpoint(99);

  @Test
  public void shouldReturnCheckpoint() throws Exception {
    when(client.getStore()).thenReturn(store);
    when(store.getFinalizedCheckpoint()).thenReturn(checkpoint);

    FinalizedCheckpointHandler handler = new FinalizedCheckpointHandler(client);
    handler.handle(context);

    verify(context).result(JsonProvider.objectToJSON(checkpoint));
  }

  @Test
  public void shouldReturnNoContentWhenStoreIsNull() throws Exception {
    when(client.getStore()).thenReturn(null);

    FinalizedCheckpointHandler handler = new FinalizedCheckpointHandler(client);
    handler.handle(context);

    verify(context).status(SC_NO_CONTENT);
  }
}
