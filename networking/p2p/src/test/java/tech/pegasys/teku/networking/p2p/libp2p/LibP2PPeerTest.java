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

package tech.pegasys.teku.networking.p2p.libp2p;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.libp2p.core.Connection;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.security.SecureChannel.Session;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import kotlin.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStreamController;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.RpcRequestBodySelector;

public class LibP2PPeerTest {
  public static final String PEER_ID = "16Uiu2HAmFxCpRh2nZevFR3KGXJ3jhpixMYFSuawqKZyZYHrYoiK5";
  private final Connection connection = mock(Connection.class);

  @SuppressWarnings("unchecked")
  private final RpcHandler<RpcRequestHandler, RpcRequest, RpcResponseHandler<Void>> rpcHandler =
      mock(RpcHandler.class);

  @SuppressWarnings("unchecked")
  private final RpcMethod<RpcRequestHandler, RpcRequest, RpcResponseHandler<Void>> rpcMethod =
      mock(RpcMethod.class);

  private LibP2PPeer libP2PPeer;

  private final SafeFuture<Unit> closeFuture = new SafeFuture<>();

  @BeforeEach
  public void init() {
    when(rpcHandler.getRpcMethod()).thenReturn(rpcMethod);
    final Session secureSession = mock(Session.class);
    when(connection.secureSession()).thenReturn(secureSession);
    when(connection.closeFuture()).thenReturn(closeFuture);
    when(connection.remoteAddress())
        .thenReturn(Multiaddr.fromString("/ip4/123.34.58.22/tcp/5883/"));
    when(secureSession.getRemoteId()).thenReturn(PeerId.fromBase58(PEER_ID));
    libP2PPeer =
        new LibP2PPeer(connection, List.of(rpcHandler), ReputationManager.NOOP, peer -> 0.0);
  }

  @SuppressWarnings({"unchecked", "FutureReturnValueIgnored", "rawtypes"})
  @Test
  public void sendRequest_throttlesRequests() {

    // fill the queue with incomplete futures
    final List<SafeFuture<RpcStreamController<RpcRequestHandler>>> queuedFutures =
        IntStream.range(0, NetworkConstants.MAX_CONCURRENT_REQUESTS)
            .mapToObj(
                __ -> {
                  final SafeFuture<RpcStreamController<RpcRequestHandler>> future =
                      new SafeFuture<>();
                  when(rpcHandler.sendRequestWithBodySelector(eq(connection), any(), any()))
                      .thenReturn(future);
                  libP2PPeer.sendRequest(rpcMethod, (RpcRequestBodySelector) null, null);
                  return future;
                })
            .toList();

    when(rpcHandler.sendRequest(connection, null, null))
        .thenReturn(SafeFuture.completedFuture(mock(RpcStreamController.class)));

    final SafeFuture<RpcStreamController<RpcRequestHandler>> throttledRequest =
        libP2PPeer.sendRequest(rpcMethod, (RpcRequestBodySelector) null, null);

    // completed request should be throttled
    assertThat(throttledRequest).isNotDone();

    // empty the queue
    queuedFutures.forEach(future -> future.complete(mock(RpcStreamController.class)));

    // throttled request should have completed now
    assertThat(throttledRequest).isDone();
  }

  @Test
  public void disconnectCleanly_shouldCloseConnectionOnlyOnce() {
    final AtomicReference<Optional<DisconnectReason>> disconnectionReason = new AtomicReference<>();
    final AtomicBoolean disconnectionLocallyInitiated = new AtomicBoolean();
    final AtomicInteger disconnectionCount = new AtomicInteger(0);

    libP2PPeer.subscribeDisconnect(
        (reason, locallyInitiated) -> {
          disconnectionReason.set(reason);
          disconnectionLocallyInitiated.set(locallyInitiated);
          disconnectionCount.addAndGet(1);
        });

    libP2PPeer.disconnectCleanly(DisconnectReason.IRRELEVANT_NETWORK).finish(__ -> {});
    verify(connection).close();

    libP2PPeer.disconnectCleanly(DisconnectReason.IRRELEVANT_NETWORK).finish(__ -> {});
    libP2PPeer.disconnectImmediately(Optional.of(DisconnectReason.REMOTE_FAULT), false);

    verify(connection, times(1)).close();

    assertThat(disconnectionCount.get()).isEqualTo(0);

    closeFuture.complete(null);

    assertThat(disconnectionReason.get()).contains(DisconnectReason.IRRELEVANT_NETWORK);
    assertThat(disconnectionLocallyInitiated.get()).isTrue();
    assertThat(disconnectionCount.get()).isEqualTo(1);
  }
}
