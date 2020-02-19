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
import tech.pegasys.artemis.beaconrestapi.schema.BeaconChainHeadResponse;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BeaconChainHeadHandlerTest {
  private Context context = mock(Context.class);
  private ChainStorageClient client = mock(ChainStorageClient.class);

  private final UnsignedLong headBlockSlot = DataStructureUtil.randomUnsignedLong(90);
  private final UnsignedLong headBlockEpoch = BeaconStateUtil.compute_epoch_at_slot(headBlockSlot);
  private final Bytes32 headBlockRoot = DataStructureUtil.randomBytes32(91);

  private final UnsignedLong finalizedBlockEpoch = DataStructureUtil.randomUnsignedLong(92);
  private final UnsignedLong finalizedBlockSlot =
      BeaconStateUtil.compute_start_slot_at_epoch(finalizedBlockEpoch);
  private final Bytes32 finalizedBlockRoot = DataStructureUtil.randomBytes32(93);

  private final UnsignedLong justifiedBlockEpoch = DataStructureUtil.randomUnsignedLong(94);
  private final UnsignedLong justifiedBlockSlot =
      BeaconStateUtil.compute_start_slot_at_epoch(justifiedBlockEpoch);
  private final Bytes32 justifiedBlockRoot = DataStructureUtil.randomBytes32(95);

  @Test
  public void shouldReturnBeaconChainHeadResponse() throws Exception {
    when(client.getBestSlot()).thenReturn(headBlockSlot);
    when(client.getBestBlockRoot()).thenReturn(headBlockRoot);

    when(client.getFinalizedRoot()).thenReturn(finalizedBlockRoot);
    when(client.getFinalizedEpoch()).thenReturn(finalizedBlockEpoch);

    when(client.getJustifiedRoot()).thenReturn(justifiedBlockRoot);
    when(client.getJustifiedEpoch()).thenReturn(justifiedBlockEpoch);

    BeaconChainHeadHandler handler = new BeaconChainHeadHandler(client);
    handler.handle(context);

    verify(context).result(JsonProvider.objectToJSON(chainHeadResponse()));
  }

  @Test
  public void shouldReturnNoContentWhenHeadBlockRootIsNull() throws Exception {
    when(client.getBestBlockRoot()).thenReturn(null);

    BeaconChainHeadHandler handler = new BeaconChainHeadHandler(client);
    handler.handle(context);

    verify(context).status(SC_NO_CONTENT);
  }

  private BeaconChainHeadResponse chainHeadResponse() {
    BeaconChainHeadResponse response =
        new BeaconChainHeadResponse(
            headBlockSlot,
            headBlockEpoch,
            headBlockRoot,
            finalizedBlockSlot,
            finalizedBlockEpoch,
            finalizedBlockRoot,
            justifiedBlockSlot,
            justifiedBlockEpoch,
            justifiedBlockRoot);
    return response;
  }
}
