/*
 * Copyright Consensys Software Inc., 2025
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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStatePendingPartialWithdrawals;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.spec.SpecMilestone;

public class GetStatePendingPartialWithdrawalsIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {
  @Test
  public void shouldGetElectraDepositsJson() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.ELECTRA);
    createBlocksAtSlots(10);
    final Response response = get("head");

    final String responseText = response.body().string();
    final JsonNode node = JsonTestUtil.parseAsJsonNode(responseText);
    assertThat(node.get("version").asText()).isEqualTo("electra");
    assertThat(node.get("execution_optimistic").asBoolean()).isFalse();
    assertThat(node.get("finalized").asBoolean()).isFalse();
    assertThat(node.get("data").size()).isEqualTo(0);
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.ELECTRA.lowerCaseName());
  }

  public Response get(final String stateId) throws IOException {
    return getResponse(GetStatePendingPartialWithdrawals.ROUTE.replace("{state_id}", stateId));
  }
}
