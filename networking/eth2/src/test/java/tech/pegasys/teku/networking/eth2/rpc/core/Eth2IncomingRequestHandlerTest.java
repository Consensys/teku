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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.networking.eth2.rpc.Utils;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.util.Waiter;
import tech.pegasys.teku.util.async.SafeFuture;

public abstract class Eth2IncomingRequestHandlerTest
    extends AbstractRequestHandlerTest<
        Eth2IncomingRequestHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>> {

  private final BeaconState state = mock(BeaconState.class);
  private final BeaconBlocksByRangeRequestMessage request =
      new BeaconBlocksByRangeRequestMessage(UnsignedLong.ONE, UnsignedLong.ONE, UnsignedLong.ONE);

  private Bytes requestData;

  @BeforeEach
  @Override
  public void setup() {
    super.setup();
    requestData = beaconChainMethods.beaconBlocksByRange().encodeRequest(request);

    lenient().when(state.getSlot()).thenReturn(UnsignedLong.ONE);
    lenient()
        .when(combinedChainDataClient.getNonfinalizedBlockState(any()))
        .thenReturn(Optional.of(state));
    lenient()
        .when(combinedChainDataClient.getBlockAtSlotExact(any(), any()))
        .thenAnswer(i -> getBlockAtSlot(i.getArgument(0)));
  }

  @Override
  protected Eth2IncomingRequestHandler<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      createRequestHandler(final BeaconChainMethods beaconChainMethods) {
    return beaconChainMethods.beaconBlocksByRange().createIncomingRequestHandler();
  }

  private SafeFuture<Optional<SignedBeaconBlock>> getBlockAtSlot(final UnsignedLong slot) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot.longValue());
    return SafeFuture.completedFuture(Optional.of(block));
  }

  @Test
  public void shouldCloseStreamIfRequestNotReceivedInTime() {
    verify(rpcStream, never()).close();
    verify(rpcStream, never()).closeWriteStream();

    // When timeout completes, we should close stream
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();

    verify(rpcStream).close();
    verify(rpcStream, never()).closeWriteStream();
  }

  @Test
  public void shouldNotCloseStreamIfRequestReceivedInTime() throws Exception {
    // Deliver request and wait for it to be processed
    reqHandler.processData(nodeId, rpcStream, Utils.toByteBuf(requestData));
    Waiter.waitFor(() -> assertThat(reqHandler.hasRequestBeenReceived()).isTrue());

    // When timeout completes, we should not close stream
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();
    asyncRunner.executeQueuedActions();
    verify(rpcStream, never()).close();
  }

  public static class Eth2IncomingRequestHandlerTest_ssz extends Eth2IncomingRequestHandlerTest {

    @Override
    protected RpcEncoding getRpcEncoding() {
      return RpcEncoding.SSZ;
    }
  }

  public static class Eth2IncomingRequestHandlerTest_sszSnappy
      extends Eth2IncomingRequestHandlerTest {

    @Override
    protected RpcEncoding getRpcEncoding() {
      return RpcEncoding.SSZ_SNAPPY;
    }
  }
}
