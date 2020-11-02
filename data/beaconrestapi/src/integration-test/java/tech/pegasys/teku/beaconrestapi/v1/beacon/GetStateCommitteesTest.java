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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static java.util.Collections.emptyMap;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.EpochCommitteeResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateCommitteesResponse;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateCommittees;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

public class GetStateCommitteesTest extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldGetCommitteesWithNoQueryParameters() throws IOException {
    final Response response = get("head", emptyMap());
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateCommitteesResponse body =
        jsonProvider.jsonToObject(response.body().string(), GetStateCommitteesResponse.class);
    final List<EpochCommitteeResponse> data = body.data;
    assertThat(data).isNotEmpty();
  }

  @Test
  public void shouldGetCommitteesForNextEpoch() throws IOException {
    final Response response = get("head", Map.of("epoch", "1"));
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateCommitteesResponse body =
        jsonProvider.jsonToObject(response.body().string(), GetStateCommitteesResponse.class);
    final List<EpochCommitteeResponse> data = body.data;
    data.forEach(
        committee -> {
          assertThat(committee.slot)
              .isGreaterThanOrEqualTo(UInt64.valueOf(Constants.SLOTS_PER_EPOCH));
          assertThat(committee.slot).isLessThan(UInt64.valueOf(Constants.SLOTS_PER_EPOCH * 2));
        });
    assertThat(data.size()).isEqualTo(8);
  }

  @Test
  public void shouldGetCommitteesForSingleIndex() throws IOException {
    final Response response = get("head", Map.of("index", "0"));
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateCommitteesResponse body =
        jsonProvider.jsonToObject(response.body().string(), GetStateCommitteesResponse.class);
    final List<EpochCommitteeResponse> data = body.data;
    data.forEach(committee -> assertThat(committee.index).isEqualTo(ZERO));
    assertThat(data.size()).isEqualTo(8);
  }

  @Test
  public void shouldGetCommitteesForSingleIndexAndSlotAndEpoch() throws IOException {
    final Response response = get("head", Map.of("index", "0", "slot", "9", "epoch", "1"));
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateCommitteesResponse body =
        jsonProvider.jsonToObject(response.body().string(), GetStateCommitteesResponse.class);
    final List<EpochCommitteeResponse> data = body.data;
    data.forEach(
        committee -> {
          assertThat(committee.index).isEqualTo(ZERO);
          assertThat(committee.slot).isEqualTo(UInt64.valueOf(9));
        });
    assertThat(data.size()).isEqualTo(1);
  }

  @Test
  public void shouldGetBadRequestIfSlotAndEpochDontAlign() throws IOException {
    final Response response = get("head", Map.of("slot", "1", "epoch", "1"));
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    final BadRequest body = jsonProvider.jsonToObject(response.body().string(), BadRequest.class);
    assertThat(body.getStatus()).isEqualTo(SC_BAD_REQUEST);
    assertThat(body.getMessage()).isEqualToIgnoringCase("Slot 1 is not in epoch 1");
  }

  @Test
  public void shouldGetBadRequestIfEpochTooFarInFuture() throws IOException {
    final Response response = get("head", Map.of("epoch", "1024000"));
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    final BadRequest body = jsonProvider.jsonToObject(response.body().string(), BadRequest.class);
    assertThat(body.getStatus()).isEqualTo(SC_BAD_REQUEST);
    assertThat(body.getMessage()).startsWith("Epoch 1024000 is too far ahead ");
  }

  public void queryFiltersOutResults(final String queryParam, final String queryParamValue)
      throws IOException {
    final Response response = get("head", Map.of("index", "1024000"));
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateCommitteesResponse body =
        jsonProvider.jsonToObject(response.body().string(), GetStateCommitteesResponse.class);
    final List<EpochCommitteeResponse> data = body.data;
    assertThat(data).isEmpty();
  }

  public Response get(final String stateIdString, final Map<String, String> query)
      throws IOException {
    return getResponse(GetStateCommittees.ROUTE.replace(":state_id", stateIdString), query);
  }
}
