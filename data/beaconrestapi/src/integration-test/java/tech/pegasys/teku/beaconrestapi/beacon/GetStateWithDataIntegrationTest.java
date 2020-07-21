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

package tech.pegasys.teku.beaconrestapi.beacon;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.teku.beaconrestapi.handlers.beacon.GetState.ROUTE;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;

public class GetStateWithDataIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  void shouldGetStateBySlot() throws Exception {
    createBlocksAtSlotsAndMapToApiResult(NINE, TEN);

    final BeaconState result = getBeaconStateFromResponse(getBySlot(TEN));
    assertThat(result.asInternalBeaconState()).isEqualTo(getInternalState(TEN));
  }

  @Test
  void shouldGetStateIfNoBlockImported() throws Exception {
    createBlocksAtSlotsAndMapToApiResult(SEVEN, NINE);

    final BeaconState result = getBeaconStateFromResponse(getBySlot(EIGHT));
    assertThat(result.asInternalBeaconState()).isEqualTo(getInternalState(EIGHT));
  }

  @Test
  void shouldReturnNotFoundForFutureSlot() throws Exception {
    createBlocksAtSlotsAndMapToApiResult(SEVEN);

    final Response response = getBySlot(EIGHT);
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
  }

  private BeaconState getBeaconStateFromResponse(Response response) throws Exception {
    assertThat(response.code()).isEqualTo(SC_OK);
    final String responseBody = response.body().string();
    return jsonProvider.jsonToObject(responseBody, BeaconState.class);
  }

  private tech.pegasys.teku.datastructures.state.BeaconState getInternalState(
      final UnsignedLong slot) {
    try {
      return combinedChainDataClient
          .getBlockAndStateInEffectAtSlot(slot)
          .thenApply(
              maybeBlockAndState ->
                  maybeBlockAndState.map(
                      blockAndState ->
                          combinedChainDataClient.regenerateBeaconState(
                              blockAndState.getState(), slot)))
          .get()
          .get();
    } catch (Exception e) {
      return null;
    }
  }

  private Response getBySlot(final UnsignedLong slot) throws IOException {
    return getResponse(ROUTE, Map.of(SLOT, slot.toString()));
  }
}
