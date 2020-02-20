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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconChainHeadResponse;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;

public class BeaconChainHeadHandlerTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  private Context context = mock(Context.class);
  private ChainStorageClient storageClient = mock(ChainStorageClient.class);
  private Store store = mock(Store.class);
  private BeaconState beaconState = DataStructureUtil.randomBeaconState(77);

  private Checkpoint finalizedCheckpoint = beaconState.getFinalized_checkpoint();
  private Checkpoint justifiedCheckpoint = beaconState.getCurrent_justified_checkpoint();

  private final Bytes32 headBlockRoot = DataStructureUtil.randomBytes32(91);
  private final UnsignedLong headBlockSlot = beaconState.getSlot();
  private final UnsignedLong headBlockEpoch = compute_epoch_at_slot(headBlockSlot);

  @Test
  public void shouldReturnBeaconChainHeadResponse() throws Exception {
    when(storageClient.getBestBlockRoot()).thenReturn(headBlockRoot);
    when(storageClient.getStore()).thenReturn(store);

    when(store.getBlockState(headBlockRoot)).thenReturn(beaconState);

    BeaconChainHeadHandler handler = new BeaconChainHeadHandler(storageClient, jsonProvider);
    handler.handle(context);

    verify(context).result(jsonProvider.objectToJSON(chainHeadResponse()));
  }

  @Test
  public void shouldReturnNoContentWhenHeadBlockRootIsNull() throws Exception {
    when(storageClient.getBestBlockRoot()).thenReturn(null);

    BeaconChainHeadHandler handler = new BeaconChainHeadHandler(storageClient, jsonProvider);
    handler.handle(context);

    verify(context).status(SC_NO_CONTENT);
  }

  private BeaconChainHeadResponse chainHeadResponse() {
    BeaconChainHeadResponse response =
        new BeaconChainHeadResponse(
            headBlockSlot,
            headBlockEpoch,
            headBlockRoot,
            finalizedCheckpoint.getEpochSlot(),
            finalizedCheckpoint.getEpoch(),
            finalizedCheckpoint.getRoot(),
            justifiedCheckpoint.getEpochSlot(),
            justifiedCheckpoint.getEpoch(),
            justifiedCheckpoint.getRoot());
    return response;
  }
}
