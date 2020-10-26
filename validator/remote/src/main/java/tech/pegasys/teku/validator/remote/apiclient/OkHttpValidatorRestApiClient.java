/*
 * Copyright 2020 ConsenSys AG.
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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_TOO_MANY_REQUESTS;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_AGGREGATE;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_ATTESTATION_DATA;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_ATTESTATION_DUTIES;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_DUTIES;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_FORK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_GENESIS;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_PROPOSER_DUTIES;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_UNSIGNED_ATTESTATION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_UNSIGNED_BLOCK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_VALIDATORS;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_AGGREGATE_AND_PROOF;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_ATTESTATION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_BLOCK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SUBSCRIBE_TO_BEACON_COMMITTEE_SUBNET;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SUBSCRIBE_TO_PERSISTENT_SUBNETS;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import tech.pegasys.teku.api.request.v1.validator.BeaconCommitteeSubscriptionRequest;
import tech.pegasys.teku.api.response.v1.beacon.GetGenesisResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateForkResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorsResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.validator.AttesterDuty;
import tech.pegasys.teku.api.response.v1.validator.GetAggregatedAttestationResponse;
import tech.pegasys.teku.api.response.v1.validator.GetAttesterDutiesResponse;
import tech.pegasys.teku.api.response.v1.validator.GetNewBlockResponse;
import tech.pegasys.teku.api.response.v1.validator.GetProposerDutiesResponse;
import tech.pegasys.teku.api.response.v1.validator.ProposerDuty;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.AttestationData;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.SignedAggregateAndProof;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.SubnetSubscription;
import tech.pegasys.teku.api.schema.TemporaryAttestationData;
import tech.pegasys.teku.api.schema.ValidatorDuties;
import tech.pegasys.teku.api.schema.ValidatorDutiesRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

public class OkHttpValidatorRestApiClient implements ValidatorRestApiClient {

  private static final Logger LOG = LogManager.getLogger();

  private static final MediaType APPLICATION_JSON =
      MediaType.parse("application/json; charset=utf-8");
  private static final Map<String, String> EMPTY_QUERY_PARAMS = emptyMap();

  private final JsonProvider jsonProvider = new JsonProvider();
  private final OkHttpClient httpClient;
  private final HttpUrl baseEndpoint;

  public OkHttpValidatorRestApiClient(final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    this.baseEndpoint = baseEndpoint;
    this.httpClient = okHttpClient;
  }

  @Override
  public Optional<Fork> getFork() {
    return get(GET_FORK, Map.of("state_id", "head"), EMPTY_QUERY_PARAMS, GetStateForkResponse.class)
        .map(GetStateForkResponse::getData);
  }

  @Override
  public Optional<GetGenesisResponse> getGenesis() {
    return get(GET_GENESIS, EMPTY_QUERY_PARAMS, GetGenesisResponse.class);
  }

  @Override
  public Optional<List<ValidatorResponse>> getValidators(final List<String> validatorIds) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("id", String.join(",", validatorIds));
    return get(GET_VALIDATORS, queryParams, GetStateValidatorsResponse.class)
        .map(response -> response.data);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ValidatorDuties> getDuties(final ValidatorDutiesRequest request) {
    return post(GET_DUTIES, request, ValidatorDuties[].class)
        .map(Arrays::asList)
        .orElse(Collections.EMPTY_LIST);
  }

  @Override
  public List<AttesterDuty> getAttestationDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndexes) {
    return post(
            GET_ATTESTATION_DUTIES,
            Map.of("epoch", epoch.toString()),
            validatorIndexes.toArray(),
            GetAttesterDutiesResponse.class)
        .map(response -> response.data)
        .orElse(Collections.emptyList());
  }

  @Override
  public List<ProposerDuty> getProposerDuties(final UInt64 epoch) {
    return get(
            GET_PROPOSER_DUTIES,
            Map.of("epoch", epoch.toString()),
            emptyMap(),
            GetProposerDutiesResponse.class)
        .map(response -> response.data)
        .orElse(Collections.emptyList());
  }

  @Override
  public Optional<BeaconBlock> createUnsignedBlock(
      final UInt64 slot, final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("randao_reveal", encodeQueryParam(randaoReveal));
    graffiti.ifPresent(bytes32 -> queryParams.put("graffiti", encodeQueryParam(bytes32)));

    return get(
            GET_UNSIGNED_BLOCK,
            Map.of("slot", slot.toString()),
            queryParams,
            GetNewBlockResponse.class)
        .map(response -> response.data);
  }

  @Override
  public SendSignedBlockResult sendSignedBlock(final SignedBeaconBlock beaconBlock) {
    return post(SEND_SIGNED_BLOCK, beaconBlock, String.class)
        .map(__ -> SendSignedBlockResult.success(Bytes32.ZERO))
        .orElseGet(() -> SendSignedBlockResult.notImported("UNKNOWN"));
  }

  @Override
  public Optional<Attestation> createUnsignedAttestation(
      final UInt64 slot, final int committeeIndex) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("slot", encodeQueryParam(slot));
    queryParams.put("committee_index", String.valueOf(committeeIndex));

    return get(GET_UNSIGNED_ATTESTATION, queryParams, Attestation.class);
  }

  @Override
  public Optional<AttestationData> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("slot", encodeQueryParam(slot));
    queryParams.put("committee_index", String.valueOf(committeeIndex));

    return get(GET_ATTESTATION_DATA, queryParams, TemporaryAttestationData.class)
        .map(TemporaryAttestationData::getAttestationData);
  }

  @Override
  public void sendSignedAttestation(final Attestation attestation) {
    post(SEND_SIGNED_ATTESTATION, attestation, null);
  }

  @Override
  public Optional<Attestation> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("slot", encodeQueryParam(slot));
    queryParams.put("attestation_data_root", encodeQueryParam(attestationHashTreeRoot));

    return get(GET_AGGREGATE, queryParams, GetAggregatedAttestationResponse.class)
        .map(result -> result.data);
  }

  @Override
  public void sendAggregateAndProofs(final List<SignedAggregateAndProof> signedAggregateAndProof) {
    post(SEND_SIGNED_AGGREGATE_AND_PROOF, signedAggregateAndProof, null);
  }

  @Override
  public void subscribeToBeaconCommittee(List<CommitteeSubscriptionRequest> requests) {
    final BeaconCommitteeSubscriptionRequest[] body =
        requests.stream()
            .map(
                request ->
                    new BeaconCommitteeSubscriptionRequest(
                        request.getValidatorIndex(),
                        request.getCommitteeIndex(),
                        request.getCommitteesAtSlot(),
                        request.getSlot(),
                        request.isAggregator()))
            .toArray(BeaconCommitteeSubscriptionRequest[]::new);
    post(SUBSCRIBE_TO_BEACON_COMMITTEE_SUBNET, body, null);
  }

  @Override
  public void subscribeToPersistentSubnets(final Set<SubnetSubscription> subnetSubscriptions) {
    post(SUBSCRIBE_TO_PERSISTENT_SUBNETS, subnetSubscriptions, null);
  }

  public <T> Optional<T> get(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> queryParams,
      final Class<T> responseClass) {
    return get(apiMethod, emptyMap(), queryParams, responseClass);
  }

  public <T> Optional<T> get(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Map<String, String> queryParams,
      final Class<T> responseClass) {
    final HttpUrl.Builder httpUrlBuilder = urlBuilder(apiMethod, urlParams);
    if (queryParams != null && !queryParams.isEmpty()) {
      queryParams.forEach(httpUrlBuilder::addQueryParameter);
    }

    final Request request = requestBuilder().url(httpUrlBuilder.build()).build();
    return executeCall(request, responseClass);
  }

  private <T> Optional<T> post(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Object requestBodyObj,
      final Class<T> responseClass) {
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

    return executeCall(request, responseClass);
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
      final Class<T> responseClass) {
    return post(apiMethod, Collections.emptyMap(), requestBodyObj, responseClass);
  }

  private HttpUrl.Builder urlBuilder(
      final ValidatorApiMethod apiMethod, final Map<String, String> urlParams) {
    return baseEndpoint.resolve(apiMethod.getPath(urlParams)).newBuilder();
  }

  private <T> Optional<T> executeCall(final Request request, final Class<T> responseClass) {
    try (final Response response = httpClient.newCall(request).execute()) {
      LOG.trace("{} {} {}", request.method(), request.url(), response.code());

      switch (response.code()) {
        case SC_OK:
          {
            if (responseClass != null) {
              final T responseObj =
                  jsonProvider.jsonToObject(response.body().string(), responseClass);
              return Optional.of(responseObj);
            } else {
              return Optional.empty();
            }
          }
        case SC_ACCEPTED:
        case SC_NO_CONTENT:
        case SC_SERVICE_UNAVAILABLE:
          return Optional.empty();

        case SC_BAD_REQUEST:
          {
            throw new IllegalArgumentException(
                "Invalid params response from Beacon Node API (url = "
                    + request.url()
                    + ", response = "
                    + response.body().string()
                    + ")");
          }
        case SC_TOO_MANY_REQUESTS:
          throw new RateLimitedException(request.url().toString());
        default:
          throw new RuntimeException(
              String.format(
                  "Unexpected response from Beacon Node API (url = %s, status = %s, response = %s)",
                  request.url(), response.code(), response.body().string()));
      }
    } catch (IOException e) {
      throw new RuntimeException("Error communicating with Beacon Node API: " + e.getMessage(), e);
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
