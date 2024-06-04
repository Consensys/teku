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
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_BLOCK_HEADER;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_GENESIS;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_PROPOSER_DUTIES;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_SYNC_COMMITTEE_CONTRIBUTION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_VALIDATORS;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.PREPARE_BEACON_PROPOSER;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_CONTRIBUTION_AND_PROOF;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_AGGREGATE_AND_PROOF;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_ATTESTATION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_VOLUNTARY_EXIT;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SYNC_COMMITTEE_MESSAGES;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_VALIDATOR_LIVENESS;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SUBSCRIBE_TO_BEACON_COMMITTEE_SUBNET;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SUBSCRIBE_TO_PERSISTENT_SUBNETS;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SUBSCRIBE_TO_SYNC_COMMITTEE_SUBNET;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
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
import tech.pegasys.teku.api.response.v1.beacon.GetBlockHeaderResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetGenesisResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorsResponse;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.validator.GetAggregatedAttestationResponse;
import tech.pegasys.teku.api.response.v1.validator.GetProposerDutiesResponse;
import tech.pegasys.teku.api.response.v1.validator.GetSyncCommitteeContributionResponse;
import tech.pegasys.teku.api.response.v1.validator.PostValidatorLivenessResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.SignedAggregateAndProof;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.api.schema.SubnetSubscription;
import tech.pegasys.teku.api.schema.altair.SignedContributionAndProof;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeContribution;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeMessage;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.api.schema.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;

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
  public Optional<GetGenesisResponse> getGenesis() {
    return get(GET_GENESIS, EMPTY_MAP, createHandler(GetGenesisResponse.class));
  }

  public Optional<GetBlockHeaderResponse> getBlockHeader(final String blockId) {
    return get(
        GET_BLOCK_HEADER,
        Map.of("block_id", blockId),
        EMPTY_MAP,
        EMPTY_MAP,
        createHandler(GetBlockHeaderResponse.class));
  }

  /**
   * <a
   * href="https://ethereum.github.io/beacon-APIs/?urls.primaryName=dev#/Beacon/getStateValidators">GET
   * Get validators from state</a>
   */
  @Override
  public Optional<List<ValidatorResponse>> getValidators(final List<String> validatorIds) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("id", String.join(",", validatorIds));
    return get(
            GET_VALIDATORS,
            EMPTY_MAP,
            EMPTY_MAP,
            queryParams,
            createHandler(GetStateValidatorsResponse.class))
        .map(response -> response.data);
  }

  @Override
  public Optional<GetProposerDutiesResponse> getProposerDuties(final UInt64 epoch) {
    return get(
        GET_PROPOSER_DUTIES,
        Map.of("epoch", epoch.toString()),
        EMPTY_MAP,
        EMPTY_MAP,
        createHandler(GetProposerDutiesResponse.class));
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
  public Optional<PostDataFailureResponse> sendVoluntaryExit(
      final SignedVoluntaryExit voluntaryExit) {
    return post(
        SEND_SIGNED_VOLUNTARY_EXIT,
        voluntaryExit,
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
  public void subscribeToBeaconCommittee(final List<CommitteeSubscriptionRequest> requests) {
    final BeaconCommitteeSubscriptionRequest[] body =
        requests.stream()
            .map(
                request ->
                    new BeaconCommitteeSubscriptionRequest(
                        String.valueOf(request.getValidatorIndex()),
                        String.valueOf(request.getCommitteeIndex()),
                        request.getCommitteesAtSlot(),
                        request.getSlot(),
                        request.isAggregator()))
            .toArray(BeaconCommitteeSubscriptionRequest[]::new);
    post(SUBSCRIBE_TO_BEACON_COMMITTEE_SUBNET, body, createHandler());
  }

  @Override
  public void subscribeToPersistentSubnets(final Set<SubnetSubscription> subnetSubscriptions) {
    post(SUBSCRIBE_TO_PERSISTENT_SUBNETS, subnetSubscriptions, createHandler());
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
  public void subscribeToSyncCommitteeSubnets(
      final List<SyncCommitteeSubnetSubscription> subnetSubscriptions) {
    post(SUBSCRIBE_TO_SYNC_COMMITTEE_SUBNET, subnetSubscriptions, createHandler());
  }

  @Override
  public void sendContributionAndProofs(
      final List<SignedContributionAndProof> signedContributionAndProofs) {
    post(SEND_CONTRIBUTION_AND_PROOF, signedContributionAndProofs, createHandler());
  }

  @Override
  public Optional<SyncCommitteeContribution> createSyncCommitteeContribution(
      final UInt64 slot, final int subcommitteeIndex, final Bytes32 beaconBlockRoot) {
    final Map<String, String> pathParams = Map.of();
    final Map<String, String> queryParams =
        Map.of(
            "slot",
            slot.toString(),
            "subcommittee_index",
            Integer.toString(subcommitteeIndex),
            "beacon_block_root",
            beaconBlockRoot.toHexString());
    return get(
            GET_SYNC_COMMITTEE_CONTRIBUTION,
            pathParams,
            queryParams,
            EMPTY_MAP,
            createHandler(GetSyncCommitteeContributionResponse.class)
                .withHandler(SC_NOT_FOUND, (request, response) -> Optional.empty()))
        .map(response -> response.data);
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

  public <T> Optional<T> get(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> queryParams,
      final ResponseHandler<T> responseHandler) {
    return get(apiMethod, EMPTY_MAP, queryParams, EMPTY_MAP, responseHandler);
  }

  public <T> Optional<T> get(
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

  public URI getBaseEndpoint() {
    return baseEndpoint.uri();
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
