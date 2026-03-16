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

package tech.pegasys.teku.ethereum.executionclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

class OkHttpWebSocketExecutionEngineClientTest {

  private final MockWebServer mockWebServer = new MockWebServer();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(1000);
  private final EventLogger eventLog = mock(EventLogger.class);
  private final ExecutionClientEventsChannel executionClientEventsPublisher =
      mock(ExecutionClientEventsChannel.class);
  private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

  private OkHttpWebSocketExecutionEngineClient engineClient;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer.start();
    final String wsUrl =
        "ws://" + mockWebServer.getHostName() + ":" + mockWebServer.getPort() + "/";

    engineClient =
        new OkHttpWebSocketExecutionEngineClient(
            httpClient,
            wsUrl,
            eventLog,
            timeProvider,
            executionClientEventsPublisher,
            OkHttpExecutionEngineClient.NON_CRITICAL_METHODS);
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void successfulRequest_returnsNullResult() throws Exception {
    mockWebServer.enqueue(webSocketResponse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}"));

    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response =
        future.get(5, TimeUnit.SECONDS);

    assertThat(response).isNotNull();
    assertThat(response.payload()).isNull();
  }

  private static @NonNull MockResponse webSocketResponse(final String s) {
    return new MockResponse()
        .withWebSocketUpgrade(
            new WebSocketListener() {
              @Override
              public void onMessage(
                  @NotNull final WebSocket webSocket, @NotNull final String text) {
                webSocket.send(s);
              }
            });
  }

  @Test
  void successfulRequest_reportsExecutionClientOnline() throws Exception {
    mockWebServer.enqueue(webSocketResponse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}"));

    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    future.get(5, TimeUnit.SECONDS);

    verify(eventLog).executionClientIsOnline();
    verify(executionClientEventsPublisher).onAvailabilityUpdated(true);
  }

  @Test
  void jsonRpcError_returnsErrorResponse() throws Exception {
    mockWebServer.enqueue(
        webSocketResponse(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}"));

    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response =
        future.get(5, TimeUnit.SECONDS);

    assertThat(response.errorMessage()).contains("Method not found").contains("-32601");
  }

  @Test
  void jsonRpcError_forCriticalMethod_reportsFailure() throws Exception {
    mockWebServer.enqueue(
        webSocketResponse(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}"));

    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    future.get(5, TimeUnit.SECONDS);

    verify(eventLog).executionClientRequestFailed(any(Exception.class), eq(false));
  }

  @Test
  void nonCriticalMethod_doesNotReportErrorOnJsonRpcFailure() throws Exception {
    mockWebServer.enqueue(
        webSocketResponse(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}"));
    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<List<String>>>
        future = engineClient.exchangeCapabilities(List.of("engine_newPayloadV1"));
    future.get(5, TimeUnit.SECONDS);

    verify(eventLog, never()).executionClientRequestFailed(any(), any(Boolean.class));
  }

  @Test
  void multipleRequests_matchResponsesById() throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .withWebSocketUpgrade(
                new WebSocketListener() {
                  @Override
                  public void onMessage(
                      @NotNull final WebSocket webSocket, @NotNull final String text) {
                    // Echo back with the same id, delayed for the first request
                    if (text.contains("\"id\":1")) {
                      // Respond to id 1 after id 2
                    } else {
                      webSocket.send("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":null}");
                      webSocket.send("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}");
                    }
                  }
                }));

    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future1 = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future2 = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x2"));

    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response2 =
        future2.get(5, TimeUnit.SECONDS);
    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response1 =
        future1.get(5, TimeUnit.SECONDS);

    assertThat(response1).isNotNull();
    assertThat(response2).isNotNull();
  }

  @Test
  void webSocketDisconnect_failsPendingRequests() throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .withWebSocketUpgrade(
                new WebSocketListener() {
                  @Override
                  public void onMessage(
                      @NotNull final WebSocket webSocket, @NotNull final String text) {
                    // Don't respond - close the connection instead
                    webSocket.close(1000, "Server closing");
                  }
                }));

    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response =
        future.get(5, TimeUnit.SECONDS);

    assertThat(response.errorMessage()).isNotEmpty();
  }

  @Test
  void reconnectsAfterDisconnect() throws Exception {
    // First connection: will be closed by server
    mockWebServer.enqueue(
        new MockResponse()
            .withWebSocketUpgrade(
                new WebSocketListener() {
                  @Override
                  public void onOpen(
                      @NotNull final WebSocket webSocket, @NotNull final Response response) {
                    webSocket.close(1000, "Server closing");
                  }
                }));

    // Second connection: responds normally
    mockWebServer.enqueue(webSocketResponse("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":null}"));

    // First request triggers connection, which gets closed
    // Give time for the close to propagate
    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future1 = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response1 =
        future1.get(5, TimeUnit.SECONDS);
    assertThat(response1.errorMessage()).isNotEmpty();

    // Second request should reconnect
    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future2 = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x2"));
    final tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1> response2 =
        future2.get(5, TimeUnit.SECONDS);
    assertThat(response2).isNotNull();
    assertThat(response2.payload()).isNull();
  }

  @Test
  void requestBodyContainsCorrectMethod() throws Exception {
    final AtomicReference<String> receivedMessage = new AtomicReference<>();
    mockWebServer.enqueue(
        new MockResponse()
            .withWebSocketUpgrade(
                new WebSocketListener() {
                  @Override
                  public void onMessage(
                      @NotNull final WebSocket webSocket, @NotNull final String text) {
                    receivedMessage.set(text);
                    webSocket.send("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}");
                  }
                }));
    final SafeFuture<tech.pegasys.teku.ethereum.executionclient.schema.Response<ExecutionPayloadV1>>
        future = engineClient.getPayloadV1(Bytes8.fromHexStringLenient("0x1"));
    future.get(5, TimeUnit.SECONDS);

    assertThat(receivedMessage.get()).contains("engine_getPayloadV1");
  }
}
