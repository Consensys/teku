/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.validator.remote.apiclient;

import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_AGGREGATE_AND_PROOF;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_ATTESTATION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SYNC_COMMITTEE_MESSAGES;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.SignedAggregateAndProof;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeMessage;
import tech.pegasys.teku.provider.JsonProvider;

public class OkHttpValidatorRestApiClient implements ValidatorRestApiClient {

  private static final Logger LOG = LogManager.getLogger();

  private static final MediaType APPLICATION_JSON =
      MediaType.parse("application/json; charset=utf-8");

  private final JsonProvider jsonProvider = new JsonProvider();
  private final OkHttpClient httpClient;
  private final HttpUrl baseEndpoint;

  public OkHttpValidatorRestApiClient(final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    this.baseEndpoint = baseEndpoint;
    this.httpClient = okHttpClient;
  }

  @Override
  public Optional<PostDataFailureResponse> sendSignedAttestations(
      final List<Attestation> attestations) {
    return post(
        SEND_SIGNED_ATTESTATION,
        Collections.emptyMap(),
        attestations,
        ResponseHandler.createForEmptyOkAndContentInBadResponse(
            jsonProvider, PostDataFailureResponse.class));
  }

  @Override
  public Optional<PostDataFailureResponse> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> signedAggregateAndProof) {
    return post(
        SEND_SIGNED_AGGREGATE_AND_PROOF,
        Collections.emptyMap(),
        signedAggregateAndProof,
        ResponseHandler.createForEmptyOkAndContentInBadResponse(
            jsonProvider, PostDataFailureResponse.class));
  }

  @Override
  public Optional<PostDataFailureResponse> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return post(
        SEND_SYNC_COMMITTEE_MESSAGES,
        Collections.emptyMap(),
        syncCommitteeMessages,
        ResponseHandler.createForEmptyOkAndContentInBadResponse(
            jsonProvider, PostDataFailureResponse.class));
  }

  private <T> Optional<T> post(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Object requestBodyObj,
      final ResponseHandler<T> responseHandler) {
    final HttpUrl.Builder httpUrlBuilder =
        baseEndpoint.resolve(apiMethod.getPath(urlParams)).newBuilder();
    final String requestBody;
    try {
      requestBody = jsonProvider.objectToJSON(requestBodyObj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    final Request request =
        requestBuilder()
            .url(httpUrlBuilder.build())
            .post(RequestBody.create(requestBody, APPLICATION_JSON))
            .build();

    return executeCall(request, responseHandler);
  }

  private Request.Builder requestBuilder() {
    final Request.Builder builder = new Request.Builder();
    if (!baseEndpoint.username().isEmpty()) {
      builder.header(
          "Authorization",
          Credentials.basic(baseEndpoint.encodedUsername(), baseEndpoint.encodedPassword()));
    }
    return builder;
  }

  private <T> Optional<T> executeCall(
      final Request request, final ResponseHandler<T> responseHandler) {
    try (final Response response = httpClient.newCall(request).execute()) {
      LOG.trace("{} {} {}", request.method(), request.url(), response.code());
      return responseHandler.handleResponse(request, response);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Error communicating with Beacon Node API: " + e.getMessage(), e);
    }
  }
}
