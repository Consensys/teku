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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;

public class GetProtoArrayTest extends AbstractMigratedBeaconHandlerTest {
  private final Map<String, String> responseMap =
      ImmutableMap.<String, String>builder()
          .put("slot", "0")
          .put("blockRoot", "0x4cc1b500e2fc6df06d141972f1494f73c82f8e9d9b66321f7df1dfde969f8eee")
          .put("parentRoot", "0x0000000000000000000000000000000000000000000000000000000000000000")
          .put("stateRoot", "0x32342ef26d1db3869388444dfe62eeea91444d5242c43b3fed5c216c2d8c6a5b")
          .put("justifiedEpoch", "0")
          .put("finalizedEpoch", "0")
          .put(
              "executionBlockHash",
              "0x0000000000000000000000000000000000000000000000000000000000000000")
          .put("validationStatus", "VALID")
          .put("weight", "409600000000")
          .build();

  @BeforeEach
  void setup() {
    setHandler(new GetProtoArray(chainDataProvider));
  }

  @Test
  public void shouldReturnProtoArrayInformation() throws JsonProcessingException {
    when(chainDataProvider.getProtoArrayData()).thenReturn(List.of(responseMap));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(List.of(responseMap));
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
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, List.of(responseMap));
    final String expected =
        Resources.toString(Resources.getResource(GetProtoArray.class, "getProtoArray.json"), UTF_8);
    assertThat(data).isEqualTo(expected);
  }
}
