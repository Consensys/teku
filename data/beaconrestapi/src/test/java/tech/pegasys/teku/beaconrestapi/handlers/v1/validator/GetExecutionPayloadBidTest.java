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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.spec.TestSpecFactory;

class GetExecutionPayloadBidTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setUp() {
    setSpec(TestSpecFactory.createMinimalGloas());
    setHandler(new GetExecutionPayloadBid(validatorDataProvider, spec, schemaDefinitionCache));
    request.setPathParameter("slot", "1");
    request.setPathParameter("builder_index", "1");
  }

  @Test
  void shouldReturnNotImplemented() throws Exception {
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_IMPLEMENTED);
    assertThat(((HttpErrorResponse) request.getResponseBody()).getMessage())
        .isEqualTo("Not implemented");
  }

  @Test
  void metadata_shouldHandle501() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_IMPLEMENTED);
  }
}
