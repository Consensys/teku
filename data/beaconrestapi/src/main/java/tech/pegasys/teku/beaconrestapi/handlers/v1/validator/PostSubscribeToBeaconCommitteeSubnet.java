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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.request.v1.validator.BeaconCommitteeSubscriptionRequest;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;

public class PostSubscribeToBeaconCommitteeSubnet extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/validator/beacon_committee_subscriptions";
  private final ValidatorDataProvider provider;

  private static final DeserializableTypeDefinition<CommitteeSubscriptionData>
      COMMITTEE_SUBSCRIPTION_REQUEST_TYPE =
          DeserializableTypeDefinition.object(CommitteeSubscriptionData.class)
              .name("CommitteeSubscriptionData")
              .initializer(CommitteeSubscriptionData::new)
              .withField(
                  "validator_index",
                  STRING_INTEGER_TYPE,
                  CommitteeSubscriptionData::getValidatorIndex,
                  CommitteeSubscriptionData::setValidatorIndex)
              .withField(
                  "committee_index",
                  STRING_INTEGER_TYPE,
                  CommitteeSubscriptionData::getCommitteeIndex,
                  CommitteeSubscriptionData::setCommitteeIndex)
              .withField(
                  "committees_at_slot",
                  UINT64_TYPE,
                  CommitteeSubscriptionData::getCommitteesAtSlot,
                  CommitteeSubscriptionData::setCommitteesAtSlot)
              .withField(
                  "slot",
                  UINT64_TYPE,
                  CommitteeSubscriptionData::getSlot,
                  CommitteeSubscriptionData::setSlot)
              .withField(
                  "is_aggregator",
                  BOOLEAN_TYPE,
                  CommitteeSubscriptionData::isAggregator,
                  CommitteeSubscriptionData::setAggregator)
              .build();

  public PostSubscribeToBeaconCommitteeSubnet(final DataProvider dataProvider) {
    this(dataProvider.getValidatorDataProvider());
  }

  public PostSubscribeToBeaconCommitteeSubnet(final ValidatorDataProvider provider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("postSubscribeToBeaconCommitteeSubnet")
            .summary("Subscribe to a committee subnet")
            .description(
                "After Beacon node receives this request, search using discv5 for peers related to this subnet and replace current peers with those ones if necessary If validator is_aggregator, beacon node must:\n"
                    + "- announce subnet topic subscription on gossipsub\n"
                    + "- aggregate attestations received on that subnet\n")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(
                DeserializableTypeDefinition.listOf(COMMITTEE_SUBSCRIPTION_REQUEST_TYPE))
            .response(
                HttpStatusCodes.SC_OK,
                "Slot signature is valid and beacon node has prepared the attestation subnet. Note that, there is no guarantee the node will find peers for the subnet")
            .withServiceUnavailableResponse()
            .build());
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Subscribe to a committee subnet",
      tags = {TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      requestBody =
          @OpenApiRequestBody(
              content = {@OpenApiContent(from = BeaconCommitteeSubscriptionRequest[].class)}),
      description =
          "After Beacon node receives this request, search using discv5 for peers related to this subnet and replace current peers with those ones if necessary If validator is_aggregator, beacon node must:\n"
              + "- announce subnet topic subscription on gossipsub\n"
              + "- aggregate attestations received on that subnet\n",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description =
                "Slot signature is valid and beacon node has prepared the attestation subnet. Note that, there is no guarantee the node will find peers for the subnet"),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid request syntax."),
        @OpenApiResponse(status = RES_INTERNAL_ERROR, description = "Beacon node internal error."),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final List<CommitteeSubscriptionData> requestBody = request.getRequestBody();

    provider.subscribeToBeaconCommittee(
        requestBody.stream()
            .map(CommitteeSubscriptionData::toCommitteeSubscriptionRequest)
            .collect(Collectors.toList()));
    request.respondWithCode(SC_OK);
  }

  static class CommitteeSubscriptionData {
    private int validatorIndex;
    private int committeeIndex;
    private UInt64 committeesAtSlot;
    private UInt64 slot;
    private boolean isAggregator;

    CommitteeSubscriptionData() {}

    CommitteeSubscriptionData(
        int validatorIndex,
        int committeeIndex,
        UInt64 committeesAtSlot,
        UInt64 slot,
        boolean isAggregator) {
      this.validatorIndex = validatorIndex;
      this.committeeIndex = committeeIndex;
      this.committeesAtSlot = committeesAtSlot;
      this.slot = slot;
      this.isAggregator = isAggregator;
    }

    public CommitteeSubscriptionRequest toCommitteeSubscriptionRequest() {
      return new CommitteeSubscriptionRequest(
          validatorIndex, committeeIndex, committeesAtSlot, slot, isAggregator);
    }

    public int getValidatorIndex() {
      return validatorIndex;
    }

    public void setValidatorIndex(int validatorIndex) {
      this.validatorIndex = validatorIndex;
    }

    public int getCommitteeIndex() {
      return committeeIndex;
    }

    public void setCommitteeIndex(int committeeIndex) {
      this.committeeIndex = committeeIndex;
    }

    public UInt64 getCommitteesAtSlot() {
      return committeesAtSlot;
    }

    public void setCommitteesAtSlot(UInt64 committeesAtSlot) {
      this.committeesAtSlot = committeesAtSlot;
    }

    public UInt64 getSlot() {
      return slot;
    }

    public void setSlot(UInt64 slot) {
      this.slot = slot;
    }

    public boolean isAggregator() {
      return isAggregator;
    }

    public void setAggregator(boolean aggregator) {
      isAggregator = aggregator;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CommitteeSubscriptionData that = (CommitteeSubscriptionData) o;
      return validatorIndex == that.validatorIndex
          && committeeIndex == that.committeeIndex
          && isAggregator == that.isAggregator
          && Objects.equals(committeesAtSlot, that.committeesAtSlot)
          && Objects.equals(slot, that.slot);
    }

    @Override
    public int hashCode() {
      return Objects.hash(validatorIndex, committeeIndex, committeesAtSlot, slot, isAggregator);
    }

    @Override
    public String toString() {
      return "CommitteeSubscriptionData{"
          + "validatorIndex="
          + validatorIndex
          + ", committeeIndex="
          + committeeIndex
          + ", committeesAtSlot="
          + committeesAtSlot
          + ", slot="
          + slot
          + ", isAggregator="
          + isAggregator
          + '}';
    }
  }
}
