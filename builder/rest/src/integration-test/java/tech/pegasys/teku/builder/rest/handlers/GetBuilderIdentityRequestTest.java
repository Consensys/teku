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

package tech.pegasys.teku.builder.rest.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.util.Map;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.builder.rest.AbstractBuilderRequestTestBase;
import tech.pegasys.teku.builder.rest.BuilderApiMethod;
import tech.pegasys.teku.builder.rest.BuilderClientException;
import tech.pegasys.teku.builder.rest.BuilderIdentity;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.networks.Eth2Network;

@TestSpecContext(milestone = SpecMilestone.GLOAS, network = Eth2Network.MINIMAL)
class GetBuilderIdentityRequestTest extends AbstractBuilderRequestTestBase {

  private GetBuilderIdentityRequest request;

  @BeforeEach
  void setupRequest() {
    request = new GetBuilderIdentityRequest(mockWebServer.url("/"), okHttpClient);
  }

  @TestTemplate
  void shouldReturnBuilderIdentityOn200() throws Exception {
    final BLSPublicKey pubkey = dataStructureUtil.randomPublicKey();
    final String body =
        String.format(
            "{\"data\":{\"pubkey\":\"%s\",\"needs_auth\":true}}", pubkey.toHexString());
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(body));

    final Optional<BuilderIdentity> result = request.submit();

    assertThat(result).isPresent();
    assertThat(result.get().pubkey()).isEqualTo(pubkey);
    assertThat(result.get().needsAuth()).isTrue();
  }

  @TestTemplate
  void shouldVerifyCorrectUrlAndMethod() throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_OK)
            .setBody(
                String.format(
                    "{\"data\":{\"pubkey\":\"%s\",\"needs_auth\":false}}",
                    dataStructureUtil.randomPublicKey().toHexString())));

    request.submit();

    final RecordedRequest recorded = mockWebServer.takeRequest();
    assertThat(recorded.getMethod()).isEqualTo("GET");
    assertThat(recorded.getRequestUrl().encodedPath())
        .isEqualTo("/" + BuilderApiMethod.GET_BUILDER_IDENTITY.getPath(Map.of()));
  }

  @TestTemplate
  void shouldThrowBuilderClientExceptionOn500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> request.submit()).isInstanceOf(BuilderClientException.class);
  }
}
