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

package tech.pegasys.teku.beaconrestapi.handlers.v1.debug;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_ACCEPTABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.metadata.DataColumnSidecarsAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetDataColumnSidecarsTest
    extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  @BeforeEach
  void setup() {
    initialise(SpecMilestone.FULU);
    genesis();

    setHandler(new GetDataColumnSidecars(chainDataProvider, schemaDefinitionCache));
    request.setPathParameter("block_id", "head");
  }

  @Test
  void shouldReturnDataColumnSidecars()
      throws JsonProcessingException, ExecutionException, InterruptedException {
    final ObjectAndMetaData<SignedBeaconBlock> blockAndMetaData =
        chainDataProvider.getBlock("head").get().orElseThrow();
    final List<DataColumnSidecar> dataColumnSidecars =
        combinedChainDataClient
            .getDataColumnSidecars(blockAndMetaData.getData().getSlot(), Collections.emptyList())
            .get();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(((DataColumnSidecarsAndMetaData) request.getResponseBody()).getData())
        .isEqualTo(dataColumnSidecars);
    assertThat(request.getResponseHeaders(ETH_CONSENSUS_VERSION_TYPE.getName()))
        .isEqualTo(SpecMilestone.FULU.lowerCaseName());
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
  void metadata_shouldHandle406() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_ACCEPTABLE);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws Exception {
    final List<DataColumnSidecar> dataColumnSidecars =
        List.of(
            dataStructureUtil.randomDataColumnSidecar(),
            dataStructureUtil.randomDataColumnSidecar(),
            dataStructureUtil.randomDataColumnSidecar());
    final DataColumnSidecarsAndMetaData dataColumnSidecarsAndMetaData =
        new DataColumnSidecarsAndMetaData(
            dataColumnSidecars, SpecMilestone.FULU, true, false, false);
    final JsonNode data =
        JsonTestUtil.parseAsJsonNode(
            getResponseStringFromMetadata(handler, SC_OK, dataColumnSidecarsAndMetaData));
    final JsonNode expected =
        JsonTestUtil.parseAsJsonNode(
            Resources.toString(
                Resources.getResource(
                    GetDataColumnSidecarsTest.class, "getDataColumnSidecars.json"),
                UTF_8));
    assertThat(data).isEqualTo(expected);
  }
}
