/*
 * Copyright Consensys Software Inc., 2026
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

import io.netty.channel.socket.ChannelOutputShutdownException;
import java.nio.channels.ClosedChannelException;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;

class RpcResponseCallbackTest {

  private final RpcStream rpcStream = mock(RpcStream.class);

  @SuppressWarnings("unchecked")
  private final RpcResponseEncoder<SszData, ?> responseEncoder = mock(RpcResponseEncoder.class);

  private final SszData data = mock(SszData.class);

  private final RpcResponseCallback<SszData> callback =
      new RpcResponseCallback<>(rpcStream, responseEncoder);

  @Test
  void shouldNotLogErrorWhenWriteFailsWithStopSending() {
    failWriteWith(new ChannelOutputShutdownException("STOP_SENDING frame received"));

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class, Level.TRACE)) {
      callback.respondAndCompleteSuccessfully(data);

      assertThat(logCaptor.getErrorLogs()).isEmpty();
      assertThat(logCaptor.getTraceLogs())
          .anySatisfy(log -> assertThat(log).contains("STOP_SENDING"));
    }
  }

  @Test
  void shouldNotLogErrorWhenWriteFailsWithClosedChannel() {
    failWriteWith(new ClosedChannelException());

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class, Level.TRACE)) {
      callback.respondAndCompleteSuccessfully(data);

      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void shouldLogErrorWhenWriteFailsWithUnexpectedError() {
    failWriteWith(new IllegalStateException("Boom"));

    try (final LogCaptor logCaptor = LogCaptor.forClass(RpcResponseCallback.class)) {
      callback.respondAndCompleteSuccessfully(data);

      logCaptor.assertErrorLog("Failed to write req/resp response");
    }
  }

  private void failWriteWith(final Throwable error) {
    when(responseEncoder.encodeSuccessfulResponse(any())).thenReturn(Bytes.EMPTY);
    when(rpcStream.writeBytes(any())).thenReturn(SafeFuture.failedFuture(error));
  }
}
