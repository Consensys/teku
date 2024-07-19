/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(
    milestone = {PHASE0, ELECTRA},
    network = Eth2Network.MINIMAL)
public class PostAttestationsRequestTest extends AbstractTypeDefRequestTestBase {

  private PostAttestationsRequest request;
  private final JsonProvider jsonProvider = new JsonProvider();

  @BeforeEach
  void setupRequest() {
    request = new PostAttestationsRequest(spec, mockWebServer.url("/"), okHttpClient);
  }

  @TestTemplate
  public void shouldSubmitAttestations() throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    final List<Attestation> attestations =
        List.of(dataStructureUtil.randomAttestation(), dataStructureUtil.randomAttestation());
    final Optional<PostDataFailureResponse> response =
        request.postAttestations(attestations, specMilestone);
    assertThat(response).isEmpty();

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getHeader(HEADER_CONSENSUS_VERSION))
        .isEqualTo(specMilestone.name().toLowerCase(Locale.ROOT));
  }

  @TestTemplate
  public void shouldHandleBadRequest() throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    final List<Attestation> attestations =
        List.of(dataStructureUtil.randomAttestation(), dataStructureUtil.randomAttestation());
    assertThatThrownBy(() -> request.postAttestations(attestations, specMilestone))
        .isInstanceOf(IllegalArgumentException.class);

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getHeader(HEADER_CONSENSUS_VERSION))
        .isEqualTo(specMilestone.name().toLowerCase(Locale.ROOT));
  }

  private String asJson(final Object object) {
    try {
      return jsonProvider.objectToJSON(object);
    } catch (JsonProcessingException e) {
      fail("Error conversing object to json", e);
      return "";
    }
  }
}
