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

package tech.pegasys.artemis.beaconrestapi.beacon;

import static com.google.common.primitives.UnsignedLong.ONE;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.SLOT;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.api.schema.SignedBeaconBlock;
import tech.pegasys.artemis.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetBlock;
import tech.pegasys.artemis.provider.JsonProvider;

public class GetBlockWithDataIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  private final JsonProvider jsonProvider = new JsonProvider();

  private static final UnsignedLong SIX = UnsignedLong.valueOf(6);
  private static final UnsignedLong SEVEN = UnsignedLong.valueOf(7);
  private static final UnsignedLong EIGHT = UnsignedLong.valueOf(8);
  private static final UnsignedLong NINE = UnsignedLong.valueOf(9);
  private static final UnsignedLong TEN = UnsignedLong.valueOf(10);

  @Test
  public void shouldGetBlockWhenPresent_getByEpoch() throws Exception {
    final List<SignedBeaconBlock> blocks = withBlockDataAtSlot(SIX, SEVEN, EIGHT);
    final Response response = getByEpoch(ONE);
    final String responseBody = response.body().string();
    final SignedBeaconBlock result =
        jsonProvider.jsonToObject(responseBody, SignedBeaconBlock.class);

    assertThat(result).usingRecursiveComparison().isEqualTo(blocks.get(2));
    assertThat(response.code()).isEqualTo(SC_OK);
  }

  @Test
  public void shouldGetBlockWhenPresent_getBySlot() throws Exception {
    final List<SignedBeaconBlock> blocks = withBlockDataAtSlot(EIGHT, NINE);
    final Response response = getBySlot(EIGHT);
    final String responseBody = response.body().string();
    final SignedBeaconBlock result =
        jsonProvider.jsonToObject(responseBody, SignedBeaconBlock.class);

    assertThat(result).usingRecursiveComparison().isEqualTo(blocks.get(0));
    assertThat(response.code()).isEqualTo(SC_OK);
  }

  @Test
  void shouldGetEffectiveBlockWhenSlotIsEmpty_getByEpoch() throws Exception {
    final List<SignedBeaconBlock> blocks = withBlockDataAtSlot(SIX, SEVEN, NINE, TEN);
    final Response response = getByEpoch(ONE);
    final String responseBody = response.body().string();
    final SignedBeaconBlock result =
        jsonProvider.jsonToObject(responseBody, SignedBeaconBlock.class);

    assertThat(result).usingRecursiveComparison().isEqualTo(blocks.get(1));
    assertThat(response.code()).isEqualTo(SC_OK);
  }

  @Test
  void shouldGetEffectiveBlockWhenSlotIsEmpty_getBySlot() throws Exception {
    final List<SignedBeaconBlock> blocks = withBlockDataAtSlot(SIX, SEVEN, TEN);
    final Response response = getBySlot(NINE);
    final String responseBody = response.body().string();
    final SignedBeaconBlock result =
        jsonProvider.jsonToObject(responseBody, SignedBeaconBlock.class);

    assertThat(result).usingRecursiveComparison().isEqualTo(blocks.get(1));
    assertThat(response.code()).isEqualTo(SC_OK);
  }

  private Response getByEpoch(final UnsignedLong epoch) throws IOException {
    return getResponse(GetBlock.ROUTE, Map.of(EPOCH, epoch.toString()));
  }

  private Response getBySlot(final UnsignedLong slot) throws IOException {
    return getResponse(GetBlock.ROUTE, Map.of(SLOT, slot.toString()));
  }
}
