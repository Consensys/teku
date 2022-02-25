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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.VoidResponse;
import org.web3j.protocol.websocket.WebSocketService;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class Web3JClientTest {
  private static final TimeProvider timeProvider = mock(TimeProvider.class);
  private static final Web3jService web3jService = mock(Web3jService.class);
  private static final Web3JClient web3JClient = new Web3JClientImpl(timeProvider);
  private static final URI endpoint = URI.create("");
  private static final Web3jHttpClient web3jHttpClient =
      new Web3jHttpClient(endpoint, timeProvider, Optional.empty());
  private static final WebSocketService webSocketService = mock(WebSocketService.class);
  private static final Web3jWebsocketClient web3jWebsocketClient =
      new Web3jWebsocketClient(endpoint, timeProvider, Optional.empty());

  static class Web3JClientImpl extends Web3JClient {
    protected Web3JClientImpl(TimeProvider timeProvider) {
      super(timeProvider);
      initWeb3jService(web3jService);
    }
  }

  @BeforeAll
  static void setup() {
    web3jHttpClient.initWeb3jService(web3jService);
    web3jWebsocketClient.initWeb3jService(webSocketService);
  }

  @Test
  public void shouldModifyRequestWithApplyModifiers() {
    web3JClient.addRequestAdapter(
        request -> {
          request.setId(123);
          return request;
        });
    Request<Void, VoidResponse> request =
        new Request<>("test", new ArrayList<>(), web3jService, VoidResponse.class);
    request.setId(1);
    Request<?, ?> actualRequest = web3JClient.applyRequestAdapters(request);
    assertThat(actualRequest.getId()).isEqualTo(123);
  }

  @SuppressWarnings("unused")
  static Stream<Arguments> getClientInstances() {
    return Stream.of(web3JClient, web3jHttpClient, web3jWebsocketClient).map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("getClientInstances")
  public void shouldModifyRequestWhenDoRequestCalled(Web3JClient client) {
    client.addRequestAdapter(
        request -> {
          request.setId(123);
          return request;
        });
    Request<Void, VoidResponse> request =
        new Request<>("test", new ArrayList<>(), web3jService, VoidResponse.class);
    request.setId(1);
    when(web3jService.sendAsync(request, VoidResponse.class))
        .thenReturn(CompletableFuture.completedFuture(new VoidResponse()));
    client.doRequest(request).finish(ex -> {});
    verify(web3jService, times(1)).sendAsync(request, VoidResponse.class);
    assertThat(request.getId()).isEqualTo(123);
  }
}
