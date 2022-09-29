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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.VoidResponse;
import org.web3j.protocol.websocket.WebSocketService;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class Web3JClientTest {
  private static final URI ENDPOINT = URI.create("");
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);

  private final EventLogger eventLog = mock(EventLogger.class);

  @SuppressWarnings("unused")
  static Stream<Arguments> getClientInstances() {
    final TimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);
    final Web3jService web3jService = mock(Web3jService.class);
    final WebSocketService webSocketService = mock(WebSocketService.class);
    return Stream.<Named<ClientFactory>>of(
            Named.of(
                "Web3JClient",
                eventLog -> {
                  final Web3JClient web3jClient = new Web3JClient(eventLog, timeProvider) {};
                  web3jClient.initWeb3jService(web3jService);
                  return web3jClient;
                }),
            Named.of(
                "Web3jHttpClient",
                eventLog -> {
                  final Web3jHttpClient client =
                      new Web3jHttpClient(
                          eventLog, ENDPOINT, timeProvider, DEFAULT_TIMEOUT, Optional.empty()) {};
                  client.initWeb3jService(web3jService);
                  return client;
                }),
            Named.of(
                "Web3jWebsocketClient",
                eventLog -> {
                  final Web3jWebsocketClient client =
                      new Web3jWebsocketClient(eventLog, ENDPOINT, timeProvider, Optional.empty());
                  client.initWeb3jService(webSocketService);
                  return client;
                }),
            Named.of(
                "Web3jIpcClient",
                eventLog -> {
                  final Web3jIpcClient client =
                      new Web3jIpcClient(
                          eventLog, URI.create("file:/a"), timeProvider, Optional.empty());
                  client.initWeb3jService(web3jService);
                  return client;
                }))
        .map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("getClientInstances")
  void shouldTimeoutIfResponseNotReceived(final ClientFactory clientFactory) throws Exception {
    final Web3JClient client = clientFactory.create(eventLog);
    Request<Void, VoidResponse> request =
        new Request<>("test", new ArrayList<>(), client.getWeb3jService(), VoidResponse.class);
    when(client.getWeb3jService().sendAsync(request, VoidResponse.class))
        .thenReturn(new CompletableFuture<>());

    final Duration crazyShortTimeout = Duration.ofMillis(0);
    final SafeFuture<Response<Void>> result = client.doRequest(request, crazyShortTimeout);
    Waiter.waitFor(result);
    SafeFutureAssert.assertThatSafeFuture(result).isCompleted();
    final Response<Void> response = SafeFutureAssert.safeJoin(result);
    assertThat(response.getErrorMessage()).isEqualTo(TimeoutException.class.getSimpleName());
    verify(eventLog).executionClientRequestTimedOut();
  }

  @ParameterizedTest
  @MethodSource("getClientInstances")
  void shouldLogOnFirstSuccess(final ClientFactory clientFactory) throws Exception {
    final Web3JClient client = clientFactory.create(eventLog);
    Request<Void, VoidResponse> request =
        new Request<>("test", new ArrayList<>(), client.getWeb3jService(), VoidResponse.class);
    when(client.getWeb3jService().sendAsync(request, VoidResponse.class))
        .thenReturn(SafeFuture.completedFuture(new VoidResponse()));

    final SafeFuture<Response<Void>> result = client.doRequest(request, DEFAULT_TIMEOUT);
    Waiter.waitFor(result);

    verify(eventLog).executionClientIsOnline();
  }

  @ParameterizedTest
  @MethodSource("getClientInstances")
  void shouldReportFailureAndRecovery(final ClientFactory clientFactory) throws Exception {
    final Web3JClient client = clientFactory.create(eventLog);
    Request<Void, VoidResponse> request =
        new Request<>("test", new ArrayList<>(), client.getWeb3jService(), VoidResponse.class);
    final Throwable error = new IllegalStateException("oopsy");
    when(client.getWeb3jService().sendAsync(request, VoidResponse.class))
        .thenReturn(SafeFuture.failedFuture(error))
        .thenReturn(SafeFuture.completedFuture(new VoidResponse()));

    Waiter.waitFor(client.doRequest(request, DEFAULT_TIMEOUT));

    verify(eventLog).executionClientRequestFailed(error, false);

    Waiter.waitFor(client.doRequest(request, DEFAULT_TIMEOUT));
    verify(eventLog).executionClientRecovered();
  }

  private interface ClientFactory {
    Web3JClient create(EventLogger eventLog);
  }
}
