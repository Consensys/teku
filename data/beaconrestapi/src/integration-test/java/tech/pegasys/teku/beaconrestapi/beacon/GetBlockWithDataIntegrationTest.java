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

import static com.google.common.primitives.UnsignedLong.ONE;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.GetBlockResponse;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetBlock;

public class GetBlockWithDataIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldGetBlockWhenPresent_getByEpoch() throws Exception {
    final List<SignedBeaconBlock> blocks = createBlocksAtSlotsAndMapToApiResult(SIX, SEVEN, EIGHT);
    final Response response = getByEpoch(ONE);
    final String responseBody = response.body().string();
    final GetBlockResponse result = jsonProvider.jsonToObject(responseBody, GetBlockResponse.class);

    assertThat(result.signedBeaconBlock).usingRecursiveComparison().isEqualTo(blocks.get(2));
    assertThat(response.code()).isEqualTo(SC_OK);
  }

  @Test
  public void shouldGetBlockWhenPresent_getBySlot() throws Exception {
    final List<SignedBeaconBlock> blocks = createBlocksAtSlotsAndMapToApiResult(EIGHT, NINE);
    final Response response = getBySlot(EIGHT);
    final String responseBody = response.body().string();
    final GetBlockResponse result = jsonProvider.jsonToObject(responseBody, GetBlockResponse.class);

    assertThat(result.signedBeaconBlock).usingRecursiveComparison().isEqualTo(blocks.get(0));
    assertThat(response.code()).isEqualTo(SC_OK);
  }

  @Test
  void shouldGetEffectiveBlockWhenSlotIsEmpty_getByEpoch() throws Exception {
    final List<SignedBeaconBlock> blocks =
        createBlocksAtSlotsAndMapToApiResult(SIX, SEVEN, NINE, TEN);
    final Response response = getByEpoch(ONE);

    assertThat(response.code()).isEqualTo(SC_OK);
    final String responseBody = response.body().string();
    final GetBlockResponse result = jsonProvider.jsonToObject(responseBody, GetBlockResponse.class);
    assertThat(result.signedBeaconBlock).usingRecursiveComparison().isEqualTo(blocks.get(1));
  }

  @Test
  void shouldGetEffectiveBlockWhenSlotIsEmpty_getBySlot() throws Exception {
    final List<SignedBeaconBlock> blocks = createBlocksAtSlotsAndMapToApiResult(SIX, SEVEN, TEN);
    final Response response = getBySlot(NINE);

    assertThat(response.code()).isEqualTo(SC_OK);
    final String responseBody = response.body().string();
    final GetBlockResponse result = jsonProvider.jsonToObject(responseBody, GetBlockResponse.class);
    assertThat(result.signedBeaconBlock).usingRecursiveComparison().isEqualTo(blocks.get(1));
  }

  private Response getByEpoch(final UnsignedLong epoch) throws IOException {
    return getResponse(GetBlock.ROUTE, Map.of(EPOCH, epoch.toString()));
  }

  private Response getBySlot(final UnsignedLong slot) throws IOException {
    return getResponse(GetBlock.ROUTE, Map.of(SLOT, slot.toString()));
  }
}
