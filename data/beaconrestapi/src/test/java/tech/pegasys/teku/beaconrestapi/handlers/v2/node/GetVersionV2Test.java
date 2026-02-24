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

package tech.pegasys.teku.beaconrestapi.handlers.v2.node;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ExecutionClientDataProvider;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;

public class GetVersionV2Test extends AbstractMigratedBeaconHandlerTest {

  private ExecutionClientDataProvider localExecutionClientDataProvider;

  @BeforeEach
  void setUp() {
    localExecutionClientDataProvider = new ExecutionClientDataProvider();
    setHandler(new GetVersionV2(localExecutionClientDataProvider));
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
    final GetVersionV2.ExecutionClientInfo clientInfo =
        new GetVersionV2.ExecutionClientInfo(
            "geth", "Geth", "1.13.0", Bytes4.fromHexString("0x01020304"));
    final GetVersionV2.VersionDataV2 responseData =
        new GetVersionV2.VersionDataV2(VersionProvider.VERSION, Optional.of(clientInfo));
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    assertThat(data).contains("\"version\":");
  }

  @Test
  public void shouldReturnVersionWithoutExecutionClient() throws Exception {
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    final GetVersionV2.VersionDataV2 response =
        (GetVersionV2.VersionDataV2) request.getResponseBody();
    assertThat(response.getVersion()).isEqualTo(VersionProvider.VERSION);
    assertThat(response.getExecutionClient()).isEmpty();
  }

  @Test
  public void shouldReturnVersionWithExecutionClient() throws Exception {
    final ClientVersion clientVersion =
        new ClientVersion("geth", "Geth", "1.13.0", Bytes4.fromHexString("0x01020304"));
    localExecutionClientDataProvider.onExecutionClientVersion(clientVersion);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);

    final GetVersionV2.VersionDataV2 response =
        (GetVersionV2.VersionDataV2) request.getResponseBody();
    assertThat(response.getVersion()).isEqualTo(VersionProvider.VERSION);
    assertThat(response.getExecutionClient()).isPresent();
    final GetVersionV2.ExecutionClientInfo clientInfo = response.getExecutionClient().get();
    assertThat(clientInfo.getName()).hasValue("Geth");
    assertThat(clientInfo.getCode()).hasValue("geth");
  }

  @Test
  public void shouldReturnVersionWhenExecutionClientBecomesUnavailable() throws Exception {
    final ClientVersion clientVersion =
        new ClientVersion("besu", "Besu", "24.1.0", Bytes4.fromHexString("0xaabbccdd"));
    localExecutionClientDataProvider.onExecutionClientVersion(clientVersion);
    localExecutionClientDataProvider.onExecutionClientVersionNotAvailable();

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    final GetVersionV2.VersionDataV2 response =
        (GetVersionV2.VersionDataV2) request.getResponseBody();
    assertThat(response.getVersion()).isEqualTo(VersionProvider.VERSION);
    assertThat(response.getExecutionClient()).isEmpty();
  }
}
