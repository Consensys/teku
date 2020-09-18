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

import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_AGGREGATE;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_ATTESTATION_DUTIES;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_DUTIES;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_FORK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_PROPOSER_DUTIES;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_UNSIGNED_ATTESTATION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_UNSIGNED_BLOCK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_AGGREGATE_AND_PROOF;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_ATTESTATION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_BLOCK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SUBSCRIBE_TO_COMMITTEE_FOR_AGGREGATION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SUBSCRIBE_TO_PERSISTENT_SUBNETS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.request.SubscribeToBeaconCommitteeRequest;
import tech.pegasys.teku.api.response.GetForkResponse;
import tech.pegasys.teku.api.response.v1.validator.AttesterDuty;
import tech.pegasys.teku.api.response.v1.validator.GetAttesterDutiesResponse;
import tech.pegasys.teku.api.response.v1.validator.GetProposerDutiesResponse;
import tech.pegasys.teku.api.response.v1.validator.ProposerDuty;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.SignedAggregateAndProof;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.SubnetSubscription;
import tech.pegasys.teku.api.schema.ValidatorDuties;
import tech.pegasys.teku.api.schema.ValidatorDutiesRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class OkHttpValidatorRestApiClient implements ValidatorRestApiClient {

  private static final Logger LOG = LogManager.getLogger();

  private static final MediaType APPLICATION_JSON =
      MediaType.parse("application/json; charset=utf-8");
  private static final Map<String, String> EMPTY_QUERY_PARAMS = Collections.emptyMap();

  private final JsonProvider jsonProvider = new JsonProvider();
  private final OkHttpClient httpClient;
  private final HttpUrl baseEndpoint;

  public OkHttpValidatorRestApiClient(final String baseEndpoint) {
    this(HttpUrl.parse(baseEndpoint), new OkHttpClient());
  }

  @VisibleForTesting
  OkHttpValidatorRestApiClient(final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    this.baseEndpoint = baseEndpoint;
    this.httpClient = okHttpClient;
  }

  @Override
  public Optional<GetForkResponse> getFork() {
    return get(GET_FORK, EMPTY_QUERY_PARAMS, GetForkResponse.class);
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
    return get(
            GET_ATTESTATION_DUTIES,
            Map.of("index", validatorIndexes.toString()),
            GetAttesterDutiesResponse.class)
        .map(response -> response.data)
        .orElse(Collections.emptyList());
  }

  @Override
  public List<ProposerDuty> getProposerDuties(final UInt64 epoch) {
    return get(GET_PROPOSER_DUTIES, Collections.emptyMap(), GetProposerDutiesResponse.class)
        .map(response -> response.data)
        .orElse(Collections.emptyList());
  }

  @Override
  public Optional<BeaconBlock> createUnsignedBlock(
      final UInt64 slot, final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("slot", encodeQueryParam(slot));
    queryParams.put("randao_reveal", encodeQueryParam(randaoReveal));
    graffiti.ifPresent(bytes32 -> queryParams.put("graffiti", encodeQueryParam(bytes32)));

    return get(GET_UNSIGNED_BLOCK, queryParams, BeaconBlock.class);
  }

  @Override
  public void sendSignedBlock(final SignedBeaconBlock beaconBlock) {
    post(SEND_SIGNED_BLOCK, beaconBlock, null);
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
  public void sendSignedAttestation(final Attestation attestation) {
    post(SEND_SIGNED_ATTESTATION, attestation, null);
  }

  @Override
  public Optional<Attestation> createAggregate(final Bytes32 attestationHashTreeRoot) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("slot", encodeQueryParam(UInt64.ZERO));
    queryParams.put("attestation_data_root", encodeQueryParam(attestationHashTreeRoot));

    return get(GET_AGGREGATE, queryParams, Attestation.class);
  }

  @Override
  public void sendAggregateAndProof(final SignedAggregateAndProof signedAggregateAndProof) {
    post(SEND_SIGNED_AGGREGATE_AND_PROOF, signedAggregateAndProof, null);
  }

  @Override
  public void subscribeToBeaconCommitteeForAggregation(
      final int committeeIndex, final UInt64 aggregationSlot) {
    final SubscribeToBeaconCommitteeRequest request =
        new SubscribeToBeaconCommitteeRequest(committeeIndex, aggregationSlot);
    post(SUBSCRIBE_TO_COMMITTEE_FOR_AGGREGATION, request, null);
  }

  @Override
  public void subscribeToPersistentSubnets(final Set<SubnetSubscription> subnetSubscriptions) {
    post(SUBSCRIBE_TO_PERSISTENT_SUBNETS, subnetSubscriptions, null);
  }

  public <T> Optional<T> get(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> queryParams,
      final Class<T> responseClass) {
    final HttpUrl.Builder httpUrlBuilder = urlBuilder(apiMethod);
    if (queryParams != null && !queryParams.isEmpty()) {
      queryParams.forEach(httpUrlBuilder::addQueryParameter);
    }

    final Request request = new Request.Builder().url(httpUrlBuilder.build()).build();
    return executeCall(request, responseClass);
  }

  private <T> Optional<T> post(
      final ValidatorApiMethod apiMethod,
      final Object requestBodyObj,
      final Class<T> responseClass) {
    final String requestBody;
    try {
      requestBody = jsonProvider.objectToJSON(requestBodyObj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    final Request request =
        new Request.Builder()
            .url(urlBuilder(apiMethod).build())
            .post(RequestBody.create(requestBody, APPLICATION_JSON))
            .build();

    return executeCall(request, responseClass);
  }

  private HttpUrl.Builder urlBuilder(final ValidatorApiMethod apiMethod) {
    return baseEndpoint.resolve(apiMethod.getPath()).newBuilder();
  }

  private <T> Optional<T> executeCall(final Request request, final Class<T> responseClass) {
    try (final Response response = httpClient.newCall(request).execute()) {
      LOG.trace("{} {} {}", request.method(), request.url(), response.code());

      switch (response.code()) {
        case 200:
          {
            final String responseBody = response.body().string();
            if (responseClass != null) {
              final T responseObj = jsonProvider.jsonToObject(responseBody, responseClass);
              return Optional.of(responseObj);
            } else {
              return Optional.empty();
            }
          }
        case 202:
        case 204:
        case 404:
        case 503:
          {
            return Optional.empty();
          }
        case 400:
          {
            LOG.error(
                "Invalid params response from Beacon Node API - {}", response.body().string());
            return Optional.empty();
          }
        default:
          {
            final String responseBody = response.body().string();
            LOG.error(
                "Unexpected error calling Beacon Node API (status = {}, response = {})",
                response.code(),
                responseBody);
            throw new RuntimeException(
                "Unexpected response from Beacon Node API (status = "
                    + response.code()
                    + ", response = "
                    + responseBody
                    + ")");
          }
      }
    } catch (IOException e) {
      LOG.error("Error communicating with Beacon Node API", e);
      throw new RuntimeException(e);
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
