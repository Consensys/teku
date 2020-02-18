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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;

public class FinalizedCheckpointHandlerTest {
  private Context mockContext = Mockito.mock(Context.class);
  private ChainStorageClient mockClient = Mockito.mock(ChainStorageClient.class);
  private Store mockStore = Mockito.mock(Store.class);

  private final Checkpoint checkpoint = DataStructureUtil.randomCheckpoint(99);

  @Test
  public void shouldReturnCheckpoint() throws Exception {
    when(mockClient.getStore()).thenReturn(mockStore);
    when(mockStore.getFinalizedCheckpoint()).thenReturn(checkpoint);

    FinalizedCheckpointHandler handler = new FinalizedCheckpointHandler(mockClient);
    handler.handle(mockContext);

    verify(mockContext).result(JsonProvider.objectToJSON(checkpoint));
  }
}
