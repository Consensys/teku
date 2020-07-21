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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;

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
    return combinedChainDataClient.getStateAtSlotExact(slot).join().orElseThrow();
  }

  @Test
  void shouldGetStateByStateRoot() throws Exception {
    List<SignedBeaconBlock> data = createBlocksAtSlotsAndMapToApiResult(NINE);
    final Bytes32 stateRoot = data.get(0).message.state_root;

    final Response response = getByStateRoot(stateRoot);
    final String responseBody = response.body().string();
    final BeaconState result = jsonProvider.jsonToObject(responseBody, BeaconState.class);
    assertThat(result.slot).isEqualTo(NINE);
  }

  @Test
  public void shouldGetStateByStateRootForMissedSlot() throws Exception {
    createBlocksAtSlotsAndMapToApiResult(SIX, NINE, TEN);
    final Bytes32 stateRoot = GetStateRootFromSlot(SIX, SEVEN);

    final Response response = getByStateRoot(stateRoot);
    assertThat(response.code()).isEqualTo(SC_OK);
    final String responseBody = response.body().string();
    final BeaconState result = jsonProvider.jsonToObject(responseBody, BeaconState.class);
    assertThat(result.slot).isEqualTo(SEVEN);
  }

  private Bytes32 GetStateRootFromSlot(final UnsignedLong populatedSlot, UnsignedLong slot)
      throws ExecutionException, InterruptedException, EpochProcessingException,
          SlotProcessingException {
    Optional<tech.pegasys.teku.datastructures.state.BeaconState> beaconStateOptional =
        combinedChainDataClient.getLatestStateAtSlot(populatedSlot).get();
    tech.pegasys.teku.datastructures.state.BeaconState missingSlotState =
        stateTransition.process_slots(beaconStateOptional.get(), slot);
    return missingSlotState.hash_tree_root();
  }

  private Response getBySlot(final UnsignedLong slot) throws IOException {
    return getResponse(ROUTE, Map.of(SLOT, slot.toString()));
  }

  private Response getByStateRoot(final Bytes32 stateRoot) throws IOException {
    return getResponse(ROUTE, Map.of("stateRoot", stateRoot.toHexString().toLowerCase()));
  }
}
