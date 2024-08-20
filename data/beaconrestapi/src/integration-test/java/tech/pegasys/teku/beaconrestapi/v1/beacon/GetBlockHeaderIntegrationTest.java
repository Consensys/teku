/*
 * Copyright Consensys Software Inc., 2022
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

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeader;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;

public class GetBlockHeaderIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldGetBlockHeader() throws Exception {
    final List<SignedBlockAndState> created = createBlocksAtSlots(10);
    final Response response = get("head");

    final JsonNode responseAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());

    final SignedBeaconBlockHeader signedBeaconBlockHeader =
        JsonUtil.parse(
            responseAsJsonNode.get("data").get("header").toString(),
            SignedBeaconBlockHeader.SSZ_SCHEMA.getJsonTypeDefinition());

    assertThat(signedBeaconBlockHeader).isEqualTo(created.get(0).getBlock().asHeader());
  }

  @Test
  public void shouldGetNonCanonicalBlockHeader() throws Exception {
    createBlocksAtSlots(10);
    final ChainBuilder fork = chainBuilder.fork();
    SignedBlockAndState forked = fork.generateNextBlock();
    SignedBlockAndState canonical = chainBuilder.generateNextBlock(1);
    chainUpdater.saveBlock(forked);
    chainUpdater.updateBestBlock(canonical);

    final Response response = get(forked.getRoot().toHexString());

    final JsonNode responseAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());

    assertThat(responseAsJsonNode.get("data").get("canonical").asBoolean()).isFalse();
  }

  public Response get(final String blockIdString) throws IOException {
    return getResponse(GetBlockHeader.ROUTE.replace("{block_id}", blockIdString));
  }
}
