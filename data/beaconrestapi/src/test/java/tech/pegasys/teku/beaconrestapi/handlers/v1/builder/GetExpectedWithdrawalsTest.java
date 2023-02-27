/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.builder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

class GetExpectedWithdrawalsTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  @BeforeEach
  void setup() {
    initialise(SpecMilestone.CAPELLA);
    genesis();

    setHandler(new GetExpectedWithdrawals(chainDataProvider, schemaDefinitionCache));
    request.setPathParameter("block_id", "head");
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle501() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_IMPLEMENTED);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final BeaconBlockBodyCapella beaconBlock =
        BeaconBlockBodyCapella.required(dataStructureUtil.randomBeaconBlock(1).getBody());
    final ObjectAndMetaData<SszList<Withdrawal>> responseData =
        withMetaData(beaconBlock.getExecutionPayload().getWithdrawals());

    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(GetExpectedWithdrawalsTest.class, "getExpectedWithdrawals.json"),
            UTF_8);
    assertThat(data).isEqualTo(expected);
  }
}
