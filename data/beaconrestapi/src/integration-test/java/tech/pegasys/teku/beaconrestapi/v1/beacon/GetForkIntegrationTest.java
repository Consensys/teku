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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.GetStateForkResponse;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateFork;

public class GetForkIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldGetForkChoiceAtEmptyHeadSlot() throws IOException {
    createBlocksAtSlots(10, 11, 12);
    setCurrentSlot(13);
    final Response response = get("head");
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateForkResponse body =
        jsonProvider.jsonToObject(response.body().string(), GetStateForkResponse.class);
    final Fork data = body.data;

    final Fork expected = new Fork(safeJoin(recentChainData.getBestState().get()).getFork());
    assertThat(expected).isEqualTo(data);
  }

  public Response get(final String stateIdString) throws IOException {
    return getResponse(GetStateFork.ROUTE.replace("{state_id}", stateIdString));
  }
}
