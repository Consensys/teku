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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.ethereum.executionlayer.client.auth.JwtTestHelper.generateJwtSecret;

import com.google.common.net.HttpHeaders;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.BooleanResponse;
import org.web3j.protocol.core.methods.response.VoidResponse;
import org.web3j.protocol.websocket.WebSocketClient;
import org.web3j.protocol.websocket.WebSocketService;
import tech.pegasys.teku.ethereum.executionlayer.client.auth.JwtConfig;
import tech.pegasys.teku.ethereum.executionlayer.client.auth.Token;
import tech.pegasys.teku.ethereum.executionlayer.client.auth.TokenProvider;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.Response;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Web3jWebsocketClientTest {
  private final TimeProvider timeProvider = mock(TimeProvider.class);
  private final WebSocketClient webSocketClient = mock(WebSocketClient.class);
  private final WebSocketService webSocketService = mock(WebSocketService.class);

  @Test
  public void shouldConnectBeforeRequest() throws Exception {
    final Web3jWebsocketClient web3jWebsocketClient =
        new Web3jWebsocketClient(timeProvider, webSocketClient, webSocketService, Optional.empty());
    Request<Void, VoidResponse> request =
        new Request<>("test", new ArrayList<>(), webSocketService, VoidResponse.class);
    when(webSocketService.sendAsync(request, VoidResponse.class))
        .thenReturn(CompletableFuture.completedFuture(new VoidResponse()));
    web3jWebsocketClient.doRequest(request).finish(ex -> {});
    verify(webSocketService, times(1)).connect(any(), any(), any());
  }

  @Test
  public void shouldEnableJwtAuthIfProvided() throws Exception {
    JwtConfig jwtConfig = new JwtConfig(generateJwtSecret());
    final Web3jWebsocketClient web3jWebsocketClient =
        new Web3jWebsocketClient(
            timeProvider, webSocketClient, webSocketService, Optional.of(jwtConfig));
    Request<Void, BooleanResponse> request =
        new Request<>("test", new ArrayList<>(), webSocketService, BooleanResponse.class);
    BooleanResponse result = new BooleanResponse();
    result.setResult(true);
    when(webSocketService.sendAsync(request, BooleanResponse.class))
        .thenReturn(CompletableFuture.completedFuture(result));
    when(timeProvider.getTimeInMillis()).thenReturn(UInt64.ONE);
    TokenProvider tokenProvider = new TokenProvider(jwtConfig);
    Token expectedToken = tokenProvider.token(UInt64.ONE).get();
    assertThat(web3jWebsocketClient.doRequest(request))
        .isCompletedWithValueMatching(value -> value.equals(new Response<>(true)));
    verify(webSocketClient)
        .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + expectedToken.getJwtToken());
    verify(webSocketService, times(1)).connect(any(), any(), any());
  }
}
