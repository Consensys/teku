/*
 * Copyright 2022 ConsenSys AG.
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
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.BlockHeaderData;
import tech.pegasys.teku.api.migrated.BlockHeadersResponse;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;

class GetBlockHeadersTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {
  private GetBlockHeaders handler;
  private StubRestApiRequest request;

  @BeforeEach
  void setup() {
    initialise(SpecMilestone.PHASE0);
    genesis();
    handler = new GetBlockHeaders(chainDataProvider);
    request = new StubRestApiRequest();
  }

  @Test
  public void shouldReturnBlockHeaderInformation()
      throws JsonProcessingException, ExecutionException, InterruptedException {
    final BlockHeadersResponse blockHeadersResponse =
        chainDataProvider.getBlockHeaders(Optional.empty(), Optional.empty()).get();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(blockHeadersResponse);
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
    final List<BlockHeaderData> headers =
        List.of(generateBlockHeaderData(), generateBlockHeaderData());
    final BlockHeadersResponse responseData = new BlockHeadersResponse(headers, true);

    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(GetBlockHeadersTest.class, "getBlockHeaders.json"), UTF_8);
    assertThat(data).isEqualTo(expected);
  }

  private BlockHeaderData generateBlockHeaderData() {
    return new BlockHeaderData(
        dataStructureUtil.randomBytes32(), true, dataStructureUtil.randomSignedBeaconBlockHeader());
  }
}
