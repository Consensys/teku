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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.GetLightClientBootstrapResponse;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.lightclient.GetLightClientBootstrap;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetLightClientBootstrapIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  final Bytes32 blockRoot = Bytes32.random();

  @BeforeEach
  void setup() {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
  }

  @Test
  void shouldReturnResultIfCreatedSuccessfully() throws IOException {
    BeaconState state =
        safeJoin(dataProvider.getChainDataProvider().getBeaconStateAtHead()).get().getData();
    BeaconBlockHeader latestBlockHeader = state.getLatestBlockHeader();
    BeaconBlockHeader expectedHeader =
        new BeaconBlockHeader(
            latestBlockHeader.getSlot(),
            latestBlockHeader.getProposerIndex(),
            latestBlockHeader.getParentRoot(),
            state.hashTreeRoot(),
            latestBlockHeader.getBodyRoot());
    SyncCommittee expectedSyncCommittee = state.toVersionAltair().get().getCurrentSyncCommittee();

    final Response response = get(expectedHeader.getRoot());
    assertThat(response.code()).isEqualTo(SC_OK);

    final GetLightClientBootstrapResponse parsedBootstrapResponse =
        jsonProvider.jsonToObject(response.body().string(), GetLightClientBootstrapResponse.class);
    final tech.pegasys.teku.api.schema.BeaconBlockHeader apiBlockHeader =
        new tech.pegasys.teku.api.schema.BeaconBlockHeader(expectedHeader);
    final tech.pegasys.teku.api.schema.altair.SyncCommittee apiSyncCommittee =
        new tech.pegasys.teku.api.schema.altair.SyncCommittee(expectedSyncCommittee);

    assertThat(parsedBootstrapResponse.data.header).isEqualTo(apiBlockHeader);
    assertThat(parsedBootstrapResponse.data.currentSyncCommittee).isEqualTo(apiSyncCommittee);
  }

  @Test
  void shouldReturnBadRequestIfInvalidPath() throws IOException {
    final Response response =
        getResponse(GetLightClientBootstrap.ROUTE.replace("{block_root}", "foo"));
    assertBadRequest(response);
  }

  @Test
  void shouldReturnNotFoundIfNoBlock() throws IOException {
    final Response response = get(blockRoot);
    assertNotFound(response);
  }

  public Response get(final Bytes32 blockRoot) throws IOException {
    return getResponse(
        GetLightClientBootstrap.ROUTE.replace("{block_root}", blockRoot.toHexString()));
  }
}
