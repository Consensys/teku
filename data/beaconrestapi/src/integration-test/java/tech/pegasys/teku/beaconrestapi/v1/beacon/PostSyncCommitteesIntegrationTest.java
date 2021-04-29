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
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.PostSyncCommitteeFailureResponse;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeSignature;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostSyncCommittees;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SubmitCommitteeSignatureError;
import tech.pegasys.teku.validator.api.SubmitCommitteeSignaturesResult;

public class PostSyncCommitteesIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  private final String errorString = "The Error Description";

  @Test
  void shouldSubmitSyncCommitteesAndGetResponse() throws IOException {
    spec = TestSpecFactory.createMinimalAltair();
    DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final List<SyncCommitteeSignature> requestBody =
        List.of(
            new SyncCommitteeSignature(
                UInt64.ONE,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomUInt64(),
                new BLSSignature(dataStructureUtil.randomSignature())));
    final SafeFuture<Optional<SubmitCommitteeSignaturesResult>> future =
        SafeFuture.completedFuture(
            Optional.of(
                new SubmitCommitteeSignaturesResult(
                    List.of(new SubmitCommitteeSignatureError(UInt64.ZERO, errorString)))));
    when(validatorApiChannel.sendSyncCommitteeSignatures(
            List.of(requestBody.get(0).asInternalCommitteeSignature())))
        .thenReturn(future);
    Response response = post(PostSyncCommittees.ROUTE, jsonProvider.objectToJSON(requestBody));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    final PostSyncCommitteeFailureResponse responseBody =
        jsonProvider.jsonToObject(response.body().string(), PostSyncCommitteeFailureResponse.class);

    assertThat(responseBody.failures.get(0).message).isEqualTo(errorString);
  }
}
