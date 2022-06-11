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

package tech.pegasys.teku.validator.remote.typedef;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszDataAssert;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.remote.typedef.handlers.RegisterValidatorsRequest;

@TestSpecContext(
    milestone = SpecMilestone.BELLATRIX,
    network = {Eth2Network.MAINNET})
class OkHttpValidatorTypeDefClientTest {

  private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
  private static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private DataStructureUtil dataStructureUtil;

  private OkHttpValidatorTypeDefClient okHttpValidatorTypeDefClient;

  private RegisterValidatorsRequest sszRegisterValidatorsRequest;

  private final MockWebServer mockWebServer = new MockWebServer();
  private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

  @BeforeEach
  public void beforeEach(SpecContext specContext) throws Exception {
    mockWebServer.start();
    okHttpValidatorTypeDefClient =
        new OkHttpValidatorTypeDefClient(
            okHttpClient, mockWebServer.url("/"), specContext.getSpec(), false);
    sszRegisterValidatorsRequest =
        new RegisterValidatorsRequest(mockWebServer.url("/"), okHttpClient, true);
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockWebServer.shutdown();
  }

  @TestTemplate
  void registerValidators_makesJsonRequest() throws InterruptedException, JsonProcessingException {

    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    String expectedRequest =
        JsonUtil.serialize(
            validatorRegistrations,
            ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition());

    okHttpValidatorTypeDefClient.registerValidators(validatorRegistrations);

    RecordedRequest recordedRequest = mockWebServer.takeRequest();

    verifyRegisterValidatorsPostRequest(recordedRequest, JSON_CONTENT_TYPE);

    String actualRequest = recordedRequest.getBody().readUtf8();

    assertJsonEquals(actualRequest, expectedRequest);
  }

  @TestTemplate
  void registerValidators_handlesFailures() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(400).setBody("{\"code\":400,\"message\":\"oopsy\"}"));

    SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    IllegalArgumentException badRequestException =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> okHttpValidatorTypeDefClient.registerValidators(validatorRegistrations));

    assertThat(badRequestException.getMessage())
        .contains("Invalid params response from Beacon Node API");

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(500)
            .setBody("{\"code\":500,\"message\":\"Internal server error\"}"));

    RemoteServiceNotAvailableException serverException =
        Assertions.assertThrows(
            RemoteServiceNotAvailableException.class,
            () -> okHttpValidatorTypeDefClient.registerValidators(validatorRegistrations));

    assertThat(serverException.getMessage()).contains("Server error from Beacon Node API");
  }

  @TestTemplate
  void registerValidators_makesSszRequestIfSszEncodingPreferred() throws InterruptedException {

    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    sszRegisterValidatorsRequest.registerValidators(validatorRegistrations);

    RecordedRequest recordedRequest = mockWebServer.takeRequest();

    verifyRegisterValidatorsPostRequest(recordedRequest, OCTET_STREAM_CONTENT_TYPE);

    byte[] sszBytes = recordedRequest.getBody().readByteArray();

    SszList<SignedValidatorRegistration> deserializedSszBytes =
        ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.sszDeserialize(Bytes.of(sszBytes));

    SszDataAssert.assertThatSszData(deserializedSszBytes)
        .isEqualByAllMeansTo(validatorRegistrations);
  }

  @TestTemplate
  void registerValidators_fallbacksToJsonIfSszNotSupported() throws InterruptedException {

    mockWebServer.enqueue(new MockResponse().setResponseCode(415));
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    sszRegisterValidatorsRequest.registerValidators(validatorRegistrations);

    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

    verifyRegisterValidatorsPostRequest(mockWebServer.takeRequest(), OCTET_STREAM_CONTENT_TYPE);
    verifyRegisterValidatorsPostRequest(mockWebServer.takeRequest(), JSON_CONTENT_TYPE);

    // subsequent requests default immediately to json
    sszRegisterValidatorsRequest.registerValidators(validatorRegistrations);

    assertThat(mockWebServer.getRequestCount()).isEqualTo(3);

    verifyRegisterValidatorsPostRequest(mockWebServer.takeRequest(), JSON_CONTENT_TYPE);
  }

  private void verifyRegisterValidatorsPostRequest(
      RecordedRequest recordedRequest, String expectedContentType) {
    assertThat(recordedRequest.getPath()).isEqualTo("/eth/v1/validator/register_validator");
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo(expectedContentType);
  }

  private void assertJsonEquals(String actual, String expected) {
    try {
      assertThat(OBJECT_MAPPER.readTree(actual)).isEqualTo(OBJECT_MAPPER.readTree(expected));
    } catch (JsonProcessingException ex) {
      Assertions.fail(ex);
    }
  }
}
