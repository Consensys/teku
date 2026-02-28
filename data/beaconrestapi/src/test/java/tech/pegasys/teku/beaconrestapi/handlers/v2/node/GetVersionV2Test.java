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
    final ClientVersion executionClient =
        new ClientVersion("geth", "Geth", "1.13.0", Bytes4.fromHexString("0x01020304"));
    final ClientVersion beaconNode =
        new ClientVersion(
            ClientVersion.TEKU_CLIENT_CODE,
            VersionProvider.CLIENT_IDENTITY,
            VersionProvider.IMPLEMENTATION_VERSION,
            Bytes4.ZERO);
    final GetVersionV2.VersionDataV2 responseData =
        new GetVersionV2.VersionDataV2(beaconNode, Optional.of(executionClient));
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    assertThat(data).contains("\"beacon_node\":");
    assertThat(data).contains("\"execution_client\":");
    assertThat(data).contains("\"code\":");
    assertThat(data).contains("\"name\":");
    assertThat(data).contains("\"version\":");
    assertThat(data).contains("\"commit\":");
  }

  @Test
  void metadata_shouldHandle200_withoutExecutionClient() throws JsonProcessingException {
    final ClientVersion beaconNode =
        new ClientVersion(
            ClientVersion.TEKU_CLIENT_CODE,
            VersionProvider.CLIENT_IDENTITY,
            VersionProvider.IMPLEMENTATION_VERSION,
            Bytes4.ZERO);
    final GetVersionV2.VersionDataV2 responseData =
        new GetVersionV2.VersionDataV2(beaconNode, Optional.empty());
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    assertThat(data).contains("\"beacon_node\":");
    assertThat(data).doesNotContain("\"execution_client\":");
  }

  @Test
  public void shouldReturnBeaconNodeInfoWithoutExecutionClient() throws Exception {
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    final GetVersionV2.VersionDataV2 response =
        (GetVersionV2.VersionDataV2) request.getResponseBody();

    final ClientVersion beaconNode = response.beaconNode();
    assertThat(beaconNode).isNotNull();
    assertThat(beaconNode.code()).isEqualTo(ClientVersion.TEKU_CLIENT_CODE);
    assertThat(beaconNode.name()).isEqualTo(VersionProvider.CLIENT_IDENTITY);
    assertThat(beaconNode.version()).isEqualTo(VersionProvider.IMPLEMENTATION_VERSION);
    assertThat(beaconNode.commit()).isNotNull();

    assertThat(response.executionClient()).isEmpty();
  }

  @Test
  public void shouldReturnExecutionClientInfoWhenAvailable() throws Exception {
    final ClientVersion clientVersion =
        new ClientVersion("geth", "Geth", "1.13.0", Bytes4.fromHexString("0x01020304"));
    localExecutionClientDataProvider.onExecutionClientVersion(clientVersion);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);

    final GetVersionV2.VersionDataV2 response =
        (GetVersionV2.VersionDataV2) request.getResponseBody();

    assertThat(response.beaconNode()).isNotNull();
    assertThat(response.beaconNode().code()).isEqualTo(ClientVersion.TEKU_CLIENT_CODE);
    assertThat(response.executionClient()).isPresent();

    final ClientVersion executionClient = response.executionClient().get();
    assertThat(executionClient.code()).isEqualTo("geth");
    assertThat(executionClient.name()).isEqualTo("Geth");
    assertThat(executionClient.version()).isEqualTo("1.13.0");
    assertThat(executionClient.commit()).isEqualTo(Bytes4.fromHexString("0x01020304"));
  }

  @Test
  public void shouldClearExecutionClientWhenItBecomesUnavailable() throws Exception {
    final ClientVersion clientVersion =
        new ClientVersion("besu", "Besu", "24.1.0", Bytes4.fromHexString("0xaabbccdd"));
    localExecutionClientDataProvider.onExecutionClientVersion(clientVersion);
    localExecutionClientDataProvider.onExecutionClientVersionNotAvailable();

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    final GetVersionV2.VersionDataV2 response =
        (GetVersionV2.VersionDataV2) request.getResponseBody();

    assertThat(response.beaconNode()).isNotNull();
    assertThat(response.beaconNode().code()).isEqualTo(ClientVersion.TEKU_CLIENT_CODE);
    assertThat(response.executionClient()).isEmpty();
  }

  @Test
  public void shouldReflectUpdatedExecutionClientVersion() throws Exception {
    final ClientVersion initialVersion =
        new ClientVersion("geth", "Geth", "1.13.0", Bytes4.fromHexString("0x01020304"));
    localExecutionClientDataProvider.onExecutionClientVersion(initialVersion);
    final ClientVersion updatedVersion =
        new ClientVersion("geth", "Geth", "1.14.0", Bytes4.fromHexString("0x05060708"));
    localExecutionClientDataProvider.onExecutionClientVersion(updatedVersion);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);

    final GetVersionV2.VersionDataV2 response =
        (GetVersionV2.VersionDataV2) request.getResponseBody();
    assertThat(response.executionClient()).isPresent();
    final ClientVersion executionClient = response.executionClient().get();
    assertThat(executionClient.version()).isEqualTo("1.14.0");
    assertThat(executionClient.commit()).isEqualTo(Bytes4.fromHexString("0x05060708"));
  }
}
