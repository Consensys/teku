/*
 * Copyright 2021 ConsenSys AG.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeMessage;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostSyncCommittees;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostSyncCommitteesIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  private final String errorString = "The Error Description";

  @Test
  void shouldSubmitSyncCommitteesAndGetResponse() throws IOException {
    spec = TestSpecFactory.createMinimalAltair();
    DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final List<SyncCommitteeMessage> requestBody =
        List.of(
            new SyncCommitteeMessage(
                UInt64.ONE,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomUInt64(),
                new BLSSignature(dataStructureUtil.randomSignature())));
    final SafeFuture<List<SubmitDataError>> future =
        SafeFuture.completedFuture(List.of(new SubmitDataError(UInt64.ZERO, errorString)));
    when(validatorApiChannel.sendSyncCommitteeMessages(
            requestBody.get(0).asInternalCommitteeSignature(spec).stream()
                .collect(Collectors.toList())))
        .thenReturn(future);
    Response response = post(PostSyncCommittees.ROUTE, jsonProvider.objectToJSON(requestBody));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    final PostDataFailureResponse responseBody =
        jsonProvider.jsonToObject(response.body().string(), PostDataFailureResponse.class);

    assertThat(responseBody.failures.get(0).message).isEqualTo(errorString);
  }

  @Test
  void shouldGet200OkWhenThereAreNoErrors() throws Exception {
    spec = TestSpecFactory.createMinimalAltair();
    DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final List<SyncCommitteeMessage> requestBody =
        List.of(
            new SyncCommitteeMessage(
                UInt64.ONE,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomUInt64(),
                new BLSSignature(dataStructureUtil.randomSignature())));
    final SafeFuture<List<SubmitDataError>> future =
        SafeFuture.completedFuture(Collections.emptyList());
    when(validatorApiChannel.sendSyncCommitteeMessages(any())).thenReturn(future);
    Response response = post(PostSyncCommittees.ROUTE, jsonProvider.objectToJSON(requestBody));

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  @Test
  void phase0SlotCausesBadRequest() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    spec = TestSpecFactory.createMinimalPhase0();
    DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final List<SyncCommitteeMessage> requestBody =
        List.of(
            new SyncCommitteeMessage(
                UInt64.ONE,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomUInt64(),
                new BLSSignature(dataStructureUtil.randomSignature())));
    Response response = post(PostSyncCommittees.ROUTE, jsonProvider.objectToJSON(requestBody));
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string())
        .contains("Could not create sync committee signature at phase0 slot 1");
  }
}
