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

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.ATTESTATION_DATA_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(
    milestone = {PHASE0, ELECTRA},
    network = Eth2Network.MINIMAL)
public class GetAggregateAttestationRequestTest extends AbstractTypeDefRequestTestBase {

  private GetAggregateAttestationRequest getAggregateAttestationRequest;
  private Buffer responseBodyBuffer;
  private final UInt64 slot = UInt64.ONE;

  @BeforeEach
  void setupRequest() {
    getAggregateAttestationRequest =
        new GetAggregateAttestationRequest(mockWebServer.url("/"), okHttpClient, spec, slot);
    responseBodyBuffer = new Buffer();
  }

  @AfterEach
  void reset() {
    responseBodyBuffer.clear();
    responseBodyBuffer.close();
  }

  @TestTemplate
  public void getAggregateAttestation_makesExpectedRequest() throws Exception {
    final UInt64 committeeIndex = dataStructureUtil.randomUInt64();
    final Bytes32 attestationHashTreeRoot = dataStructureUtil.randomBytes32();

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));

    getAggregateAttestationRequest.createAggregate(attestationHashTreeRoot, committeeIndex);

    final RecordedRequest request = mockWebServer.takeRequest();

    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).contains(ValidatorApiMethod.GET_AGGREGATE_V2.getPath(emptyMap()));
    assertThat(request.getRequestUrl().queryParameter(SLOT)).isEqualTo(slot.toString());
    assertThat(request.getRequestUrl().queryParameter(ATTESTATION_DATA_ROOT))
        .isEqualTo(attestationHashTreeRoot.toHexString());
    assertThat(request.getRequestUrl().queryParameter(COMMITTEE_INDEX))
        .isEqualTo(committeeIndex.toString());
  }

  @TestTemplate
  public void shouldGetAggregateAttestation() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final GetAggregateAttestationRequest.GetAggregateAttestationResponse
        getAggregateAttestationResponse =
            new GetAggregateAttestationRequest.GetAggregateAttestationResponse(attestation);

    final String mockResponse = readExpectedJsonResource(specMilestone);

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(mockResponse));

    final UInt64 committeeIndex =
        specMilestone.isGreaterThanOrEqualTo(ELECTRA)
            ? UInt64.valueOf(
                attestation.getCommitteeBitsRequired().streamAllSetBits().findFirst().orElseThrow())
            : attestation.getData().getIndex();

    final Optional<ObjectAndMetaData<Attestation>> maybeAttestationAndMetaData =
        getAggregateAttestationRequest.createAggregate(attestation.hashTreeRoot(), committeeIndex);
    assertThat(maybeAttestationAndMetaData).isPresent();
    assertThat(maybeAttestationAndMetaData.get().getData())
        .isEqualTo(getAggregateAttestationResponse.getData());
    assertThat(maybeAttestationAndMetaData.get().getMilestone()).isEqualTo(specMilestone);
  }

  private String readExpectedJsonResource(final SpecMilestone specMilestone) {
    final String fileName = String.format("getAggregateAttestation%s.json", specMilestone.name());
    return readResource("responses/get_aggregate_attestation_responses/" + fileName);
  }
}
