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

package tech.pegasys.teku.beaconrestapi.handlers.v1.debug;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;

class GetChainHeadsV1Test extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {
  private GetChainHeadsV1 handler;
  private List<ProtoNodeData> protoNodeDataList;

  @BeforeEach
  void setup() {
    initialise(SpecMilestone.ALTAIR);
    genesis();
    handler = new GetChainHeadsV1(chainDataProvider);
    protoNodeDataList = chainDataProvider.getChainHeads();
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
  void metadata_shouldHandle200() throws JsonProcessingException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, protoNodeDataList);
    AssertionsForClassTypes.assertThat(data)
        .isEqualTo(
            "{\"data\":[{\"slot\":\"0\",\"root\":\"0xad2005566b57353fca12fc9bf697cab59c450b72fcf06bead50a85e7450fb0f6\"}]}");
  }

  @Test
  void shouldGetSuccessfulResponse() throws JsonProcessingException {
    final StubRestApiRequest request = new StubRestApiRequest();
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(protoNodeDataList);
  }
}
