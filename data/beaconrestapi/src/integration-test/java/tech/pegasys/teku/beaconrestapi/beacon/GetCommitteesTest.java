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

import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.Committee;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.RestApiConstants;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetCommittees;
import tech.pegasys.teku.core.ChainProperties;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

public class GetCommitteesTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldReturnNoContentIfStoreNotDefined() throws Exception {
    startPreGenesisRestAPI();
    final UnsignedLong epoch = UnsignedLong.ONE;

    final Response response = getByEpoch(epoch.intValue());
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfHeadRootUnavailable() throws Exception {
    startPreForkChoiceRestAPI();
    final UnsignedLong epoch = UnsignedLong.ONE;

    final Response response = getByEpoch(epoch.intValue());
    assertNoContent(response);
  }

  @Test
  void shouldGetCommitteesAtCurrentEpochWhenBlockIsMissing() throws Exception {
    startRestAPIAtGenesis(StateStorageMode.PRUNE);
    final int EPOCH = 2;
    createBlocksAtSlotsAndMapToApiResult(9, 10, 20);

    List<Committee> result = getCommitteesByEpoch(EPOCH);
    assertThat(result.get(0).slot).isEqualTo(firstSlotInEpoch(EPOCH));
  }

  @Test
  void shouldGetCommitteesAtNextEpoch() throws Exception {
    startRestAPIAtGenesis(StateStorageMode.PRUNE);
    final int EPOCH = 3;
    createBlocksAtSlotsAndMapToApiResult(9, 10, 20);

    List<Committee> result = getCommitteesByEpoch(EPOCH);
    assertThat(result.get(0).slot).isEqualTo(firstSlotInEpoch(EPOCH));
  }

  @Test
  void shouldGetCommitteesAtFutureEpoch() throws Exception {
    startRestAPIAtGenesis(StateStorageMode.PRUNE);
    createBlocksAtSlotsAndMapToApiResult(9, 10, 20);

    // currently at epoch 2, epoch 3 will be available, but 4 will be missing,
    // as committees are only calculated for 1 future epoch
    final Response response = getByEpoch(4);
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  void shouldGetCommitteesAtCurrentEpochWithBlockPresent() throws Exception {
    startRestAPIAtGenesis(StateStorageMode.PRUNE);
    final int EPOCH = 1;
    createBlocksAtSlotsAndMapToApiResult(7, 8, 9, 10);

    List<Committee> result = getCommitteesByEpoch(EPOCH);
    assertThat(result.get(0).slot).isEqualTo(firstSlotInEpoch(EPOCH));
  }

  @Test
  void shouldGetCommitteesAtNextEpochWithBlockPresent() throws Exception {
    startRestAPIAtGenesis(StateStorageMode.PRUNE);
    final int EPOCH = 2;
    createBlocksAtSlotsAndMapToApiResult(7, 8, 9, 10);

    List<Committee> result = getCommitteesByEpoch(EPOCH);
    assertThat(result.get(0).slot).isEqualTo(firstSlotInEpoch(EPOCH));
  }

  @Test
  void shouldHandleMissingFinalizedEpoch() throws Exception {
    startRestAPIAtGenesis(StateStorageMode.PRUNE);
    final int targetSlot = 20;
    final int finalizedSlot = 20 + Constants.SLOTS_PER_HISTORICAL_ROOT;
    createBlocksAtSlots(targetSlot, finalizedSlot);
    final UnsignedLong finalizedEpoch =
        ChainProperties.computeBestEpochFinalizableAtSlot(finalizedSlot);
    final SignedBlockAndState finalizedBlock = finalizeChainAtEpoch(finalizedEpoch);
    assertThat(finalizedBlock.getSlot()).isEqualTo(UnsignedLong.valueOf(finalizedSlot));

    final int targetEpoch = finalizedEpoch.minus(UnsignedLong.ONE).intValue();
    final Response response = getByEpoch(targetEpoch);
    assertThat(response.code()).isEqualTo(SC_GONE);
  }

  private List<Committee> getCommitteesByEpoch(final int epoch) throws IOException {
    final Response response = getByEpoch(epoch);
    final String responseBody = response.body().string();
    assertThat(response.code()).isEqualTo(SC_OK);
    final Committee[] result = jsonProvider.jsonToObject(responseBody, Committee[].class);
    return List.of(result);
  }

  private UnsignedLong firstSlotInEpoch(final int epoch) {
    return BeaconStateUtil.compute_start_slot_at_epoch(UnsignedLong.valueOf(epoch));
  }

  private Response getByEpoch(final int epoch) throws IOException {
    return getResponse(
        GetCommittees.ROUTE, Map.of(RestApiConstants.EPOCH, Integer.toString(epoch, 10)));
  }
}
