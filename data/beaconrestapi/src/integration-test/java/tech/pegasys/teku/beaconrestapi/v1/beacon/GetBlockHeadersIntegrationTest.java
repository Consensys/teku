/*
 * Copyright ConsenSys Software Inc., 2022
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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockHeadersResponse;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeaders;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;

public class GetBlockHeadersIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  final List<SignedBlockAndState> canonicalBlockAndStateList = new ArrayList<>();
  final List<SignedBlockAndState> nonCanonicalBlockAndStateList = new ArrayList<>();

  @BeforeEach
  public void setup() {
    startRestApiAtGenesisStoringNonCanonicalBlocks();
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

    final GetBlockHeadersResponse body = get(parent.getSlot());

    assertThat(body.data.size()).isGreaterThan(0);
    assertThat(body.data.get(0).root).isEqualTo(parent.getRoot());
  }

  @ParameterizedTest(name = "finalized={0}")
  @ValueSource(booleans = {true, false})
  public void shouldGetNonCanonicalHeadersBySlot(final boolean isFinalized) throws IOException {
    setupData();

    // PRE: we have a non-canonical block and a canonical block at the same slot
    final SignedBeaconBlock canonical =
        canonicalBlockAndStateList.get(canonicalBlockAndStateList.size() - 1).getBlock();
    final SignedBeaconBlock forked = nonCanonicalBlockAndStateList.get(1).getBlock();
    assertThat(forked.getSlot()).isEqualTo(canonical.getSlot());
    if (isFinalized) {
      chainUpdater.finalizeEpoch(2);
    }
    final GetBlockHeadersResponse body = get(nonCanonicalBlockAndStateList.get(1).getSlot());

    assertThat(body.data.size()).isGreaterThan(1);
    assertThat(
            body.data.stream()
                .map(header -> String.format("%s:%s", header.root, header.canonical))
                .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(
            String.format("%s:%s", canonical.getRoot(), true),
            String.format("%s:%s", forked.getRoot(), false));
  }

  private void setupData() {
    canonicalBlockAndStateList.addAll(createBlocksAtSlots(10));
    final ChainBuilder fork = chainBuilder.fork();
    nonCanonicalBlockAndStateList.add(fork.generateNextBlock());
    chainUpdater.saveBlock(
        nonCanonicalBlockAndStateList.get(nonCanonicalBlockAndStateList.size() - 1));
    nonCanonicalBlockAndStateList.add(fork.generateNextBlock());
    chainUpdater.saveBlock(
        nonCanonicalBlockAndStateList.get(nonCanonicalBlockAndStateList.size() - 1));
    canonicalBlockAndStateList.add(chainBuilder.generateNextBlock(1));
    chainUpdater.updateBestBlock(
        canonicalBlockAndStateList.get(canonicalBlockAndStateList.size() - 1));
    chainUpdater.advanceChain(32);
  }

  public Response get() throws IOException {
    return getResponse(GetBlockHeaders.ROUTE);
  }

  public GetBlockHeadersResponse get(final UInt64 slot) throws IOException {
    final Response response = getResponse(GetBlockHeaders.ROUTE, Map.of("slot", slot.toString()));
    assertThat(response.code()).isEqualTo(SC_OK);
    return jsonProvider.jsonToObject(response.body().string(), GetBlockHeadersResponse.class);
  }
}
