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

package tech.pegasys.teku.ethereum.executionclient.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.units.bigints.UInt256;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidV1;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.SignedBuilderBidV1;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistrationV1;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.ValidatorRegistrationV1;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

class RestExecutionBuilderClientTest {

  private static final Duration WAIT_FOR_CALL_COMPLETION = Duration.ofSeconds(10);

  private static final String INTERNAL_SERVER_ERROR_MESSAGE =
      "{\"code\":500,\"message\":\"Internal server error\"}";

  private static final String SIGNED_VALIDATOR_REGISTRATION_REQUEST =
      readResource("builder/signedValidatorRegistration.json");

  private static final Spec BELLATRIX_MINIMAL_SPEC = TestSpecFactory.createMinimalBellatrix();

  private static final SchemaDefinitionsBellatrix SCHEMA_DEFINITIONS_BELLATRIX =
      BELLATRIX_MINIMAL_SPEC
          .forMilestone(SpecMilestone.BELLATRIX)
          .getSchemaDefinitions()
          .toVersionBellatrix()
          .orElseThrow();

  private static final String SIGNED_BLINDED_BEACON_BLOCK_REQUEST =
      readResource("builder/signedBlindedBeaconBlock.json");

  private static final Bytes32 PARENT_HASH =
      Bytes32.fromHexString("0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2");
  private static final Bytes48 PUB_KEY =
      Bytes48.fromHexString(
          "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1af526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a");

  private static final SignedValidatorRegistrationV1 SIGNED_VALIDATOR_REGISTRATION =
      createSignedValidatorRegistration();

  private static final SignedBeaconBlock SIGNED_BLINDED_BEACON_BLOCK =
      createSignedBlindedBeaconBlock();

  private final MockWebServer mockWebServer = new MockWebServer();
  private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

  private RestExecutionBuilderClient restExecutionBuilderClient;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer.start();
    String endpoint = "http://localhost:" + mockWebServer.getPort();
    OkHttpRestClient okHttpRestClient = new OkHttpRestClient(okHttpClient, endpoint);
    this.restExecutionBuilderClient =
        new RestExecutionBuilderClient(okHttpRestClient, BELLATRIX_MINIMAL_SPEC);
  }

  @AfterEach
  void afterEach() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void getStatus_success() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    assertThat(restExecutionBuilderClient.status())
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.getPayload()).isNull();
            });

    verifyGetRequest("/eth1/v1/builder/status");
  }

  @Test
  void getStatus_failures() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(restExecutionBuilderClient.status())
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyGetRequest("/eth1/v1/builder/status");
  }

  @Test
  void registerValidator_success() {

    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    assertThat(
            restExecutionBuilderClient.registerValidator(UInt64.ONE, SIGNED_VALIDATOR_REGISTRATION))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              assertThat(response.getPayload()).isNull();
            });

    verifyPostRequest("/eth/v1/builder/validators", SIGNED_VALIDATOR_REGISTRATION_REQUEST);
  }

  @Test
  void registerValidator_failures() {

    String unknownValidatorError = "{\"code\":400,\"message\":\"unknown validator\"}";

    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(unknownValidatorError));

    assertThat(
            restExecutionBuilderClient.registerValidator(UInt64.ONE, SIGNED_VALIDATOR_REGISTRATION))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(unknownValidatorError);
            });

    verifyPostRequest("/eth/v1/builder/validators", SIGNED_VALIDATOR_REGISTRATION_REQUEST);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(
            restExecutionBuilderClient.registerValidator(UInt64.ONE, SIGNED_VALIDATOR_REGISTRATION))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyPostRequest("/eth/v1/builder/validators", SIGNED_VALIDATOR_REGISTRATION_REQUEST);
  }

  @Test
  void getExecutionPayloadHeader_success() {

    String executionPayloadHeaderResponse =
        readResource("builder/executionPayloadHeaderResponse.json");
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(executionPayloadHeaderResponse));

    assertThat(
            restExecutionBuilderClient.getHeader(
                UInt64.ONE, BLSPublicKey.fromBytesCompressed(PUB_KEY), PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              SignedBuilderBidV1 responsePayload = response.getPayload();
              verifyExecutionPayloadHeaderResponse(responsePayload);
            });

    verifyGetRequest("/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY);
  }

  @Test
  void getExecutionPayloadHeader_failures() {

    String missingParentHashError =
        "{\"code\":400,\"message\":\"Unknown hash: missing parent hash\"}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(missingParentHashError));

    assertThat(
            restExecutionBuilderClient.getHeader(
                UInt64.ONE, BLSPublicKey.fromBytesCompressed(PUB_KEY), PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(missingParentHashError);
            });

    verifyGetRequest("/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(
            restExecutionBuilderClient.getHeader(
                UInt64.ONE, BLSPublicKey.fromBytesCompressed(PUB_KEY), PARENT_HASH))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyGetRequest("/eth/v1/builder/header/1/" + PARENT_HASH + "/" + PUB_KEY);
  }

  @Test
  void sendSignedBlindedBlock_success() {

    String unblindedExecutionPayloadResponse =
        readResource("builder/unblindedExecutionPayloadResponse.json");
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(unblindedExecutionPayloadResponse));

    assertThat(restExecutionBuilderClient.getPayload(SIGNED_BLINDED_BEACON_BLOCK))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isSuccess()).isTrue();
              ExecutionPayload responsePayload = response.getPayload();
              verifyExecutionPayloadResponse(responsePayload);
            });

    verifyPostRequest("/eth/v1/builder/blinded_blocks", SIGNED_BLINDED_BEACON_BLOCK_REQUEST);
  }

  @Test
  void sendSignedBlindedBlock_failures() {

    String missingSignatureError =
        "{\"code\":400,\"message\":\"Invalid block: missing signature\"}";
    mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody(missingSignatureError));

    assertThat(restExecutionBuilderClient.getPayload(SIGNED_BLINDED_BEACON_BLOCK))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(missingSignatureError);
            });

    verifyPostRequest("/eth/v1/builder/blinded_blocks", SIGNED_BLINDED_BEACON_BLOCK_REQUEST);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(500).setBody(INTERNAL_SERVER_ERROR_MESSAGE));

    assertThat(restExecutionBuilderClient.getPayload(SIGNED_BLINDED_BEACON_BLOCK))
        .succeedsWithin(WAIT_FOR_CALL_COMPLETION)
        .satisfies(
            response -> {
              assertThat(response.isFailure()).isTrue();
              assertThat(response.getErrorMessage()).isEqualTo(INTERNAL_SERVER_ERROR_MESSAGE);
            });

    verifyPostRequest("/eth/v1/builder/blinded_blocks", SIGNED_BLINDED_BEACON_BLOCK_REQUEST);
  }

  private void verifyGetRequest(String apiPath) {
    verifyRequest("GET", apiPath, Optional.empty());
  }

  private void verifyPostRequest(String apiPath, String requestBody) {
    verifyRequest("POST", apiPath, Optional.of(requestBody));
  }

  private void verifyRequest(String method, String apiPath, Optional<String> requestBody) {
    try {
      RecordedRequest request = mockWebServer.takeRequest();
      assertThat(request.getMethod()).isEqualTo(method);
      assertThat(request.getPath()).isEqualTo(apiPath);
      if (requestBody.isEmpty()) {
        assertThat(request.getBody().size()).isZero();
      } else {
        assertThat(request.getBody().size()).isNotZero();
        JSONAssert.assertEquals(requestBody.get(), request.getBody().readUtf8(), true);
      }
    } catch (InterruptedException | JSONException ex) {
      Assertions.fail(ex);
    }
  }

  private void verifyExecutionPayloadHeaderResponse(SignedBuilderBidV1 response) {
    assertThat(response).isNotNull();
    BLSSignature signature = response.getSignature();
    assertThat(signature.toString())
        .isEqualTo(
            "0x1b66ac1fb663c9bc59509846d6ec05345bd908eda73e670af888da41af171505cc411d61252fb6cb3fa0017b679f8bb2305b26a285fa2737f175668d0dff91cc1b66ac1fb663c9bc59509846d6ec05345bd908eda73e670af888da41af171505");
    BuilderBidV1 builderBidV1 = response.getMessage();
    assertThat(builderBidV1.getValue()).isEqualTo(UInt256.ONE);
    assertThat(builderBidV1.getPublicKey()).isEqualTo(BLSPublicKey.fromBytesCompressed(PUB_KEY));
    // payload header assertions
    ExecutionPayloadHeader payloadHeader = builderBidV1.getExecutionPayloadHeader();
    assertThat(payloadHeader.getParentHash())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2"));
    assertThat(payloadHeader.getFeeRecipient())
        .isEqualTo(Bytes20.fromHexString("0xabcf8e0d4e9587369b2301d0790347320302cc09"));
    assertThat(payloadHeader.getStateRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2"));
    assertThat(payloadHeader.getReceiptsRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2"));
    assertThat(payloadHeader.getLogsBloom())
        .isEqualTo(
            Bytes.fromHexString(
                "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));
    assertThat(payloadHeader.getPrevRandao())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2"));
    assertThat(payloadHeader.getBlockNumber()).isEqualTo(UInt64.ONE);
    assertThat(payloadHeader.getGasLimit()).isEqualTo(UInt64.ONE);
    assertThat(payloadHeader.getGasUsed()).isEqualTo(UInt64.ONE);
    assertThat(payloadHeader.getTimestamp()).isEqualTo(UInt64.ONE);
    assertThat(payloadHeader.getExtraData())
        .isEqualTo(
            Bytes.fromHexString(
                "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2"));
    assertThat(payloadHeader.getBaseFeePerGas()).isEqualTo(UInt256.ONE);
    assertThat(payloadHeader.getBlockHash())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2"));
    assertThat(payloadHeader.getTransactionsRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2"));
  }

  private void verifyExecutionPayloadResponse(ExecutionPayload payloadResponse) {
    assertThat(payloadResponse).isNotNull();
    assertThat(payloadResponse.getParentHash())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2"));
    assertThat(payloadResponse.getFeeRecipient())
        .isEqualTo(Bytes20.fromHexString("0xabcf8e0d4e9587369b2301d0790347320302cc09"));
    assertThat(payloadResponse.getStateRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2"));
    assertThat(payloadResponse.getReceiptsRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2"));
    assertThat(payloadResponse.getLogsBloom())
        .isEqualTo(
            Bytes.fromHexString(
                "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));
    assertThat(payloadResponse.getPrevRandao())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2"));
    assertThat(payloadResponse.getBlockNumber()).isEqualTo(UInt64.ONE);
    assertThat(payloadResponse.getGasLimit()).isEqualTo(UInt64.ONE);
    assertThat(payloadResponse.getGasUsed()).isEqualTo(UInt64.ONE);
    assertThat(payloadResponse.getTimestamp()).isEqualTo(UInt64.ONE);
    assertThat(payloadResponse.getExtraData())
        .isEqualTo(
            Bytes.fromHexString(
                "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2"));
    assertThat(payloadResponse.getBaseFeePerGas()).isEqualTo(UInt256.ONE);
    assertThat(payloadResponse.getBlockHash())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2"));
    // transactions assertions
    SszList<Transaction> transactions = payloadResponse.getTransactions();
    assertThat(transactions)
        .hasSize(1)
        .first()
        .satisfies(
            transaction ->
                assertThat(transaction.getBytes())
                    .isEqualTo(
                        Bytes.fromHexString(
                            "0x02f878831469668303f51d843b9ac9f9843b9aca0082520894c93269b73096998db66be0441e836d873535cb9c8894a19041886f000080c001a031cc29234036afbf9a1fb9476b463367cb1f957ac0b919b69bbc798436e604aaa018c4e9c3914eb27aadd0b91e10b18655739fcf8c1fc398763a9f1beecb8ddc86")));
  }

  private static SignedValidatorRegistrationV1 createSignedValidatorRegistration() {
    BLSSignature signature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromHexString(
                "0x1b66ac1fb663c9bc59509846d6ec05345bd908eda73e670af888da41af171505cc411d61252fb6cb3fa0017b679f8bb2305b26a285fa2737f175668d0dff91cc1b66ac1fb663c9bc59509846d6ec05345bd908eda73e670af888da41af171505"));
    Bytes20 feeRecipient = Bytes20.fromHexString("0xabcf8e0d4e9587369b2301d0790347320302cc09");
    UInt64 gasLimit = UInt64.ONE;
    UInt64 timestamp = UInt64.ONE;
    BLSPublicKey pubKey =
        BLSPublicKey.fromBytesCompressed(
            Bytes48.fromHexString(
                "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1af526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a"));

    ValidatorRegistrationV1 validatorRegistrationV1 =
        SCHEMA_DEFINITIONS_BELLATRIX
            .getValidatorRegistrationSchema()
            .create(feeRecipient, gasLimit, timestamp, pubKey);
    return SCHEMA_DEFINITIONS_BELLATRIX
        .getSignedValidatorRegistrationSchema()
        .create(validatorRegistrationV1, signature);
  }

  private static SignedBeaconBlock createSignedBlindedBeaconBlock() {
    try {
      return JsonUtil.parse(
          SIGNED_BLINDED_BEACON_BLOCK_REQUEST,
          SCHEMA_DEFINITIONS_BELLATRIX.getSignedBlindedBeaconBlockSchema().getJsonTypeDefinition());
    } catch (JsonProcessingException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static String readResource(String resource) {
    try {
      return Resources.toString(Resources.getResource(resource), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
