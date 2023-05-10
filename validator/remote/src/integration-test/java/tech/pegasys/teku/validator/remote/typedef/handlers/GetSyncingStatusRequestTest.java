/*
 * Copyright ConsenSys Software Inc., 2023
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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(network = Eth2Network.MINIMAL)
public class GetSyncingStatusRequestTest extends AbstractTypeDefRequestTestBase {

  private GetSyncingStatusRequest request;

  @BeforeEach
  void setupRequest() {
    request = new GetSyncingStatusRequest(okHttpClient, mockWebServer.url("/"));
  }

  @TestTemplate
  public void getSyncingStatus_setsElOfflineToEmptyIfFieldDoesNotExist() {
    final String mockResponse = readResource("responses/syncing_no_el_offline.json");

    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(mockResponse));

    final SyncingStatus syncingStatus = request.getSyncingStatus();

    assertThat(syncingStatus.isElOffline()).isNotNull().isEmpty();
  }
}
