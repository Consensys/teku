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

import java.io.IOException;
import java.time.Duration;
import java.util.List;
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

  private OkHttpRestClient underTest;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer.start();
    String endpoint = "http://localhost:" + mockWebServer.getPort();
    this.responseTypeDefinition =
        DeserializableTypeDefinition.object(TestObject.class)
            .initializer(TestObject::new)
            .withField("foo", CoreTypes.STRING_TYPE, TestObject::getFoo, TestObject::setFoo)
            .build();
    this.requestTypeDefinition =
        SerializableTypeDefinition.object(TestObject2.class)
            .withField("block_hash", CoreTypes.BYTES32_TYPE, TestObject2::getBlockHash)
            .build();
    this.underTest = new OkHttpRestClient(okHttpClient, endpoint);
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

    SafeFuture<Response<TestObject>> responseFuture =
        underTest.getAsync(TEST_PATH, responseTypeDefinition);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(10))
        .satisfies(
            response -> {
              assertThat(response.getErrorMessage()).isNull();
              assertThat(response.getPayload())
                  .satisfies(testObject -> assertThat(testObject.getFoo()).isEqualTo("bar"));
            });

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getMethod()).isEqualTo("GET");
  }

  @Test
  void getsResponseAsyncIgnoringResponseBody() throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    SafeFuture<Response<Void>> responseFuture = underTest.getAsync(TEST_PATH);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(5))
        .satisfies(
            response -> {
              assertThat(response.getErrorMessage()).isNull();
              assertThat(response.getPayload()).isNull();
            });

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getMethod()).isEqualTo("GET");
  }

  @Test
  void getAsyncHandlesFailures() throws InterruptedException {
    String errorBody = "{\"code\":400,\"message\":\"Invalid block: missing signature\"}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(errorBody));

    SafeFuture<Response<TestObject>> responseFuture =
        underTest.getAsync(TEST_PATH, responseTypeDefinition);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(5))
        .satisfies(
            response -> {
              assertThat(response.getErrorMessage()).isEqualTo(errorBody);
              assertThat(response.getPayload()).isNull();
            });

    // error without a body
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    SafeFuture<Response<TestObject>> secondResponseFuture =
        underTest.getAsync(TEST_PATH, responseTypeDefinition);

    assertThat(secondResponseFuture)
        .succeedsWithin(Duration.ofSeconds(5))
        .satisfies(
            response -> {
              assertThat(response.getErrorMessage()).isEqualTo("500: Server Error");
              assertThat(response.getPayload()).isNull();
            });

    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

    RecordedRequest request = mockWebServer.takeRequest();
    RecordedRequest secondRequest = mockWebServer.takeRequest();

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

    TestObject2 requestBodyObject = new TestObject2(TEST_BLOCK_HASH);
    SafeFuture<Response<TestObject>> responseFuture =
        underTest.postAsync(
            TEST_PATH, requestBodyObject, requestTypeDefinition, responseTypeDefinition);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(10))
        .satisfies(
            response -> {
              assertThat(response.getErrorMessage()).isNull();
              assertThat(response.getPayload())
                  .satisfies(testObject -> assertThat(testObject.getFoo()).isEqualTo("bar"));
            });

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getBody().readUtf8())
        .isEqualTo(
            "{\"block_hash\":\"0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2\"}");
    assertThat(request.getMethod()).isEqualTo("POST");
  }

  @Test
  void postsAsyncIgnoringResponseBody() throws InterruptedException {
    mockWebServer.enqueue(
        new MockResponse()
            .setBody("{\"foo\" : \"bar\"}")
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

    TestObject2 requestBodyObject = new TestObject2(TEST_BLOCK_HASH);
    SafeFuture<Response<Void>> responseFuture =
        underTest.postAsync(TEST_PATH, requestBodyObject, requestTypeDefinition);

    assertThat(responseFuture)
        .succeedsWithin(Duration.ofSeconds(10))
        .satisfies(
            response -> {
              assertThat(response.getErrorMessage()).isNull();
              assertThat(response.getPayload()).isNull();
            });

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getPath()).isEqualTo("/" + TEST_PATH);
    assertThat(request.getBody().readUtf8())
        .isEqualTo(
            "{\"block_hash\":\"0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2\"}");
    assertThat(request.getMethod()).isEqualTo("POST");
  }

  private static class TestObject {
    private String foo;

    public String getFoo() {
      return foo;
    }

    public void setFoo(String foo) {
      this.foo = foo;
    }
  }

  private static class TestObject2 {
    private final Bytes32 blockHash;

    public TestObject2(Bytes32 blockHash) {
      this.blockHash = blockHash;
    }

    public Bytes32 getBlockHash() {
      return blockHash;
    }
  }
}
