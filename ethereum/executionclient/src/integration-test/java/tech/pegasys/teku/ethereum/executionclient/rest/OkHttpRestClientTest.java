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

package tech.pegasys.teku.ethereum.executionclient.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.net.SocketException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;

class OkHttpRestClientTest {

  private static final String TEST_PATH = "v1/test";
  private static final Bytes32 TEST_BLOCK_HASH =
      Bytes32.fromHexString("0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2");

  private final MockWebServer mockWebServer = new MockWebServer();
  private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

  private DeserializableTypeDefinition<TestObject> responseTypeDefinition;
  private SerializableTypeDefinition<TestObject2> requestTypeDefinition;
  private SerializableTypeDefinition<TestObject2> failingRequestTypeDefinition;

  private OkHttpRestClient underTest;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer.start();
    final String endpoint = "http://localhost:" + mockWebServer.getPort();
    responseTypeDefinition =
        DeserializableTypeDefinition.object(TestObject.class)
            .initializer(TestObject::new)
            .withField("foo", CoreTypes.STRING_TYPE, TestObject::getFoo, TestObject::setFoo)
            .build();
    requestTypeDefinition =
        SerializableTypeDefinition.object(TestObject2.class)
            .withField("block_hash", CoreTypes.BYTES32_TYPE, TestObject2::getBlockHash)
            .build();
    failingRequestTypeDefinition = new FailingSerializableTypeDefinition();
    underTest = new OkHttpRestClient(okHttpClient, endpoint);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void getsResponseAsync() throws InterruptedException {
    mockWebServer.enqueue(
        new MockResponse()
            .setBody("{\"foo\" : \"bar\"}")
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

    final SafeFuture<Response<TestObject>> responseFuture =
        underTest.getAsync(TEST_PATH, responseTypeDefinition);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.getErrorMessage()).isNull();
              assertThat(response.getPayload())
                  .satisfies(testObject -> assertThat(testObject.getFoo()).isEqualTo("bar"));
            });

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getMethod()).isEqualTo("GET");
  }

  @Test
  void getsResponseAsyncIgnoringResponseBody() throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    final SafeFuture<Response<Void>> responseFuture = underTest.getAsync(TEST_PATH);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.getErrorMessage()).isNull();
              assertThat(response.getPayload()).isNull();
            });

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getMethod()).isEqualTo("GET");
  }

  @Test
  void getAsyncHandlesFailures() throws InterruptedException {
    final String errorBody = "{\"code\":400,\"message\":\"Invalid block: missing signature\"}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(errorBody));

    final SafeFuture<Response<TestObject>> responseFuture =
        underTest.getAsync(TEST_PATH, responseTypeDefinition);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.getErrorMessage()).isEqualTo(errorBody);
              assertThat(response.getPayload()).isNull();
            });

    // error without a body
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    final SafeFuture<Response<TestObject>> secondResponseFuture =
        underTest.getAsync(TEST_PATH, responseTypeDefinition);

    assertThat(secondResponseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.getErrorMessage()).isEqualTo("500: Server Error");
              assertThat(response.getPayload()).isNull();
            });

    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

    final RecordedRequest request = mockWebServer.takeRequest();
    final RecordedRequest secondRequest = mockWebServer.takeRequest();

    Consumer<RecordedRequest> requestsAssertions =
        (req) -> {
          assertThat(req.getPath()).isEqualTo("/" + TEST_PATH);
          assertThat(req.getMethod()).isEqualTo("GET");
        };

    assertThat(List.of(request, secondRequest)).allSatisfy(requestsAssertions);
  }

  @Test
  void postsAsync() throws InterruptedException {
    mockWebServer.enqueue(
        new MockResponse()
            .setBody("{\"foo\" : \"bar\"}")
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

    final TestObject2 requestBodyObject = new TestObject2(TEST_BLOCK_HASH);
    final SafeFuture<Response<TestObject>> responseFuture =
        underTest.postAsync(
            TEST_PATH, requestBodyObject, requestTypeDefinition, responseTypeDefinition);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.getErrorMessage()).isNull();
              assertThat(response.getPayload())
                  .satisfies(testObject -> assertThat(testObject.getFoo()).isEqualTo("bar"));
            });

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getBody().readUtf8())
        .isEqualTo(
            "{\"block_hash\":\"0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2\"}");
    assertThat(request.getMethod()).isEqualTo("POST");
  }

  @Test
  void postsAsyncDoesNotThrowExceptionsInOtherThreadsWhenRequestCreationFails() {

    final TestObject2 requestBodyObject = new TestObject2(TEST_BLOCK_HASH);
    final SafeFuture<Response<TestObject>> responseFuture =
        underTest.postAsync(
            TEST_PATH, requestBodyObject, failingRequestTypeDefinition, responseTypeDefinition);

    // this will fail if there are uncaught exceptions in other threads
    Waiter.waitFor(() -> assertThat(responseFuture).isDone(), 30, TimeUnit.SECONDS, false);

    SafeFutureAssert.assertThatSafeFuture(responseFuture)
        .isCompletedExceptionallyWithMessage("Broken pipe");
  }

  @Test
  void postsAsyncIgnoringResponseBody() throws InterruptedException {
    mockWebServer.enqueue(
        new MockResponse()
            .setBody("{\"foo\" : \"bar\"}")
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

    final TestObject2 requestBodyObject = new TestObject2(TEST_BLOCK_HASH);
    final SafeFuture<Response<Void>> responseFuture =
        underTest.postAsync(TEST_PATH, requestBodyObject, requestTypeDefinition);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(
            response -> {
              assertThat(response.getErrorMessage()).isNull();
              assertThat(response.getPayload()).isNull();
            });

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getBody().readUtf8())
        .isEqualTo(
            "{\"block_hash\":\"0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2\"}");
    assertThat(request.getMethod()).isEqualTo("POST");
  }

  private static class FailingSerializableTypeDefinition
      implements SerializableTypeDefinition<TestObject2> {

    @Override
    public void serializeOpenApiType(final JsonGenerator gen) {}

    @Override
    public void serialize(final TestObject2 value, final JsonGenerator gen) throws IOException {
      throw new SocketException("Broken pipe");
    }

    @Override
    public SerializableTypeDefinition<TestObject2> withDescription(final String description) {
      return this;
    }
  }

  private static class TestObject {
    private String foo;

    public String getFoo() {
      return foo;
    }

    public void setFoo(final String foo) {
      this.foo = foo;
    }
  }

  private static class TestObject2 {
    private final Bytes32 blockHash;

    public TestObject2(final Bytes32 blockHash) {
      this.blockHash = blockHash;
    }

    public Bytes32 getBlockHash() {
      return blockHash;
    }
  }
}
