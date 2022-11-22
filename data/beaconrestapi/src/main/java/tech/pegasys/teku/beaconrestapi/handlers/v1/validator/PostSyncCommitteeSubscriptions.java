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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;

public class PostSyncCommitteeSubscriptions extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/sync_committee_subscriptions";
  private final ValidatorDataProvider provider;

  static final DeserializableTypeDefinition<PostSyncCommitteeData> REQUEST_TYPE =
      DeserializableTypeDefinition.object(PostSyncCommitteeData.class)
          .name("PostSyncCommitteeData")
          .initializer(PostSyncCommitteeData::new)
          .withField(
              "validator_index",
              INTEGER_TYPE,
              PostSyncCommitteeData::getValidatorIndex,
              PostSyncCommitteeData::setValidatorIndex)
          .withField(
              "sync_committee_indices",
              DeserializableTypeDefinition.listOf(INTEGER_TYPE),
              PostSyncCommitteeData::getSyncCommitteeIndices,
              PostSyncCommitteeData::setSyncCommitteeIndices)
          .withField(
              "until_epoch",
              UINT64_TYPE,
              PostSyncCommitteeData::getUntilEpoch,
              PostSyncCommitteeData::setUntilEpoch)
          .build();

  public PostSyncCommitteeSubscriptions(final DataProvider dataProvider) {
    this(dataProvider.getValidatorDataProvider());
  }

  public PostSyncCommitteeSubscriptions(final ValidatorDataProvider validatorDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("postSyncCommitteeSubscriptions")
            .summary("Subscribe to a Sync committee subnet")
            .description(
                "Subscribe to a number of sync committee subnets\n\n"
                    + "Sync committees are not present in phase0, but are required for Altair networks.\n\n"
                    + "Subscribing to sync committee subnets is an action performed by VC to enable network participation in Altair networks, and only required if the VC has an active validator in an active sync committee.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(DeserializableTypeDefinition.listOf(REQUEST_TYPE))
            .response(SC_OK, "Successful response")
            .build());
    this.provider = validatorDataProvider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final List<PostSyncCommitteeData> requestData = request.getRequestBody();
    final List<SyncCommitteeSubnetSubscription> subscriptions =
        requestData.stream()
            .map(PostSyncCommitteeData::toSyncCommitteeSubnetSubscription)
            .collect(Collectors.toList());
    request.respondAsync(
        provider
            .subscribeToSyncCommitteeSubnets(subscriptions)
            .thenApply(AsyncApiResponse::respondOk));
  }

  public static class PostSyncCommitteeData {
    private int validatorIndex;
    private IntSet syncCommitteeIndices;
    private UInt64 untilEpoch;

    public PostSyncCommitteeData() {}

    public PostSyncCommitteeData(
        final int validatorIndex, final IntSet syncCommitteeIndices, final UInt64 untilEpoch) {
      this.validatorIndex = validatorIndex;
      this.syncCommitteeIndices = syncCommitteeIndices;
      this.untilEpoch = untilEpoch;
    }

    public SyncCommitteeSubnetSubscription toSyncCommitteeSubnetSubscription() {
      return new SyncCommitteeSubnetSubscription(validatorIndex, syncCommitteeIndices, untilEpoch);
    }

    public int getValidatorIndex() {
      return validatorIndex;
    }

    public void setValidatorIndex(int validatorIndex) {
      this.validatorIndex = validatorIndex;
    }

    public List<Integer> getSyncCommitteeIndices() {
      return new IntArrayList(syncCommitteeIndices);
    }

    public void setSyncCommitteeIndices(List<Integer> syncCommitteeIndices) {
      this.syncCommitteeIndices = new IntOpenHashSet(syncCommitteeIndices);
    }

    public UInt64 getUntilEpoch() {
      return untilEpoch;
    }

    public void setUntilEpoch(UInt64 untilEpoch) {
      this.untilEpoch = untilEpoch;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      PostSyncCommitteeData that = (PostSyncCommitteeData) o;
      return validatorIndex == that.validatorIndex
          && Objects.equals(syncCommitteeIndices, that.syncCommitteeIndices)
          && Objects.equals(untilEpoch, that.untilEpoch);
    }

    @Override
    public int hashCode() {
      return Objects.hash(validatorIndex, syncCommitteeIndices, untilEpoch);
    }

    @Override
    public String toString() {
      return "PostSyncCommitteeData{"
          + "validatorIndex="
          + validatorIndex
          + ", syncCommitteeIndices="
          + syncCommitteeIndices
          + ", untilEpoch="
          + untilEpoch
          + '}';
    }
  }
}
