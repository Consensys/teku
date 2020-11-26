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
import tech.pegasys.teku.api.response.v1.beacon.GetBlockResponse;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;

public class GetBlockIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldGetBlock() throws IOException {
    final List<SignedBlockAndState> created = createBlocksAtSlots(10);
    final Response response = get("head");

    final GetBlockResponse body =
        jsonProvider.jsonToObject(response.body().string(), GetBlockResponse.class);
    final SignedBeaconBlock data = body.data;
    final SignedBlockAndState block = created.get(0);
    assertThat(data)
        .isEqualTo(
            new SignedBeaconBlock(
                new BeaconBlock(block.getBlock().getMessage()),
                new BLSSignature(block.getBlock().getSignature())));
  }

  @Test
  public void shouldGetBlock_return404WhenBlockRootNotFound() throws IOException {
    final Response response = get("0xdeadbeef");
    assertNotFound(response);
  }

  public Response get(final String blockIdString) throws IOException {
    return getResponse(GetBlock.ROUTE.replace(":block_id", blockIdString));
  }
}
