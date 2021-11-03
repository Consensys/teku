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

package tech.pegasys.teku.beaconrestapi.v1.debug;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_ACCEPT_JSON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_ACCEPT_OCTET;

import java.io.IOException;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.debug.GetStateResponse;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetState;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetStateIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldGetStateAsJson() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    final Response response = get("head", HEADER_ACCEPT_JSON);
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateResponse stateResponse =
        jsonProvider.jsonToObject(response.body().string(), GetStateResponse.class);
    assertThat(stateResponse).isNotNull();
  }

  @Test
  public void shouldGetStateAsJsonWithoutHeader() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    final Response response = get("head");
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateResponse stateResponse =
        jsonProvider.jsonToObject(response.body().string(), GetStateResponse.class);
    assertThat(stateResponse).isNotNull();
  }

  @Test
  public void shouldGetStateAsOctetStream() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    final Response response = get("head", HEADER_ACCEPT_OCTET);
    assertThat(response.code()).isEqualTo(SC_OK);
    final BeaconState state =
        spec.getGenesisSchemaDefinitions()
            .getBeaconStateSchema()
            .sszDeserialize(Bytes.wrap(response.body().bytes()));
    assertThat(state).isNotNull();
  }

  @Test
  public void shouldRejectAltairStateRequestsForJson() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final Response response = get("head");
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
  }

  public Response get(final String stateIdIdString, final String contentType) throws IOException {
    return getResponse(GetState.ROUTE.replace("{state_id}", stateIdIdString), contentType);
  }

  public Response get(final String stateIdIdString) throws IOException {
    return getResponse(GetState.ROUTE.replace("{state_id}", stateIdIdString));
  }
}
