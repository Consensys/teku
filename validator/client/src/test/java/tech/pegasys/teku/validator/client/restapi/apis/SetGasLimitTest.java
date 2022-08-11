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

package tech.pegasys.teku.validator.client.restapi.apis;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.BeaconProposerPreparer;

public class SetGasLimitTest {

  private final BeaconProposerPreparer beaconProposerPreparer = mock(BeaconProposerPreparer.class);
  private final SetGasLimit handler = new SetGasLimit(Optional.of(beaconProposerPreparer));

  private final StubRestApiRequest request = new StubRestApiRequest(handler.getMetadata());

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final UInt64 gasLimit = dataStructureUtil.randomUInt64();

  @Test
  void badPubkey_shouldGiveIllegalArgument() {
    request.setPathParameter("pubkey", "pubkey");
    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(IllegalArgumentException.class);
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
  void shouldShareContextIfBellatrixNotEnabled() {
    request.setPathParameter("pubkey", dataStructureUtil.randomPublicKey().toString());
    request.setRequestBody(
        new SetFeeRecipient.SetFeeRecipientBody(dataStructureUtil.randomEth1Address()));
    assertThatThrownBy(
            () -> {
              SetFeeRecipient handler = new SetFeeRecipient(Optional.empty());
              handler.handleRequest(request);
            })
        .hasMessageContaining("Bellatrix is not currently scheduled");
  }

  @Test
  void metadata_shouldHandle202() {
    verifyMetadataEmptyResponse(handler, SC_ACCEPTED);
  }

  @Test
  void metadata_shouldReadRequestBody() throws IOException {
    SetGasLimit.SetGasLimitBody body =
        (SetGasLimit.SetGasLimitBody)
            getRequestBodyFromMetadata(handler, "{\"gas_limit\":\"" + gasLimit + "\"}");
    assertThat(body).isEqualTo(new SetGasLimit.SetGasLimitBody(gasLimit));
  }

  @Test
  void metadata_shoulThrowInvalidArgument() {
    assertThatThrownBy(() -> getRequestBodyFromMetadata(handler, "{\"gas_limit\":\"-45000\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
