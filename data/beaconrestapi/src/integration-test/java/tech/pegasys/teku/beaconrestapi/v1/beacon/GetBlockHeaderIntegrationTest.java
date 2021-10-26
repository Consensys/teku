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

import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.BlockHeader;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockHeaderResponse;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;

public class GetBlockHeaderIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldGetBlockHeader() throws IOException {
    final List<SignedBlockAndState> created = createBlocksAtSlots(10);
    final Response response = get("head");

    final GetBlockHeaderResponse body =
        jsonProvider.jsonToObject(response.body().string(), GetBlockHeaderResponse.class);
    final BlockHeader data = body.data;
    assertThat(data).isEqualTo(new BlockHeader(created.get(0).getBlock(), true));
  }

  public Response get(final String blockIdString) throws IOException {
    return getResponse(GetBlockHeader.ROUTE.replace("{block_id}", blockIdString));
  }
}
