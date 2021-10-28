/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.v2.debug;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_ACCEPT_JSON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_ACCEPT_OCTET;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v2.debug.GetStateResponseV2;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.api.schema.altair.BeaconStateAltair;
import tech.pegasys.teku.api.schema.phase0.BeaconStatePhase0;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.debug.GetState;
import tech.pegasys.teku.spec.SpecMilestone;

public class GetStateIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  @Test
  public void shouldGetPhase0StateAsJson() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    final Response response = get("head", HEADER_ACCEPT_JSON);
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateResponseV2 stateResponse =
        jsonProvider.jsonToObject(response.body().string(), GetStateResponseV2.class);
    assertThat(stateResponse).isNotNull();
    assertThat(stateResponse.version).isEqualTo(Version.phase0);
    assertThat(stateResponse.data).isInstanceOf(BeaconStatePhase0.class);
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo(Version.phase0.name());
  }

  @Test
  public void shouldGetAltairStateAsJson() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final Response response = get("head", HEADER_ACCEPT_JSON);
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateResponseV2 stateResponse =
        jsonProvider.jsonToObject(response.body().string(), GetStateResponseV2.class);
    assertThat(stateResponse).isNotNull();
    assertThat(stateResponse.version).isEqualTo(Version.altair);
    assertThat(stateResponse.data).isInstanceOf(BeaconStateAltair.class);
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo(Version.altair.name());
  }

  @Test
  public void shouldGetAltairStateAsSsz() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final Response response = get("head", HEADER_ACCEPT_OCTET);
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo(Version.altair.name());
  }

  public Response get(final String stateIdIdString, final String contentType) throws IOException {
    return getResponse(GetState.ROUTE.replace("{state_id}", stateIdIdString), contentType);
  }

  public Response get(final String stateIdIdString) throws IOException {
    return getResponse(GetState.ROUTE.replace("{state_id}", stateIdIdString));
  }
}
