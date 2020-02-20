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

import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconHeadResponse;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BeaconHeadHandlerTest {
  private Context context = mock(Context.class);
  private final JsonProvider jsonProvider = new JsonProvider();
  private BeaconState rootState = DataStructureUtil.randomBeaconState(1);
  private final UnsignedLong bestSlot = UnsignedLong.valueOf(51234);

  private final ChainStorageClient storageClient = mock(ChainStorageClient.class);

  @Test
  public void shouldReturnBeaconHead() throws Exception {
    BeaconHeadHandler handler = new BeaconHeadHandler(storageClient, jsonProvider);
    Bytes32 blockRoot = Bytes32.random();
    BeaconHeadResponse head =
        new BeaconHeadResponse(bestSlot, blockRoot, rootState.hash_tree_root());

    when(storageClient.getBestBlockRoot()).thenReturn(blockRoot);
    when(storageClient.getBestBlockRootState()).thenReturn(rootState);
    when(storageClient.getBestSlot()).thenReturn(bestSlot);

    handler.handle(context);

    verify(context).result(jsonProvider.objectToJSON(head));
  }

  @Test
  public void shouldReturnNoContentIfBlockRootNotSet() throws Exception {
    BeaconHeadHandler handler = new BeaconHeadHandler(storageClient, jsonProvider);
    when(storageClient.getBestBlockRoot()).thenReturn(null);
    handler.handle(context);

    verify(context).status(SC_NO_CONTENT);
  }
}
