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

import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beacon.sync.events.SyncState.SYNCING;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletResponse;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;

class GetInclusionListTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setUp() {
    setHandler(new GetInclusionList(syncDataProvider, validatorDataProvider));
  }

  @Test
  void shouldRespondSyncing() throws JsonProcessingException {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SYNCING);
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

  // missing 200

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}
