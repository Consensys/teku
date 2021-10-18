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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.FinalityCheckpointsResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateFinalityCheckpointsResponse;
import tech.pegasys.teku.api.schema.Checkpoint;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateFinalityCheckpoints;

public class GetStateFinalityCheckpointsTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldGetEmptyCheckpointsAtGenesis() throws IOException {
    startRestAPIAtGenesis();
    final Response response = get("genesis");

    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateFinalityCheckpointsResponse body =
        jsonProvider.jsonToObject(
            response.body().string(), GetStateFinalityCheckpointsResponse.class);
    final FinalityCheckpointsResponse data = body.data;

    assertThat(data.current_justified).isEqualTo(Checkpoint.EMPTY);
    assertThat(data.previous_justified).isEqualTo(Checkpoint.EMPTY);
    assertThat(data.finalized).isEqualTo(Checkpoint.EMPTY);
  }

  public Response get(final String stateIdString) throws IOException {
    return getResponse(GetStateFinalityCheckpoints.ROUTE.replace("{state_id}", stateIdString));
  }
}
