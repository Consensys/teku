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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.capella.SignedBlsToExecutionChange;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostBlsToExecutionChanges;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class PostBlsToExecutionChangesIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  public void beforeEach() {
    spec = TestSpecFactory.createMinimalCapella();
    dataStructureUtil = new DataStructureUtil(spec);
    startRestAPIAtGenesis(SpecMilestone.CAPELLA);
  }

  @Test
  void postValidBlsToExecutionReturnsOk() throws IOException {
    final List<SignedBlsToExecutionChange> requestBody =
        List.of(
            new SignedBlsToExecutionChange(dataStructureUtil.randomSignedBlsToExecutionChange()));

    when(blsToExecutionChangePool.addLocal(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    Response response =
        post(PostBlsToExecutionChanges.ROUTE, jsonProvider.objectToJSON(requestBody));

    assertThat(response.code()).isEqualTo(SC_OK);
  }

  @Test
  void postInvalidBlsToExecutionReturnsBadRequest() throws IOException {
    final List<SignedBlsToExecutionChange> requestBody =
        List.of(
            new SignedBlsToExecutionChange(dataStructureUtil.randomSignedBlsToExecutionChange()));

    when(blsToExecutionChangePool.addLocal(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("Invalid!")));

    Response response =
        post(PostBlsToExecutionChanges.ROUTE, jsonProvider.objectToJSON(requestBody));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string()).contains("Invalid!");
  }
}
