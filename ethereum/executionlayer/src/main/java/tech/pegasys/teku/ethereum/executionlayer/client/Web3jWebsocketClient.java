/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionlayer.client;

import java.net.ConnectException;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.websocket.WebSocketClient;
import org.web3j.protocol.websocket.WebSocketService;
import tech.pegasys.teku.ethereum.executionlayer.client.auth.JwtAuthWebsocketHelper;
import tech.pegasys.teku.ethereum.executionlayer.client.auth.JwtConfig;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class Web3jWebsocketClient extends Web3JClient {
  private final AtomicBoolean connected = new AtomicBoolean(false);
  private final URI endpoint;
  private WebSocketClient webSocketClient;
  private Optional<JwtAuthWebsocketHelper> jwtAuth = Optional.empty();

  public Web3jWebsocketClient(
      final URI endpoint, final TimeProvider timeProvider, final Optional<JwtConfig> jwtConfig) {
    super(timeProvider);
    this.endpoint = endpoint;
    initService();
    setupJwtAuth(jwtConfig, timeProvider);
  }

  private synchronized void initService() {
    this.webSocketClient = new WebSocketClient(endpoint);
    WebSocketService webSocketService = new WebSocketService(webSocketClient, false);
    initWeb3jService(webSocketService);
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
          .connect(
              message -> {},
              this::handleError,
              () -> {
                connected.set(false);
                initService();
              });
      connected.set(true);
      return Optional.empty();
    } catch (ConnectException ex) {
      connected.set(false);
      initService();
      handleError(ex);
      return Optional.of(ex);
    }
  }

  @Override
  protected <T> SafeFuture<T> doWeb3JRequest(CompletableFuture<T> web3Request) {
    return tryToConnect()
        .<SafeFuture<T>>map(SafeFuture::failedFuture)
        .orElseGet(() -> super.doWeb3JRequest(web3Request));
  }

  @Override
  protected <T> SafeFuture<Response<T>> doRequest(
      Request<?, ? extends org.web3j.protocol.core.Response<T>> web3jRequest) {
    return tryToConnect()
        .<SafeFuture<Response<T>>>map(
            exception -> SafeFuture.completedFuture(new Response<>(exception.getMessage())))
        .orElseGet(() -> super.doRequest(web3jRequest));
  }
}
