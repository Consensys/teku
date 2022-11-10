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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

// Only need to test PHASE0 as attestation data hasn't yet changed
@TestSpecContext(milestone = SpecMilestone.PHASE0, network = Eth2Network.MINIMAL)
class CreateAttestationDataRequestTest extends AbstractTypeDefRequestTestBase {

  private CreateAttestationDataRequest request;

  @BeforeEach
  void setupRequest() {
    request = new CreateAttestationDataRequest(mockWebServer.url("/"), okHttpClient);
  }

  @TestTemplate
  public void createAttestationData_MakesExpectedRequest() throws Exception {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    request.createAttestationData(slot, committeeIndex);

    RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.GET_ATTESTATION_DATA.getPath(emptyMap()));
    assertThat(request.getRequestUrl().queryParameter("slot")).isEqualTo(slot.toString());
    assertThat(request.getRequestUrl().queryParameter("committee_index"))
        .isEqualTo(String.valueOf(committeeIndex));
  }

  @TestTemplate
  public void createAttestationData_WhenBadRequest_ThrowsIllegalArgumentException() {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));

    assertThatThrownBy(() -> request.createAttestationData(slot, committeeIndex))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  public void createAttestationData_WhenNotFound_ThrowsError() {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;

    // An attestation could not be created for the specified slot
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));

    assertThatThrownBy(() -> request.createAttestationData(slot, committeeIndex))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected response from Beacon Node API");
  }

  @TestTemplate
  public void createAttestationData_WhenSuccessWithNonData_ReturnsAttestationData()
      throws Exception {
    final UInt64 slot = UInt64.ONE;
    final int committeeIndex = 1;
    final AttestationData expectedAttestationData = dataStructureUtil.randomAttestationData();
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(serializeSszObjectToJsonWithDataWrapper(expectedAttestationData)));
    Optional<AttestationData> attestationData = request.createAttestationData(slot, committeeIndex);
    assertThat(attestationData).isPresent();

    assertThat(attestationData.get()).isEqualTo(expectedAttestationData);
  }
}
