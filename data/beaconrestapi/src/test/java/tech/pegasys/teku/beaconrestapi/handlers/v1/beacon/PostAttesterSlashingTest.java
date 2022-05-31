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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class PostAttesterSlashingTest extends AbstractMigratedBeaconHandlerTest {
  private final NodeDataProvider nodeDataProvider = mock(NodeDataProvider.class);
  private final PostAttesterSlashing handler = new PostAttesterSlashing(nodeDataProvider, spec);

  @Test
  void shouldBeAbleToSubmitSlashing() throws Exception {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    request.setRequestBody(slashing);

    when(nodeDataProvider.postAttesterSlashing(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldReturnBadRequestIfAttesterSlashingIsInvalid() throws Exception {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    request.setRequestBody(slashing);

    when(nodeDataProvider.postAttesterSlashing(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("Nope")));

    handler.handleRequest(request);

    final HttpErrorResponse expectedBody =
        new HttpErrorResponse(
            SC_BAD_REQUEST,
            "Invalid attester slashing, it will never pass validation so it's rejected.");
    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(request.getResponseBody()).isEqualTo(expectedBody);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    verifyMetadataEmptyResponse(handler, SC_OK);
  }
}
