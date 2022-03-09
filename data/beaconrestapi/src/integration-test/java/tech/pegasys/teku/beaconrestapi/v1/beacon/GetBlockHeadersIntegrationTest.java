/*
 * Copyright 2022 ConsenSys AG.
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
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockHeadersResponse;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeaders;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;

public class GetBlockHeadersIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldGetBlockHeaders() throws IOException {
    createBlocksAtSlots(10);
    SignedBlockAndState head = chainBuilder.generateNextBlock();
    chainUpdater.updateBestBlock(head);

    final Response response = get();

    final GetBlockHeadersResponse body =
        jsonProvider.jsonToObject(response.body().string(), GetBlockHeadersResponse.class);

    assertThat(body.data.size()).isGreaterThan(0);
    assertThat(body.data.get(0).root).isEqualTo(head.getRoot());
  }

  @Test
  public void shouldGetBlockHeadersBySlot() throws IOException {
    createBlocksAtSlots(10);
    SignedBlockAndState parent = chainBuilder.generateNextBlock();
    chainUpdater.updateBestBlock(parent);
    SignedBlockAndState head = chainBuilder.generateNextBlock();
    chainUpdater.updateBestBlock(head);

    final Response response = get(parent.getSlot());

    final GetBlockHeadersResponse body =
        jsonProvider.jsonToObject(response.body().string(), GetBlockHeadersResponse.class);

    assertThat(body.data.size()).isGreaterThan(0);
    assertThat(body.data.get(0).root).isEqualTo(parent.getRoot());
  }

  @Test
  public void shouldGetNonCanonicalHeadersBySlot() throws IOException {
    createBlocksAtSlots(10);
    final ChainBuilder fork = chainBuilder.fork();
    SignedBlockAndState forked = fork.generateNextBlock();
    SignedBlockAndState canonical = chainBuilder.generateNextBlock(1);
    chainUpdater.saveBlock(forked);
    chainUpdater.updateBestBlock(canonical);
    final Response response = get(forked.getSlot());

    final GetBlockHeadersResponse body =
        jsonProvider.jsonToObject(response.body().string(), GetBlockHeadersResponse.class);

    assertThat(body.data.get(0).canonical).isFalse();
  }

  public Response get() throws IOException {
    return getResponse(GetBlockHeaders.ROUTE);
  }

  public Response get(final UInt64 slot) throws IOException {
    return getResponse(GetBlockHeaders.ROUTE, Map.of("slot", slot.toString()));
  }
}
