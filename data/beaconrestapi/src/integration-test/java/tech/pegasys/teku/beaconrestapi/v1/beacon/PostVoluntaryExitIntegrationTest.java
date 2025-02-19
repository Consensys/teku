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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostVoluntaryExit;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class PostVoluntaryExitIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
    final Response response = post(PostVoluntaryExit.ROUTE, "{\"foo\": \"bar\"}");
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsEmpty() throws Exception {
    checkEmptyBodyToRoute(PostVoluntaryExit.ROUTE, SC_BAD_REQUEST);
  }

  @Test
  public void shouldIncludeRejectReasonWhenOperationValidatorRejectsOperation() throws Exception {
    final SignedVoluntaryExit signedVoluntaryExit = dataStructureUtil.randomSignedVoluntaryExit();
    when(voluntaryExitPool.addLocal(signedVoluntaryExit))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("the reason")));

    final Response response =
        post(
            PostVoluntaryExit.ROUTE,
            JsonUtil.serialize(
                signedVoluntaryExit, signedVoluntaryExit.getSchema().getJsonTypeDefinition()));
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string()).contains("the reason");
  }

  @Test
  public void shouldReturnServerErrorWhenUnexpectedErrorHappens() throws Exception {
    final SignedVoluntaryExit signedVoluntaryExit = dataStructureUtil.randomSignedVoluntaryExit();
    doThrow(new RuntimeException()).when(voluntaryExitPool).addLocal(signedVoluntaryExit);

    final Response response =
        post(
            PostVoluntaryExit.ROUTE,
            JsonUtil.serialize(
                signedVoluntaryExit, signedVoluntaryExit.getSchema().getJsonTypeDefinition()));
    assertThat(response.code()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  public void shouldReturnSuccessWhenRequestBodyIsValid() throws Exception {
    final SignedVoluntaryExit signedVoluntaryExit = dataStructureUtil.randomSignedVoluntaryExit();

    when(voluntaryExitPool.addLocal(signedVoluntaryExit))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final Response response =
        post(
            PostVoluntaryExit.ROUTE,
            JsonUtil.serialize(
                signedVoluntaryExit, signedVoluntaryExit.getSchema().getJsonTypeDefinition()));

    verify(voluntaryExitPool).addLocal(signedVoluntaryExit);

    assertThat(response.code()).isEqualTo(SC_OK);
  }
}
