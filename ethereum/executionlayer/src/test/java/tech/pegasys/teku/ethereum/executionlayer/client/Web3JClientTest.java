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
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
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
import tech.pegasys.teku.ethereum.executionlayer.client.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class Web3JClientTest {
  private static final TimeProvider TIME_PROVIDER = StubTimeProvider.withTimeInSeconds(1000);
  private static final Web3jService WEB3J_SERVICE = mock(Web3jService.class);
  private static final Web3JClient WEB3J_CLIENT = new Web3JClientImpl(TIME_PROVIDER);
  private static final URI ENDPOINT = URI.create("");
  private static final Web3jHttpClient WEB3J_HTTP_CLIENT =
      new Web3jHttpClient(ENDPOINT, TIME_PROVIDER, Optional.empty());
  private static final WebSocketService WEB_SOCKET_SERVICE = mock(WebSocketService.class);
  private static final Web3jWebsocketClient WEB3J_WEBSOCKET_CLIENT =
      new Web3jWebsocketClient(ENDPOINT, TIME_PROVIDER, Optional.empty());
  private static final Web3jIpcClient WEB3J_IPC_CLIENT =
      new Web3jIpcClient(URI.create("file:/a"), TIME_PROVIDER, Optional.empty());
  private static final Duration TIMEOUT = Duration.ofSeconds(10000000);

  static class Web3JClientImpl extends Web3JClient {
    protected Web3JClientImpl(TimeProvider timeProvider) {
      super(timeProvider);
      initWeb3jService(WEB3J_SERVICE);
    }
  }

  @BeforeAll
  static void setup() {
    WEB3J_HTTP_CLIENT.initWeb3jService(WEB3J_SERVICE);
    WEB3J_WEBSOCKET_CLIENT.initWeb3jService(WEB_SOCKET_SERVICE);
  }

  @Test
  public void shouldModifyRequestWithApplyModifiers() {
    WEB3J_CLIENT.addRequestAdapter(
        request -> {
          request.setId(123);
          return request;
        });
    Request<Void, VoidResponse> request =
        new Request<>("test", new ArrayList<>(), WEB3J_SERVICE, VoidResponse.class);
    request.setId(1);
    Request<?, ?> actualRequest = WEB3J_CLIENT.applyRequestAdapters(request);
    assertThat(actualRequest.getId()).isEqualTo(123);
  }

  @SuppressWarnings("unused")
  static Stream<Arguments> getClientInstances() {
    return Stream.of(WEB3J_CLIENT, WEB3J_HTTP_CLIENT, WEB3J_WEBSOCKET_CLIENT, WEB3J_IPC_CLIENT)
        .map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("getClientInstances")
  public void shouldModifyRequestWhenDoRequestCalled(final Web3JClient client) {
    client.addRequestAdapter(
        request -> {
          request.setId(123);
          return request;
        });
    Request<Void, VoidResponse> request =
        new Request<>("test", new ArrayList<>(), WEB3J_SERVICE, VoidResponse.class);
    request.setId(1);
    when(WEB3J_SERVICE.sendAsync(request, VoidResponse.class))
        .thenReturn(CompletableFuture.completedFuture(new VoidResponse()));
    assertThat(client.doRequest(request, TIMEOUT)).isCompleted();
    verify(WEB3J_SERVICE, times(1)).sendAsync(request, VoidResponse.class);
    assertThat(request.getId()).isEqualTo(123);
  }

  @ParameterizedTest
  @MethodSource("getClientInstances")
  void shouldTimeoutIfResponseNotReceived(final Web3JClient client) throws Exception {
    Request<Void, VoidResponse> request =
        new Request<>("test", new ArrayList<>(), WEB3J_SERVICE, VoidResponse.class);
    when(WEB3J_SERVICE.sendAsync(request, VoidResponse.class))
        .thenReturn(new CompletableFuture<>());

    final Duration crazyShortTimeout = Duration.ofMillis(0);
    final SafeFuture<Response<Void>> result = client.doRequest(request, crazyShortTimeout);
    waitFor(result);
    assertThatSafeFuture(result).isCompleted();
    final Response<Void> response = safeJoin(result);
    assertThat(response.getErrorMessage()).isEqualTo(TimeoutException.class.getSimpleName());
  }
}
