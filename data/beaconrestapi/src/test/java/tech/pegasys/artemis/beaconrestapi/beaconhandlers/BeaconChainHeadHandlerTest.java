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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconChainHead;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;

@ExtendWith(MockitoExtension.class)
public class BeaconChainHeadHandlerTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  private Context context = mock(Context.class);
  private ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);
  private BeaconState beaconState = DataStructureUtil.randomBeaconState(77);

  private Checkpoint finalizedCheckpoint = beaconState.getFinalized_checkpoint();
  private Checkpoint justifiedCheckpoint = beaconState.getCurrent_justified_checkpoint();
  private Checkpoint previousJustifiedCheckpoint = beaconState.getPrevious_justified_checkpoint();

  private final Bytes32 headBlockRoot = DataStructureUtil.randomBytes32(91);
  private final UnsignedLong headBlockSlot = beaconState.getSlot();
  private final UnsignedLong headBlockEpoch = compute_epoch_at_slot(headBlockSlot);

  @Captor private ArgumentCaptor<SafeFuture<String>> args;

  @Test
  public void shouldReturnBeaconChainHeadResponse() throws Exception {
    final BeaconChainHeadHandler handler =
        new BeaconChainHeadHandler(chainDataProvider, jsonProvider);

    when(chainDataProvider.getBestBlockRoot()).thenReturn(Optional.of(headBlockRoot));
    when(chainDataProvider.getStateByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));

    handler.handle(context);

    verify(context).result(args.capture());
    assertThat(args.getValue().get()).isEqualTo(jsonProvider.objectToJSON(chainHeadResponse()));
  }

  @Test
  public void shouldReturnNoContentWhenHeadBlockRootIsNull() throws Exception {
    final BeaconChainHeadHandler handler =
        new BeaconChainHeadHandler(chainDataProvider, jsonProvider);

    when(chainDataProvider.getBestBlockRoot()).thenReturn(Optional.empty());

    handler.handle(context);

    verify(context).status(SC_NO_CONTENT);
  }

  private BeaconChainHead chainHeadResponse() {
    return new BeaconChainHead(
        headBlockSlot,
        headBlockEpoch,
        headBlockRoot,
        finalizedCheckpoint.getEpochSlot(),
        finalizedCheckpoint.getEpoch(),
        finalizedCheckpoint.getRoot(),
        justifiedCheckpoint.getEpochSlot(),
        justifiedCheckpoint.getEpoch(),
        justifiedCheckpoint.getRoot(),
        previousJustifiedCheckpoint.getEpochSlot(),
        previousJustifiedCheckpoint.getEpoch(),
        previousJustifiedCheckpoint.getRoot());
  }
}
