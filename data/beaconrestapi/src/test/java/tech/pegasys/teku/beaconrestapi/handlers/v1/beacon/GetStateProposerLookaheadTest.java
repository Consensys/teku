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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNSUPPORTED_MEDIA_TYPE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseSszFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;

public class GetStateProposerLookaheadTest
    extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  @BeforeEach
  public void setup() {

    final GetStateProposerLookahead proposerLookahead =
        new GetStateProposerLookahead(chainDataProvider, schemaDefinitionCache);
    initialise(SpecMilestone.FULU);
    genesis();
    setHandler(proposerLookahead);
    request.setPathParameter("state_id", "head");
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
  void metadata_shouldHandle415() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_UNSUPPORTED_MEDIA_TYPE);
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
    final BeaconStateFulu state = BeaconStateFulu.required(dataStructureUtil.randomBeaconState(32));
    final ObjectAndMetaData<SszUInt64Vector> responseData =
        new ObjectAndMetaData<>(
            state.getProposerLookahead(), SpecMilestone.FULU, false, true, false);
    final String resource =
        Resources.toString(
            Resources.getResource(
                GetStateProposerLookaheadTest.class, "stateProposerLookahead.json"),
            UTF_8);
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    assertThat(data).isEqualTo(resource);
  }

  @Test
  void metadata_shouldHandle200OctetStream() throws IOException {
    final BeaconStateFulu state = BeaconStateFulu.required(dataStructureUtil.randomBeaconState(32));
    final ObjectAndMetaData<SszUInt64Vector> responseData =
        new ObjectAndMetaData<>(
            state.getProposerLookahead(), SpecMilestone.FULU, false, true, false);
    final byte[] data = getResponseSszFromMetadata(handler, SC_OK, responseData);
    assertThat(Bytes.of(data)).isEqualTo(state.getProposerLookahead().sszSerialize());
  }
}
