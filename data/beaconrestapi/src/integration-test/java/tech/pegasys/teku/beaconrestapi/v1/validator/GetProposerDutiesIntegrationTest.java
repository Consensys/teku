/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beaconrestapi.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetProposerDuties;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;

public class GetProposerDutiesIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private static final Logger LOG = LogManager.getLogger();

  @Test
  void shouldReturnCorrectDependentRootPreFulu() throws IOException {
    startRestApiAtGenesisWithValidatorApiHandler(SpecMilestone.ALTAIR);
    final List<SignedBlockAndState> chain = createBlocksAtSlots(1, 2, 3, 4, 5, 6, 7, 8);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(syncStateProvider.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    final Response response = getProposerDuties("1");
    final String responseBody = response.body().string();
    assertThat(response.code()).isEqualTo(200);
    assertThat(dependentRoot(responseBody)).isEqualTo(chain.getLast().getParentRoot());
  }

  // epoch | 0                   |<1>
  // slot  | 1  2  3  4  5  6  7 |
  // query |                   ^ |
  // dep   |                   D |
  // EXPECT - should be able to query for the next epoch, may not be stable if it's too early.
  @Test
  void shouldReturnNextEpochDutiesFutureEpochPreFulu() throws IOException {
    startRestApiAtGenesisWithValidatorApiHandler(SpecMilestone.ALTAIR);
    final List<SignedBlockAndState> chain = createBlocksAtSlots(1, 2, 3, 4, 5, 6, 7);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(syncStateProvider.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    final Response response = getProposerDuties("1");
    final String responseBody = response.body().string();
    logChainData(chain);
    assertThat(response.code()).isEqualTo(200);
    assertThat(dependentRoot(responseBody)).isEqualTo(chain.getLast().getRoot());
  }

  // epoch  GENESIS | 0                   |<1>
  // slot         0 | 1  2  3  4  5  6  7 |
  // query          |                   ^ |
  // dep          D |                     |
  // EXPECT epoch 1 duties with dependent root slot 0 (GENESIS)
  @Test
  void shouldReturnNextEpochDutiesFutureEpochPostFulu() throws IOException {
    startRestApiAtGenesisWithValidatorApiHandler(SpecMilestone.FULU);
    final List<SignedBlockAndState> chain = createBlocksAtSlots(1, 2, 3, 4, 5, 6, 7);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(syncStateProvider.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    logChainData(chain);
    final Response response = getProposerDuties("1");
    final String responseBody = response.body().string();
    assertThat(response.code()).isEqualTo(200);
    assertThat(dependentRoot(responseBody)).isEqualTo(chain.getFirst().getParentRoot());
  }

  // epoch  GENESIS | 0                   |<1>
  // slot         0 | 1  2  3  4  5  6  7 | 8
  // query          |                     | ^
  // dep          D |                     |
  // EXPECT epoch 1 duties with dependent root slot 0 (GENESIS)
  @Test
  void shouldReturnCorrectDependentRootPostFulu() throws IOException {
    startRestApiAtGenesisWithValidatorApiHandler(SpecMilestone.FULU);
    final List<SignedBlockAndState> chain = createBlocksAtSlots(1, 2, 3, 4, 5, 6, 7, 8);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(syncStateProvider.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    logChainData(chain);
    final Response response = getProposerDuties("1");
    final String responseBody = response.body().string();
    assertThat(response.code()).isEqualTo(200);
    assertThat(dependentRoot(responseBody)).isEqualTo(chain.getFirst().getParentRoot());
  }

  // epoch | 0                   | 1                      | <2>
  // slot  | 1  2  3  4  5  6  7 | 8  9 10 11 12 13 14 15 | 16
  // query |                     |                        |  ^
  // dep   |                     |                      D |
  // EXPECT - expect epoch 2 duties with dependent root slot 15
  @Test
  void shouldReturnCorrectDependentRootPreFuluExtraSlots() throws IOException {
    startRestApiAtGenesisWithValidatorApiHandler(SpecMilestone.BELLATRIX);
    final List<SignedBlockAndState> chain =
        createBlocksAtSlots(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(syncStateProvider.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    logChainData(chain);
    final Response response = getProposerDuties("2");
    final String responseBody = response.body().string();
    assertThat(response.code()).isEqualTo(200);
    assertThat(dependentRoot(responseBody)).isEqualTo(chain.getLast().getParentRoot());
  }

  // epoch  GENESIS | 0                   |<1>                     |  2
  // slot         0 | 1  2  3  4  5  6  7 | 8  9 10 11 12 13 14 15 | 16
  // query          |                     |                        |  ^
  // dep          D |                     |                        |
  // EXPECT - expect epoch 1 duties with dependent root slot 0 (GENESIS)
  @Test
  void shouldReturnCorrectDependentRootPostFuluExtraSlots() throws IOException {
    startRestApiAtGenesisWithValidatorApiHandler(SpecMilestone.FULU);
    final List<SignedBlockAndState> chain =
        createBlocksAtSlots(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(syncStateProvider.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    logChainData(chain);
    final Response response = getProposerDuties("1");
    final String responseBody = response.body().string();
    assertThat(response.code()).isEqualTo(200);
    assertThat(dependentRoot(responseBody)).isEqualTo(chain.getFirst().getParentRoot());
  }

  // epoch | 0                   | 1                      | <2>
  // slot  | 1  2  3  4  5  6  7 | 8  9 10 11 12 13 14 15 |
  // query |                     |                      ^ |
  // dep   |                   D |                        |
  // EXPECT - expect epoch 2 duties with dependent root slot 15
  @Test
  void shouldReturnCorrectDependentRootPostFuluExtraSlotsFutureEpoch() throws IOException {
    startRestApiAtGenesisWithValidatorApiHandler(SpecMilestone.FULU);
    final List<SignedBlockAndState> chain =
        createBlocksAtSlots(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(syncStateProvider.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    logChainData(chain);
    final Response response = getProposerDuties("2");
    final String responseBody = response.body().string();
    assertThat(response.code()).isEqualTo(200);
    assertThat(dependentRoot(responseBody)).isEqualTo(chain.get(6).getRoot());
  }

  // epoch | 0                   | 1                      | <2>
  // slot  | 1  2  3  4  5  6  7 | 8  9 10 11 12 13 14 15 | 16
  // query |                     |                        |  ^
  // dep   |                   D |                        |
  // EXPECT - expect epoch 2 duties with dependent root slot 15
  @Test
  void shouldReturnCorrectDependentRootSecondEpoch() throws IOException {
    startRestApiAtGenesisWithValidatorApiHandler(SpecMilestone.FULU);
    final List<SignedBlockAndState> chain =
        createBlocksAtSlots(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(syncStateProvider.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    logChainData(chain);
    final Response response = getProposerDuties("2");
    final String responseBody = response.body().string();
    assertThat(response.code()).isEqualTo(200);
    assertThat(dependentRoot(responseBody)).isEqualTo(chain.get(6).getRoot());
  }

  final Bytes32 dependentRoot(final String responseBody) {
    try {
      final JsonNode jsonNode = JsonTestUtil.parseAsJsonNode(responseBody);
      return Bytes32.fromHexString(jsonNode.get("dependent_root").asText());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Response getProposerDuties(final String epoch) throws IOException {
    return getResponse(GetProposerDuties.ROUTE.replace("{epoch}", epoch));
  }

  private void logChainData(final List<SignedBlockAndState> chain) {
    LOG.info(
        "Genesis root: {}",
        recentChainData.getGenesisData().orElseThrow().getGenesisValidatorsRoot());
    chain.forEach(
        signedBlockAndState ->
            LOG.info(
                "slot: {}, root: {}",
                signedBlockAndState.getSlot(),
                signedBlockAndState.getRoot()));
  }
}
