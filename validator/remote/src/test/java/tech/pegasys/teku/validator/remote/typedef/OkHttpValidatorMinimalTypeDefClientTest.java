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

package tech.pegasys.teku.validator.remote.typedef;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.nio.charset.StandardCharsets;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.exceptions.BadRequestException;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;

class OkHttpValidatorMinimalTypeDefClientTest {
  private final MockWebServer mockWebServer = new MockWebServer();
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private OkHttpValidatorMinimalTypeDefClient typeDefClient;
  private OkHttpClient okHttpClient;

  @BeforeEach
  public void beforeEach() throws Exception {
    mockWebServer.start();
    okHttpClient = new OkHttpClient();
    typeDefClient = new OkHttpValidatorMinimalTypeDefClient(mockWebServer.url("/"), okHttpClient);
  }

  @Test
  public void sendVoluntaryExit_makesExpectedRequest() throws Exception {
    final SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));

    typeDefClient.sendVoluntaryExit(exit);

    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.SEND_SIGNED_VOLUNTARY_EXIT.getPath(emptyMap()));
    assertThat(request.getBody().readString(StandardCharsets.UTF_8))
        .contains(
            "\"message\":{\"epoch\":\"4665021361504678828\",\"validator_index\":\"4666673844721362956\"}");
  }

  @Test
  public void sendVoluntaryExit_seesErrorMessage() {
    final SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(SC_BAD_REQUEST)
            .setBody("{\"code\": 400,\"message\": \"No\"}"));

    assertThatThrownBy(() -> typeDefClient.sendVoluntaryExit(exit))
        .isInstanceOf(BadRequestException.class)
        .hasMessageStartingWith("No");
  }
}
