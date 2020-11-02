/*
 * Copyright 2020 ConsenSys AG.
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

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.GetStateRootResponse;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateRoot;

public class GetStateRootIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldStateRootAtEmptyHeadSlot() throws IOException {
    createBlocksAtSlots(10, 11, 12);
    setCurrentSlot(13);
    final Bytes32 chainHeadStateRoot =
        recentChainData.getBestState().map(state -> state.hashTreeRoot()).orElse(Bytes32.ZERO);
    final Response response = get("head");
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateRootResponse body =
        jsonProvider.jsonToObject(response.body().string(), GetStateRootResponse.class);

    assertThat(body.data.root.toHexString()).isEqualTo(chainHeadStateRoot.toHexString());
  }

  public Response get(final String stateIdString) throws IOException {
    return getResponse(GetStateRoot.ROUTE.replace(":state_id", stateIdString));
  }
}
