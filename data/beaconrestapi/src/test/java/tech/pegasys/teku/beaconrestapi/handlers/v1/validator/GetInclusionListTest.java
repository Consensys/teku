/*
 * Copyright Consensys Software Inc., 2025
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;

class GetInclusionListTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  @BeforeEach
  void setUp() {

    initialise(SpecMilestone.EIP7805);
    setHandler(new GetInclusionList(validatorDataProvider, spec));
  }

  @Test
  void shouldRespondSyncing() throws JsonProcessingException {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(false);
    handler.handleRequest(request);

    Assertions.assertThat(request.getResponseCode())
        .isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    Assertions.assertThat(request.getResponseBody()).isInstanceOf(HttpErrorResponse.class);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, HttpStatusCodes.SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final List<Transaction> inclusionListTransactions =
        List.of(dataStructureUtil.randomExecutionPayloadTransaction());
    final String data = getResponseStringFromMetadata(handler, SC_OK, inclusionListTransactions);
    final String expected =
        Resources.toString(
            Resources.getResource(GetInclusionListTest.class, "getInclusionList.json"), UTF_8);
    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}
