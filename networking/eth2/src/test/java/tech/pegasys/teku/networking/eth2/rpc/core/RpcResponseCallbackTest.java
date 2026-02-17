/*
 * Copyright Consensys Software Inc., 2025
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.RpcContextCodec;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessagePhase0;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessageSchemaPhase0;

public class RpcResponseCallbackTest {
  protected final RpcStream rpcStream = mock(RpcStream.class);

  final StatusMessageSchemaPhase0 schema = new StatusMessageSchemaPhase0();
  final StatusMessagePhase0 message =
      schema.create(
          Bytes4.leftPad(Bytes.EMPTY),
          Bytes32.fromHexStringLenient("0x01"),
          UInt64.valueOf(2),
          Bytes32.fromHexStringLenient("0x03"),
          UInt64.valueOf(4),
          Optional.empty());

  private final RpcContextCodec<?, StatusMessagePhase0> contextCodec =
      RpcContextCodec.noop(new StatusMessageSchemaPhase0());
  private final RpcResponseEncoder<StatusMessagePhase0, ?> responseEncoder =
      new RpcResponseEncoder<>(
          RpcEncoding.createSszSnappyEncoding(
              TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize()),
          contextCodec);
  final RpcResponseCallback<StatusMessagePhase0> rpcResponseCallback =
      new RpcResponseCallback<>(rpcStream, responseEncoder);

  @BeforeEach
  public void setup() {
    when(rpcStream.closeWriteStream()).thenReturn(SafeFuture.COMPLETE);
    when(rpcStream.writeBytes(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void successCallbackAlwaysRun() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    rpcResponseCallback.alwaysRun(latch::countDown);
    assertThat(latch.getCount()).isEqualTo(1);
    rpcResponseCallback.completeSuccessfully();
    latch.await();
  }

  @Test
  public void successWithDataCallbackAlwaysRun() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    rpcResponseCallback.alwaysRun(latch::countDown);
    assertThat(latch.getCount()).isEqualTo(1);
    rpcResponseCallback.respondAndCompleteSuccessfully(message);
    latch.await();
  }

  @Test
  public void successWithMoreDataCallbackAlwaysRun() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    rpcResponseCallback.alwaysRun(latch::countDown);
    rpcResponseCallback.respond(message).join();
    assertThat(latch.getCount()).isEqualTo(1);
    rpcResponseCallback.respond(message).join();
    assertThat(latch.getCount()).isEqualTo(1);
    rpcResponseCallback.completeSuccessfully();
    latch.await();
  }

  @Test
  public void failedErrorCallbackAlwaysRun() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    rpcResponseCallback.alwaysRun(latch::countDown);
    assertThat(latch.getCount()).isEqualTo(1);
    rpcResponseCallback.completeWithErrorResponse(new RpcException((byte) 1, ""));
    latch.await();
  }

  @Test
  public void failedUnexpectedErrorCallbackAlwaysRun() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    rpcResponseCallback.alwaysRun(latch::countDown);
    assertThat(latch.getCount()).isEqualTo(1);
    rpcResponseCallback.completeWithUnexpectedError(new RuntimeException());
    latch.await();
  }
}
