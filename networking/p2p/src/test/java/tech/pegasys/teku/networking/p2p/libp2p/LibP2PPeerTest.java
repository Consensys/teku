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

package tech.pegasys.teku.networking.p2p.libp2p;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.libp2p.core.Connection;
import io.libp2p.core.security.SecureChannel.Session;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStreamController;
import tech.pegasys.teku.spec.constants.NetworkConstants;

public class LibP2PPeerTest {

  private final Connection connection = mock(Connection.class);

  @SuppressWarnings("unchecked")
  private final RpcHandler<RpcRequestHandler, Object, RpcResponseHandler<Void>> rpcHandler =
      mock(RpcHandler.class);

  @SuppressWarnings("unchecked")
  private final RpcMethod<RpcRequestHandler, Object, RpcResponseHandler<Void>> rpcMethod =
      mock(RpcMethod.class);

  private LibP2PPeer libP2PPeer;

  @BeforeEach
  public void init() {
    when(rpcHandler.getRpcMethod()).thenReturn(rpcMethod);
    final Session secureSession = mock(Session.class);
    when(connection.secureSession()).thenReturn(secureSession);
    when(connection.closeFuture()).thenReturn(new SafeFuture<>());
    libP2PPeer =
        new LibP2PPeer(connection, List.of(rpcHandler), ReputationManager.NOOP, peer -> 0.0);
  }

  @SuppressWarnings({"unchecked", "FutureReturnValueIgnored"})
  @Test
  public void sendRequest_throttlesRequests() {

    // fill the queue with incomplete futures
    final List<SafeFuture<RpcStreamController<RpcRequestHandler>>> queuedFutures =
        IntStream.range(0, NetworkConstants.MAX_CONCURRENT_REQUESTS)
            .mapToObj(
                __ -> {
                  final SafeFuture<RpcStreamController<RpcRequestHandler>> future =
                      new SafeFuture<>();
                  when(rpcHandler.sendRequest(connection, null, null)).thenReturn(future);
                  libP2PPeer.sendRequest(rpcMethod, null, null);
                  return future;
                })
            .toList();

    when(rpcHandler.sendRequest(connection, null, null))
        .thenReturn(SafeFuture.completedFuture(mock(RpcStreamController.class)));

    final SafeFuture<RpcStreamController<RpcRequestHandler>> throttledRequest =
        libP2PPeer.sendRequest(rpcMethod, null, null);

    // completed request should be throttled
    assertThat(throttledRequest).isNotDone();

    // empty the queue
    queuedFutures.forEach(future -> future.complete(mock(RpcStreamController.class)));

    // throttled request should have completed now
    assertThat(throttledRequest).isDone();
  }
}
