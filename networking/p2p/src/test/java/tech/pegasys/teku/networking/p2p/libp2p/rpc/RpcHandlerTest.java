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

package tech.pegasys.teku.networking.p2p.libp2p.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.libp2p.core.Connection;
import io.libp2p.core.Stream;
import io.libp2p.core.StreamPromise;
import io.libp2p.core.mux.StreamMuxer.Session;
import java.util.concurrent.CompletableFuture;
import kotlin.Unit;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler.Controller;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.networking.p2p.rpc.StreamTimeoutException;

public class RpcHandlerTest {

  StubAsyncRunner asyncRunner = new StubAsyncRunner();
  RpcMethod rpcMethod = mock(RpcMethod.class);
  RpcHandler rpcHandler = new RpcHandler(asyncRunner, rpcMethod);

  Connection connection = mock(Connection.class);
  Session session = mock(Session.class);
  StreamPromise<Object> streamPromise =
      new StreamPromise<>(new CompletableFuture<>(), new CompletableFuture<>());
  CompletableFuture<Unit> closeFuture = new CompletableFuture<>();
  SafeFuture<Void> writeFuture = new SafeFuture<>();

  Stream stream = mock(Stream.class);
  Controller controller = mock(Controller.class);
  RpcStream rpcStream = mock(RpcStream.class);
  RpcRequestHandler rpcRequestHandler = mock(RpcRequestHandler.class);

  @BeforeEach
  void init() {
    when(connection.muxerSession()).thenReturn(session);
    when(session.createStream(any())).thenReturn(streamPromise);
    when(connection.closeFuture()).thenReturn(closeFuture);

    when(controller.getRpcStream()).thenReturn(rpcStream);
    when(rpcStream.writeBytes(any())).thenReturn(writeFuture);
  }

  @Test
  void sendRequest_positiveCase() {
    SafeFuture<RpcStream> future =
        rpcHandler.sendRequest(connection, Bytes.fromHexString("0x11223344"), rpcRequestHandler);

    assertThat(future).isNotDone();
    streamPromise.getStream().complete(stream);

    assertThat(future).isNotDone();
    streamPromise.getController().complete(controller);

    assertThat(future).isNotDone();
    writeFuture.complete(null);

    verify(stream, never()).close();
    verify(controller, never()).closeAbruptly();
    assertThat(future).isCompletedWithValue(rpcStream);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);

    // interrupting after completion shouldn't affect anything
    closeFuture.complete(null);

    verify(stream, never()).close();
    verify(controller, never()).closeAbruptly();
    assertThat(future).isCompletedWithValue(rpcStream);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
  }

  @Test
  void sendRequest_streamClosedWhenConnectionClosedBeforeController() {
    SafeFuture<RpcStream> future =
        rpcHandler.sendRequest(connection, Bytes.fromHexString("0x11223344"), rpcRequestHandler);

    streamPromise.getStream().complete(stream);
    closeFuture.complete(null);

    verify(connection).muxerSession();
    verify(controller, never()).getRpcStream();
    verify(stream).close();

    assertThatThrownBy(future::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
  }

  @Test
  void sendRequest_streamClosedWhenConnectionClosedBeforeWrite() {
    SafeFuture<RpcStream> future =
        rpcHandler.sendRequest(connection, Bytes.fromHexString("0x11223344"), rpcRequestHandler);

    streamPromise.getStream().complete(stream);
    streamPromise.getController().complete(controller);
    closeFuture.complete(null);

    verify(connection).muxerSession();
    verify(controller).getRpcStream();
    verify(rpcStream).writeBytes(any());
    verify(stream).close();

    assertThatThrownBy(future::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
  }

  @Test
  void sendRequest_noStreamCreationWhenInitiallyClosed() {
    closeFuture.complete(null);

    SafeFuture<RpcStream> future =
        rpcHandler.sendRequest(connection, Bytes.fromHexString("0x11223344"), rpcRequestHandler);

    verify(connection, never()).muxerSession();
    assertThatThrownBy(future::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
  }

  @Test
  void sendRequest_streamClosedOnTimeoutBeforeController() {
    SafeFuture<RpcStream> future =
        rpcHandler.sendRequest(connection, Bytes.fromHexString("0x11223344"), rpcRequestHandler);

    streamPromise.getStream().complete(stream);
    asyncRunner.executeQueuedActions();

    verify(connection).muxerSession();
    verify(controller, never()).getRpcStream();

    assertThatThrownBy(future::get).hasRootCauseInstanceOf(StreamTimeoutException.class);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
    verify(stream).close();
  }
}
