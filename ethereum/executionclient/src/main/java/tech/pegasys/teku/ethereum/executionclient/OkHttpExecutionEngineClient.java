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

import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getMessageOrSimpleName;
import static tech.pegasys.teku.spec.config.Constants.EL_ENGINE_BLOCK_EXECUTION_TIMEOUT;
import static tech.pegasys.teku.spec.config.Constants.EL_ENGINE_NON_BLOCK_EXECUTION_TIMEOUT;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobAndProofV1;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobAndProofV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ClientVersionV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV4;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV2Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV3Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV4Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV5Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV6Response;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV2;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV3;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV4;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

public class OkHttpExecutionEngineClient implements ExecutionEngineClient {

  private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

  private static final int ERROR_REPEAT_DELAY_MILLIS = 30 * 1000;
  private static final int NO_ERROR_TIME = -1;
  private static final long STARTUP_LAST_ERROR_TIME = 0;

  private static final Duration EXCHANGE_CAPABILITIES_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration GET_CLIENT_VERSION_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration GET_BLOBS_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration GET_PAYLOAD_TIMEOUT = Duration.ofSeconds(2);

  public static final List<String> NON_CRITICAL_METHODS =
      List.of("engine_exchangeCapabilities", "engine_getClientVersionV1", "engine_getBlobsV1");

  private final OkHttpClient httpClient;
  private final HttpUrl endpointUrl;
  private final EventLogger eventLog;
  private final TimeProvider timeProvider;
  private final ExecutionClientEventsChannel executionClientEventsPublisher;
  private final Set<String> nonCriticalMethods;
  private final ObjectMapper objectMapper;
  private final AtomicLong requestIdCounter = new AtomicLong(0);

  private long lastErrorTime = STARTUP_LAST_ERROR_TIME;

  public OkHttpExecutionEngineClient(
      final OkHttpClient httpClient,
      final String endpoint,
      final EventLogger eventLog,
      final TimeProvider timeProvider,
      final ExecutionClientEventsChannel executionClientEventsPublisher,
      final Collection<String> nonCriticalMethods) {
    this.httpClient = httpClient;
    this.endpointUrl = HttpUrl.get(endpoint);
    this.eventLog = eventLog;
    this.timeProvider = timeProvider;
    this.executionClientEventsPublisher = executionClientEventsPublisher;
    this.nonCriticalMethods = new HashSet<>(nonCriticalMethods);
    this.objectMapper =
        JsonMapper.builder()
            .addModule(new BlackbirdModule())
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
  }

  @Override
  public SafeFuture<PowBlock> getPowBlock(final Bytes32 blockHash) {
    return doEthBlockRequest("eth_getBlockByHash", list(blockHash.toHexString(), false));
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return doEthBlockRequest("eth_getBlockByNumber", list("latest", false));
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> getPayloadV1(final Bytes8 payloadId) {
    return doRequest(
        "engine_getPayloadV1",
        Collections.singletonList(payloadId.toHexString()),
        ExecutionPayloadV1.class,
        GET_PAYLOAD_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<GetPayloadV2Response>> getPayloadV2(final Bytes8 payloadId) {
    return doRequest(
        "engine_getPayloadV2",
        Collections.singletonList(payloadId.toHexString()),
        GetPayloadV2Response.class,
        GET_PAYLOAD_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<GetPayloadV3Response>> getPayloadV3(final Bytes8 payloadId) {
    return doRequest(
        "engine_getPayloadV3",
        Collections.singletonList(payloadId.toHexString()),
        GetPayloadV3Response.class,
        GET_PAYLOAD_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<GetPayloadV4Response>> getPayloadV4(final Bytes8 payloadId) {
    return doRequest(
        "engine_getPayloadV4",
        Collections.singletonList(payloadId.toHexString()),
        GetPayloadV4Response.class,
        GET_PAYLOAD_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<GetPayloadV5Response>> getPayloadV5(final Bytes8 payloadId) {
    return doRequest(
        "engine_getPayloadV5",
        Collections.singletonList(payloadId.toHexString()),
        GetPayloadV5Response.class,
        GET_PAYLOAD_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<GetPayloadV6Response>> getPayloadV6(final Bytes8 payloadId) {
    return doRequest(
        "engine_getPayloadV6",
        Collections.singletonList(payloadId.toHexString()),
        GetPayloadV6Response.class,
        GET_PAYLOAD_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV1(
      final ExecutionPayloadV1 executionPayload) {
    return doRequest(
        "engine_newPayloadV1",
        Collections.singletonList(executionPayload),
        PayloadStatusV1.class,
        EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV2(
      final ExecutionPayloadV2 executionPayload) {
    return doRequest(
        "engine_newPayloadV2",
        Collections.singletonList(executionPayload),
        PayloadStatusV1.class,
        EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV3(
      final ExecutionPayloadV3 executionPayload,
      final List<VersionedHash> blobVersionedHashes,
      final Bytes32 parentBeaconBlockRoot) {
    final List<String> versionedHashHexes =
        blobVersionedHashes.stream().map(VersionedHash::toHexString).toList();
    return doRequest(
        "engine_newPayloadV3",
        list(executionPayload, versionedHashHexes, parentBeaconBlockRoot.toHexString()),
        PayloadStatusV1.class,
        EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV4(
      final ExecutionPayloadV3 executionPayload,
      final List<VersionedHash> blobVersionedHashes,
      final Bytes32 parentBeaconBlockRoot,
      final List<Bytes> executionRequests) {
    final List<String> versionedHashHexes =
        blobVersionedHashes.stream().map(VersionedHash::toHexString).toList();
    final List<String> executionRequestHexes =
        executionRequests.stream().map(Bytes::toHexString).toList();
    return doRequest(
        "engine_newPayloadV4",
        list(
            executionPayload,
            versionedHashHexes,
            parentBeaconBlockRoot.toHexString(),
            executionRequestHexes),
        PayloadStatusV1.class,
        EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV5(
      final ExecutionPayloadV4 executionPayload,
      final List<VersionedHash> blobVersionedHashes,
      final Bytes32 parentBeaconBlockRoot,
      final List<Bytes> executionRequests) {
    final List<String> versionedHashHexes =
        blobVersionedHashes.stream().map(VersionedHash::toHexString).toList();
    final List<String> executionRequestHexes =
        executionRequests.stream().map(Bytes::toHexString).toList();
    return doRequest(
        "engine_newPayloadV5",
        list(
            executionPayload,
            versionedHashHexes,
            parentBeaconBlockRoot.toHexString(),
            executionRequestHexes),
        PayloadStatusV1.class,
        EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV1(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV1> payloadAttributes) {
    return doRequest(
        "engine_forkchoiceUpdatedV1",
        list(forkChoiceState, payloadAttributes.orElse(null)),
        ForkChoiceUpdatedResult.class,
        EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV2(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV2> payloadAttributes) {
    return doRequest(
        "engine_forkchoiceUpdatedV2",
        list(forkChoiceState, payloadAttributes.orElse(null)),
        ForkChoiceUpdatedResult.class,
        EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV3(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV3> payloadAttributes) {
    return doRequest(
        "engine_forkchoiceUpdatedV3",
        list(forkChoiceState, payloadAttributes.orElse(null)),
        ForkChoiceUpdatedResult.class,
        EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV4(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV4> payloadAttributes) {
    return doRequest(
        "engine_forkchoiceUpdatedV4",
        list(forkChoiceState, payloadAttributes.orElse(null)),
        ForkChoiceUpdatedResult.class,
        EL_ENGINE_BLOCK_EXECUTION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<List<String>>> exchangeCapabilities(final List<String> capabilities) {
    return doRequest(
        "engine_exchangeCapabilities",
        Collections.singletonList(capabilities),
        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class),
        EXCHANGE_CAPABILITIES_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<List<ClientVersionV1>>> getClientVersionV1(
      final ClientVersionV1 clientVersion) {
    return doRequest(
        "engine_getClientVersionV1",
        Collections.singletonList(clientVersion),
        objectMapper.getTypeFactory().constructCollectionType(List.class, ClientVersionV1.class),
        GET_CLIENT_VERSION_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<List<BlobAndProofV1>>> getBlobsV1(
      final List<VersionedHash> blobVersionedHashes) {
    final List<String> versionedHashHexes =
        blobVersionedHashes.stream().map(VersionedHash::toHexString).toList();
    return doRequest(
        "engine_getBlobsV1",
        list(versionedHashHexes),
        objectMapper.getTypeFactory().constructCollectionType(List.class, BlobAndProofV1.class),
        GET_BLOBS_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<List<BlobAndProofV2>>> getBlobsV2(
      final List<VersionedHash> blobVersionedHashes) {
    final List<String> versionedHashHexes =
        blobVersionedHashes.stream().map(VersionedHash::toHexString).toList();
    return doRequest(
        "engine_getBlobsV2",
        list(versionedHashHexes),
        objectMapper.getTypeFactory().constructCollectionType(List.class, BlobAndProofV2.class),
        GET_BLOBS_TIMEOUT);
  }

  // ----------------------------- Core infrastructure -----------------------------

  private SafeFuture<PowBlock> doEthBlockRequest(final String method, final List<Object> params) {
    return doRequest(method, params, EthBlockResult.class, EL_ENGINE_NON_BLOCK_EXECUTION_TIMEOUT)
        .thenApply(Response::payload)
        .thenApply(OkHttpExecutionEngineClient::ethBlockToPowBlock);
  }

  private static PowBlock ethBlockToPowBlock(final EthBlockResult block) {
    return block == null
        ? null
        : new PowBlock(
            Bytes32.fromHexStringStrict(block.hash),
            Bytes32.fromHexStringStrict(block.parentHash),
            UInt64.valueOf(new BigInteger(block.timestamp.substring(2), 16)));
  }

  private <T> SafeFuture<Response<T>> doRequest(
      final String method,
      final List<Object> params,
      final Class<T> resultClass,
      final Duration timeout) {
    return doRequest(method, params, objectMapper.constructType(resultClass), timeout);
  }

  private <T> SafeFuture<Response<T>> doRequest(
      final String method,
      final List<Object> params,
      final JavaType resultType,
      final Duration timeout) {
    final boolean isCritical = !nonCriticalMethods.contains(method);

    final byte[] requestBodyBytes;
    try {
      requestBodyBytes = buildRequestBody(method, params);
    } catch (final Exception e) {
      handleError(isCritical, e, false);
      return SafeFuture.completedFuture(Response.fromErrorMessage(getMessageOrSimpleName(e)));
    }

    final Request httpRequest =
        new Request.Builder()
            .url(endpointUrl)
            .post(RequestBody.create(requestBodyBytes, JSON_MEDIA_TYPE))
            .build();

    final SafeFuture<Response<T>> future = new SafeFuture<>();
    final Call call;
    if (timeout.toMillis() != httpClient.callTimeoutMillis()) {
      call = httpClient.newBuilder().callTimeout(timeout).build().newCall(httpRequest);
    } else {
      call = httpClient.newCall(httpRequest);
    }

    call.enqueue(
        new Callback() {
          @Override
          public void onFailure(final Call call, final IOException e) {
            handleError(isCritical, e, false);
            future.complete(Response.fromErrorMessage(getMessageOrSimpleName(e)));
          }

          @Override
          public void onResponse(final Call call, final okhttp3.Response httpResponse) {
            try (httpResponse) {
              final ResponseBody body = httpResponse.body();
              if (!httpResponse.isSuccessful() || body == null) {
                final boolean couldBeAuthError =
                    httpResponse.code() == 401 || httpResponse.code() == 403;
                final String errorMsg =
                    body != null
                        ? body.string()
                        : (httpResponse.code() + ": " + httpResponse.message());
                handleError(isCritical, new Exception(errorMsg), couldBeAuthError);
                future.complete(Response.fromErrorMessage(errorMsg));
                return;
              }

              final JsonNode jsonResponse = objectMapper.readTree(body.string());
              final JsonNode errorNode = jsonResponse.get("error");
              if (errorNode != null && !errorNode.isNull()) {
                final int code = errorNode.path("code").asInt();
                final String msg = errorNode.path("message").asText();
                final String formattedError =
                    String.format(
                        "JSON-RPC error: %s (%d): %s", describeJsonRpcErrorCode(code), code, msg);
                if (isCritical) {
                  eventLog.executionClientRequestFailed(new Exception(formattedError), false);
                }
                future.complete(Response.fromErrorMessage(formattedError));
                return;
              }

              handleSuccess(isCritical);
              final JsonNode resultNode = jsonResponse.get("result");
              final T result =
                  resultNode == null || resultNode.isNull()
                      ? null
                      : objectMapper.treeToValue(resultNode, resultType);
              future.complete(Response.fromPayloadReceivedAsJson(result));
            } catch (final Exception e) {
              handleError(isCritical, e, false);
              future.complete(Response.fromErrorMessage(getMessageOrSimpleName(e)));
            }
          }
        });

    return future;
  }

  private byte[] buildRequestBody(final String method, final List<Object> params)
      throws JsonProcessingException {
    final Map<String, Object> request = new LinkedHashMap<>();
    request.put("jsonrpc", "2.0");
    request.put("method", method);
    request.put("params", params);
    request.put("id", requestIdCounter.incrementAndGet());
    return objectMapper.writeValueAsBytes(request);
  }

  private static String describeJsonRpcErrorCode(final int code) {
    if (code == -32700) {
      return "Parse error";
    }
    if (code == -32600) {
      return "Invalid Request";
    }
    if (code == -32601) {
      return "Method not found";
    }
    if (code == -32602) {
      return "Invalid params";
    }
    if (code == -32603) {
      return "Internal error";
    }
    if (code >= -32099 && code <= -32000) {
      return "Server error";
    }
    return "Internal error";
  }

  private synchronized void handleSuccess(final boolean isCritical) {
    if (isCritical) {
      if (lastErrorTime == STARTUP_LAST_ERROR_TIME) {
        eventLog.executionClientIsOnline();
        executionClientEventsPublisher.onAvailabilityUpdated(true);
      } else if (lastErrorTime != NO_ERROR_TIME) {
        eventLog.executionClientRecovered();
        executionClientEventsPublisher.onAvailabilityUpdated(true);
      }
      lastErrorTime = NO_ERROR_TIME;
    }
  }

  private synchronized void handleError(
      final boolean isCritical, final Throwable error, final boolean couldBeAuthError) {
    if (isCritical && shouldReportError()) {
      logExecutionClientError(error, couldBeAuthError);
      executionClientEventsPublisher.onAvailabilityUpdated(false);
    }
  }

  private synchronized boolean shouldReportError() {
    final long timeNow = timeProvider.getTimeInMillis().longValue();
    if (lastErrorTime == NO_ERROR_TIME || timeNow - lastErrorTime > ERROR_REPEAT_DELAY_MILLIS) {
      lastErrorTime = timeNow;
      return true;
    }
    return false;
  }

  private void logExecutionClientError(final Throwable error, final boolean couldBeAuthError) {
    if (ExceptionUtil.hasCause(error, ConnectException.class)) {
      eventLog.executionClientConnectFailure();
    } else if (error instanceof TimeoutException) {
      eventLog.executionClientRequestTimedOut();
    } else {
      eventLog.executionClientRequestFailed(error, couldBeAuthError);
    }
  }

  private List<Object> list(final Object... items) {
    final List<Object> list = new ArrayList<>();
    Collections.addAll(list, items);
    return list;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class EthBlockResult {

    @JsonProperty("hash")
    public String hash;

    @JsonProperty("parentHash")
    public String parentHash;

    @JsonProperty("timestamp")
    public String timestamp;
  }
}
