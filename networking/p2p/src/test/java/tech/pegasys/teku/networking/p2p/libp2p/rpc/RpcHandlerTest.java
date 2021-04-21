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
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionClosedException;
import io.libp2p.core.Stream;
import io.libp2p.core.StreamPromise;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.mux.StreamMuxer.Session;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler.Controller;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.networking.p2p.rpc.RpcStreamController;
import tech.pegasys.teku.networking.p2p.rpc.StreamTimeoutException;

@SuppressWarnings("unchecked")
public class RpcHandlerTest {

  StubAsyncRunner asyncRunner = new StubAsyncRunner();
  RpcMethod<RpcRequestHandler, Object, RpcResponseHandler<?>> rpcMethod = mock(RpcMethod.class);
  RpcHandler<RpcRequestHandler, Object, RpcResponseHandler<?>> rpcHandler =
      new RpcHandler<>(asyncRunner, rpcMethod);

  Connection connection = mock(Connection.class);
  Session session = mock(Session.class);
  StreamPromise<Controller<RpcRequestHandler>> streamPromise =
      new StreamPromise<>(new CompletableFuture<>(), new CompletableFuture<>());
  CompletableFuture<Unit> closeFuture = new CompletableFuture<>();
  CompletableFuture<String> protocolIdFuture = new CompletableFuture<>();
  SafeFuture<Void> writeFuture = new SafeFuture<>();

  Stream stream = mock(Stream.class);
  Controller<RpcRequestHandler> controller = mock(Controller.class);
  RpcStream rpcStream = mock(RpcStream.class);
  final RpcResponseHandler<?> responseHandler = mock(RpcResponseHandler.class);
  final Object request = new Object();

  @BeforeEach
  void init() {
    when(connection.muxerSession()).thenReturn(session);
    when(session.createStream((ProtocolBinding<Controller<RpcRequestHandler>>) any()))
        .thenReturn(streamPromise);
    when(connection.closeFuture()).thenReturn(closeFuture);

    when(controller.getRpcStream()).thenReturn(rpcStream);
    when(rpcStream.writeBytes(any())).thenReturn(writeFuture);
    when(stream.getProtocol()).thenReturn(protocolIdFuture);
  }

  @Test
  void sendRequest_positiveCase() {
    final SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    assertThat(future).isNotDone();
    streamPromise.getStream().complete(stream);

    assertThat(future).isNotDone();
    streamPromise.getController().complete(controller);

    assertThat(future).isNotDone();
    stream.getProtocol().complete("test");

    assertThat(future).isNotDone();
    writeFuture.complete(null);

    verify(stream, never()).close();
    verify(controller, never()).closeAbruptly();
    assertThat(future).isCompletedWithValue(controller);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);

    // interrupting after completion shouldn't affect anything
    closeFuture.complete(null);

    verify(stream, never()).close();
    verify(controller, never()).closeAbruptly();
    assertThat(future).isCompletedWithValue(controller);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
  }

  @Test
  void sendRequest_streamClosedRightBeforeCreateStreamShouldThrowPeerDisconnectedException() {

    when(session.createStream((ProtocolBinding<Controller<RpcRequestHandler>>) any()))
        .thenThrow(new ConnectionClosedException());
    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    assertThatSafeFuture(future).isCompletedExceptionallyWith(PeerDisconnectedException.class);
  }

  @Test
  void sendRequest_noStreamCreationWhenInitiallyClosed() {
    closeFuture.complete(null);

    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    verify(connection, never()).muxerSession();
    assertThatThrownBy(future::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
  }

  @ParameterizedTest(name = "closeStream={0}, exceedTimeout={1}")
  @MethodSource("getInterruptParams")
  void sendRequest_interruptBeforeStreamResolves(
      final boolean closeStream, final boolean exceedTimeout) {
    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    assertThat(future).isNotDone();

    final Class<? extends Exception> expectedException =
        executeInterrupts(closeStream, exceedTimeout);
    assertThat(future).isCompletedExceptionally();

    verify(connection).muxerSession();
    verify(controller, never()).getRpcStream();

    assertThatThrownBy(future::get).hasRootCauseInstanceOf(expectedException);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
  }

  @ParameterizedTest(name = "closeStream={0}, exceedTimeout={1}")
  @MethodSource("getInterruptParams")
  void sendRequest_interruptBeforeControllerResolves(
      final boolean closeStream, final boolean exceedTimeout) {
    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    streamPromise.getStream().complete(stream);
    assertThat(future).isNotDone();

    final Class<? extends Exception> expectedException =
        executeInterrupts(closeStream, exceedTimeout);
    assertThat(future).isCompletedExceptionally();

    verify(connection).muxerSession();
    verify(controller, never()).getRpcStream();

    assertThatThrownBy(future::get).hasRootCauseInstanceOf(expectedException);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
    verify(stream).close();
  }

  @ParameterizedTest(name = "closeStream={0}, exceedTimeout={1}")
  @MethodSource("getInterruptParams")
  void sendRequest_interruptBeforeProtocolIdResolves(
      final boolean closeStream, final boolean exceedTimeout) {
    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    streamPromise.getStream().complete(stream);
    streamPromise.getController().complete(controller);
    assertThat(future).isNotDone();

    final Class<? extends Exception> expectedException =
        executeInterrupts(closeStream, exceedTimeout);
    assertThat(future).isCompletedExceptionally();

    verify(connection).muxerSession();
    verify(controller, never()).getRpcStream();

    assertThatThrownBy(future::get).hasRootCauseInstanceOf(expectedException);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
    verify(stream).close();
  }

  @ParameterizedTest(name = "closeStream={0}, exceedTimeout={1}")
  @MethodSource("getInterruptParams")
  void sendRequest_interruptBeforeControllerResolvesButAfterProtocolIdResolves(
      final boolean closeStream, final boolean exceedTimeout) {
    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    streamPromise.getStream().complete(stream);
    stream.getProtocol().complete("test");
    assertThat(future).isNotDone();

    final Class<? extends Exception> expectedException =
        executeInterrupts(closeStream, exceedTimeout);
    assertThat(future).isCompletedExceptionally();

    verify(connection).muxerSession();
    verify(controller, never()).getRpcStream();

    assertThatThrownBy(future::get).hasRootCauseInstanceOf(expectedException);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
    verify(stream).close();
  }

  @ParameterizedTest(name = "closeStream={0}, exceedTimeout={1}")
  @MethodSource("getInterruptParams")
  void sendRequest_interruptBeforeInitialPayloadWritten(
      final boolean closeStream, final boolean exceedTimeout) {
    SafeFuture<RpcStreamController<RpcRequestHandler>> future =
        rpcHandler.sendRequest(connection, request, responseHandler);

    streamPromise.getStream().complete(stream);
    streamPromise.getController().complete(controller);
    stream.getProtocol().complete("test");
    assertThat(future).isNotDone();

    final Class<? extends Exception> expectedException =
        executeInterrupts(closeStream, exceedTimeout);
    assertThat(future).isCompletedExceptionally();

    verify(connection).muxerSession();
    verify(controller).getRpcStream();

    assertThatThrownBy(future::get).hasRootCauseInstanceOf(expectedException);
    assertThat(closeFuture.getNumberOfDependents()).isEqualTo(0);
    verify(stream).close();
  }

  private Class<? extends Exception> executeInterrupts(
      final boolean closeStream, final boolean exceedTimeout) {
    final AtomicReference<Class<? extends Exception>> expectedException =
        new AtomicReference<>(null);
    if (closeStream) {
      closeFuture.complete(null);
      expectedException.set(PeerDisconnectedException.class);
    }
    if (exceedTimeout) {
      asyncRunner.executeQueuedActions();
      expectedException.compareAndSet(null, StreamTimeoutException.class);
    }

    return expectedException.get();
  }

  public static java.util.stream.Stream<Arguments> getInterruptParams() {
    return java.util.stream.Stream.of(
        Arguments.of(true, false), Arguments.of(false, true), Arguments.of(true, true));
  }
}
