/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.ethereum.executionclient.web3j;

import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.websocket.WebSocketClient;
import org.web3j.protocol.websocket.WebSocketService;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtAuthWebsocketHelper;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtConfig;
import tech.pegasys.teku.ethereum.executionclient.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

class Web3jWebsocketClient extends Web3JClient {
  private final AtomicBoolean connected = new AtomicBoolean(false);
  private final WebSocketClient webSocketClient;
  private Optional<JwtAuthWebsocketHelper> jwtAuth = Optional.empty();

  Web3jWebsocketClient(
      final EventLogger eventLog,
      final URI endpoint,
      final TimeProvider timeProvider,
      final Optional<JwtConfig> jwtConfig,
      final ExecutionClientEventsChannel executionClientEventsPublisher) {
    super(eventLog, timeProvider, executionClientEventsPublisher);
    this.webSocketClient = new WebSocketClient(endpoint);
    final WebSocketService webSocketService = new WebSocketService(webSocketClient, false);
    initWeb3jService(webSocketService);
    setupJwtAuth(jwtConfig, timeProvider);
  }

  private void setupJwtAuth(final Optional<JwtConfig> jwtConfig, final TimeProvider timeProvider) {
    if (jwtConfig.isPresent()) {
      JwtAuthWebsocketHelper jwtAuthWebsocketHelper =
          new JwtAuthWebsocketHelper(jwtConfig.get(), timeProvider);
      this.jwtAuth = Optional.of(jwtAuthWebsocketHelper);
    }
  }

  private Optional<Exception> tryToConnect() {
    if (connected.get()) {
      return Optional.empty();
    }
    try {
      jwtAuth.ifPresent(jwtHelper -> jwtHelper.setAuth(webSocketClient));
      ((WebSocketService) getWeb3jService())
          .connect(message -> {}, this::handleError, () -> connected.set(false));
      connected.set(true);
      return Optional.empty();
    } catch (ConnectException ex) {
      connected.set(false);
      handleError(ex, true);
      return Optional.of(ex);
    }
  }

  @Override
  public <T> SafeFuture<Response<T>> doRequest(
      Request<?, ? extends org.web3j.protocol.core.Response<T>> web3jRequest,
      final Duration timeout) {
    return tryToConnect()
        .<SafeFuture<Response<T>>>map(SafeFuture::failedFuture)
        .orElseGet(() -> super.doRequest(web3jRequest, timeout));
  }

  @Override
  public boolean isWebsocketsClient() {
    return true;
  }
}
