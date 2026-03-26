/*
 * Copyright Consensys Software Inc., 2026
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;

public class PostExecutionPayloadEnvelopeTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setup() {
    setSpec(TestSpecFactory.createMinimalGloas());
    setHandler(new PostExecutionPayloadEnvelope(validatorDataProvider, schemaDefinitionCache));
  }

  @Test
  void shouldReturnOkIfSuccess() throws Exception {
    final SignedExecutionPayloadEnvelope envelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final PublishSignedExecutionPayloadResult successResult =
        PublishSignedExecutionPayloadResult.success(envelope.getBeaconBlockRoot());

    request.setRequestBody(envelope);
    when(validatorDataProvider.publishSignedExecutionPayload(any(), any()))
        .thenReturn(SafeFuture.completedFuture(successResult));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldReturnAcceptedIfPublishedButRejected() throws Exception {
    final SignedExecutionPayloadEnvelope envelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final PublishSignedExecutionPayloadResult failResult =
        PublishSignedExecutionPayloadResult.notImported(
            envelope.getBeaconBlockRoot(), "Invalid payload");

    request.setRequestBody(envelope);
    when(validatorDataProvider.publishSignedExecutionPayload(any(), any()))
        .thenReturn(SafeFuture.completedFuture(failResult));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_ACCEPTED);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldReturnServerErrorIfRejectedAndNotPublished() throws Exception {
    final SignedExecutionPayloadEnvelope envelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final PublishSignedExecutionPayloadResult failResult =
        PublishSignedExecutionPayloadResult.rejected(envelope.getBeaconBlockRoot(), "oopsy");

    request.setRequestBody(envelope);
    when(validatorDataProvider.publishSignedExecutionPayload(any(), any()))
        .thenReturn(SafeFuture.completedFuture(failResult));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(request.getResponseBodyAsJson(handler))
        .isEqualTo("{\"code\":500,\"message\":\"oopsy\"}");
  }
}
