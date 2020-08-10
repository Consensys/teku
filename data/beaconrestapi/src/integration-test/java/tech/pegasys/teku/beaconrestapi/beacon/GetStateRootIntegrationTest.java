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

import java.io.IOException;
import java.util.Map;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.RestApiConstants;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetStateRoot;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetStateRootIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldReturnNoContentIfStoreNotDefined_queryBySlot() throws Exception {
    startPreGenesisRestAPI();

    final Response response = getBySlot(1);
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfPreForkChoice_queryBySlot() throws Exception {
    startPreForkChoiceRestAPI();

    final Response response = getBySlot(1);
    assertNoContent(response);
  }

  @Test
  public void shouldReturnStateRootIfBlockPresent() throws Exception {
    startRestAPIAtGenesis();
    createBlocksAtSlotsAndMapToApiResult(SIX);
    final Response response = getBySlot(6);
    assertThat(response.code()).isEqualTo(SC_OK);
    final Bytes32 expectedRoot = getStateRootAtSlot(SIX);
    assertThat(getBytes32FromResponseBody(response)).isEqualTo(expectedRoot);
  }

  @Test
  public void shouldNotReturnStateRootForFutureSlot() throws Exception {
    startRestAPIAtGenesis();
    createBlocksAtSlotsAndMapToApiResult(SIX);
    final Response response = getBySlot(7);
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnStateRootIfBlockMissed() throws Exception {
    startRestAPIAtGenesis();
    createBlocksAtSlotsAndMapToApiResult(SIX, EIGHT);
    final Response response = getBySlot(7);
    assertThat(response.code()).isEqualTo(SC_OK);
    final Bytes32 expectedRoot = getStateRootAtSlot(SEVEN);
    assertThat(getBytes32FromResponseBody(response)).isEqualTo(expectedRoot);
  }

  private Bytes32 getBytes32FromResponseBody(final Response response) throws IOException {
    final String bytes32String = jsonProvider.jsonToObject(response.body().string(), String.class);
    return Bytes32.fromHexString(bytes32String);
  }

  private Bytes32 getStateRootAtSlot(final UInt64 slot) {
    try {
      return combinedChainDataClient
          .getStateAtSlotExact(slot)
          .join()
          .orElseThrow()
          .hash_tree_root();
    } catch (Exception e) {
      return null;
    }
  }

  private Response getBySlot(final int slot) throws IOException {
    return getResponse(
        GetStateRoot.ROUTE, Map.of(RestApiConstants.SLOT, Integer.toString(slot, 10)));
  }
}
