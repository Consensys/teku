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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.lightclient;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_ACCEPTABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseSszFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateResponse;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetLightClientUpdatesByRangeTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setup() {
    setHandler(new GetLightClientUpdatesByRange(schemaDefinitionCache));
    dataStructureUtil = new DataStructureUtil(TestSpecFactory.createMinimalAltair());
    request.setQueryParameter("start_period", "1");
    request.setQueryParameter("count", "1");
  }

  @Test
  void metadata_shouldHandleJson200() throws IOException {
    LightClientUpdate lightClientUpdate = dataStructureUtil.randomLightClientUpdate(UInt64.ONE);
    ObjectAndMetaData<LightClientUpdate> responseData =
        new ObjectAndMetaData<>(lightClientUpdate, SpecMilestone.ALTAIR, false, true, false);
    List<ObjectAndMetaData<LightClientUpdate>> response = List.of(responseData);

    final String data = getResponseStringFromMetadata(handler, SC_OK, response);
    final String expected =
        Resources.toString(
            Resources.getResource(
                GetLightClientBootstrapTest.class, "getLightClientUpdatesByRange.json"),
            StandardCharsets.UTF_8);
    assertThat(data).isEqualTo(expected);
  }

  @Test
  void metadata_shouldHandleSsz200() throws IOException {
    LightClientUpdateResponse responseData =
        dataStructureUtil.randomLightClientUpdateResponse(UInt64.ONE);
    List<LightClientUpdateResponse> response = List.of(responseData);

    final byte[] actual = getResponseSszFromMetadata(handler, SC_OK, response);
    final byte[] expected = responseData.sszSerialize().toArray();

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle406() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_ACCEPTABLE);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  public void metadata_shouldHandle501() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_IMPLEMENTED);
  }
}
