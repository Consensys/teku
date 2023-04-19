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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doReturn;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;

class GetBlockAttestationsTest extends AbstractMigratedBeaconHandlerTest {
  private final List<Attestation> attestations =
      List.of(dataStructureUtil.randomAttestation(), dataStructureUtil.randomAttestation());
  private final ObjectAndMetaData<List<Attestation>> responseData =
      new ObjectAndMetaData<>(
          attestations, spec.getGenesisSpec().getMilestone(), false, true, false);

  @BeforeEach
  void setUp() {
    setHandler(new GetBlockAttestations(chainDataProvider, spec));
    request.setPathParameter("block_id", "head");
  }

  @Test
  public void shouldReturnBlockAttestationsInformation() throws JsonProcessingException {
    final Optional<ObjectAndMetaData<List<Attestation>>> optionalData = Optional.of(responseData);
    doReturn(SafeFuture.completedFuture(optionalData))
        .when(chainDataProvider)
        .getBlockAttestations("head");

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(responseData.getData().size()).isGreaterThan(0);
    assertThat(request.getResponseBody()).isEqualTo(optionalData.get());
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
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(GetBlockAttestationsTest.class, "getBlockAttestations.json"),
            UTF_8);
    assertThat(data).isEqualTo(expected);
  }
}
