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

import static java.util.Collections.emptyMap;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_AGGREGATE;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.PREPARE_BEACON_PROPOSER;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_CONTRIBUTION_AND_PROOF;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_AGGREGATE_AND_PROOF;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_ATTESTATION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SYNC_COMMITTEE_MESSAGES;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_VALIDATOR_LIVENESS;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.response.v1.validator.GetAggregatedAttestationResponse;
import tech.pegasys.teku.api.response.v1.validator.PostValidatorLivenessResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.SignedAggregateAndProof;
import tech.pegasys.teku.api.schema.altair.SignedContributionAndProof;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeMessage;
import tech.pegasys.teku.api.schema.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class OkHttpValidatorRestApiClient implements ValidatorRestApiClient {

  private static final Logger LOG = LogManager.getLogger();

  private static final MediaType APPLICATION_JSON =
      MediaType.parse("application/json; charset=utf-8");
  private static final Map<String, String> EMPTY_MAP = emptyMap();

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
        attestations,
        ResponseHandler.createForEmptyOkAndContentInBadResponse(
            jsonProvider, PostDataFailureResponse.class));
  }

  @Override
  public Optional<Attestation> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("slot", encodeQueryParam(slot));
    queryParams.put("attestation_data_root", encodeQueryParam(attestationHashTreeRoot));

    return get(
            GET_AGGREGATE,
            queryParams,
            createHandler(GetAggregatedAttestationResponse.class)
                .withHandler(SC_NOT_FOUND, (request, response) -> Optional.empty()))
        .map(result -> result.data);
  }

  @Override
  public Optional<PostDataFailureResponse> sendAggregateAndProofs(
      final List<SignedAggregateAndProof> signedAggregateAndProof) {
    return post(
        SEND_SIGNED_AGGREGATE_AND_PROOF,
        signedAggregateAndProof,
        ResponseHandler.createForEmptyOkAndContentInBadResponse(
            jsonProvider, PostDataFailureResponse.class));
  }

  @Override
  public Optional<PostDataFailureResponse> sendSyncCommitteeMessages(
      final List<SyncCommitteeMessage> syncCommitteeMessages) {
    return post(
        SEND_SYNC_COMMITTEE_MESSAGES,
        syncCommitteeMessages,
        ResponseHandler.createForEmptyOkAndContentInBadResponse(
            jsonProvider, PostDataFailureResponse.class));
  }

  @Override
  public void sendContributionAndProofs(
      final List<SignedContributionAndProof> signedContributionAndProofs) {
    post(SEND_CONTRIBUTION_AND_PROOF, signedContributionAndProofs, createHandler());
  }

  @Override
  public void prepareBeaconProposer(
      final List<BeaconPreparableProposer> beaconPreparableProposers) {
    post(PREPARE_BEACON_PROPOSER, beaconPreparableProposers, createHandler());
  }

  @Override
  public Optional<PostValidatorLivenessResponse> sendValidatorsLiveness(
      final UInt64 epoch, final List<UInt64> validatorsIndices) {
    return post(
        SEND_VALIDATOR_LIVENESS,
        Map.of("epoch", epoch.toString()),
        validatorsIndices,
        createHandler(PostValidatorLivenessResponse.class));
  }

  private ResponseHandler<Void> createHandler() {
    return createHandler(null);
  }

  private <T> ResponseHandler<T> createHandler(final Class<T> responseClass) {
    return new ResponseHandler<>(jsonProvider, responseClass);
  }

  private <T> Optional<T> get(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> queryParams,
      final ResponseHandler<T> responseHandler) {
    return get(apiMethod, EMPTY_MAP, queryParams, EMPTY_MAP, responseHandler);
  }

  private <T> Optional<T> get(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Map<String, String> queryParams,
      final Map<String, String> encodedQueryParams,
      final ResponseHandler<T> responseHandler) {
    final HttpUrl.Builder httpUrlBuilder = urlBuilder(apiMethod, urlParams);
    if (queryParams != null && !queryParams.isEmpty()) {
      queryParams.forEach(httpUrlBuilder::addQueryParameter);
    }
    // The encodedQueryParams are considered to be encoded already
    // and should not be encoded again. This is useful to prevent
    // the comma in an array of values (e.g. id=1,2,3) from being
    // encoded.
    if (encodedQueryParams != null && !encodedQueryParams.isEmpty()) {
      encodedQueryParams.forEach(httpUrlBuilder::addEncodedQueryParameter);
    }

    final Request request = requestBuilder().url(httpUrlBuilder.build()).build();
    return executeCall(request, responseHandler);
  }

  private <T> Optional<T> post(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Object requestBodyObj,
      final ResponseHandler<T> responseHandler) {
    final HttpUrl.Builder httpUrlBuilder = urlBuilder(apiMethod, urlParams);
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

  private <T> Optional<T> post(
      final ValidatorApiMethod apiMethod,
      final Object requestBodyObj,
      final ResponseHandler<T> responseHandler) {
    return post(apiMethod, EMPTY_MAP, requestBodyObj, responseHandler);
  }

  private HttpUrl.Builder urlBuilder(
      final ValidatorApiMethod apiMethod, final Map<String, String> urlParams) {
    return baseEndpoint.resolve(apiMethod.getPath(urlParams)).newBuilder();
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

  private String encodeQueryParam(final Object value) {
    try {
      return removeQuotesIfPresent(jsonProvider.objectToJSON(value));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          "Can't encode param of type " + value.getClass().getSimpleName(), e);
    }
  }

  private String removeQuotesIfPresent(final String value) {
    if (value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1);
    } else {
      return value;
    }
  }
}
