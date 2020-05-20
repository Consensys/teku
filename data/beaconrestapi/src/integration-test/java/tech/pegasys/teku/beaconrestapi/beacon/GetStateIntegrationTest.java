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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.RestApiConstants;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetState;
import tech.pegasys.teku.core.ChainProperties;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

public class GetStateIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldReturnNoContentIfStoreNotDefined_queryByRoot() throws Exception {
    startPreGenesisRestAPI();

    final Response response = getByRoot(Bytes32.ZERO);
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfStoreNotDefined_queryBySlot() throws Exception {
    startPreGenesisRestAPI();

    final Response response = getBySlot(1);
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfHeadRootMissing_queryBySlot() throws Exception {
    startPreForkChoiceRestAPI();

    final Response response = getBySlot(1);
    assertNoContent(response);
  }

  @Test
  public void handleMissingFinalizedState_queryBySlot() throws Exception {
    startRestAPIAtGenesis(StateStorageMode.PRUNE);

    final int targetSlot = 20;
    final int finalizedSlot = 20 + Constants.SLOTS_PER_HISTORICAL_ROOT;
    createBlocksAtSlots(targetSlot, finalizedSlot);
    final UnsignedLong finalizedEpoch =
        ChainProperties.computeBestEpochFinalizableAtSlot(finalizedSlot);
    final SignedBlockAndState finalizedBlock = finalizeChainAtEpoch(finalizedEpoch);
    assertThat(finalizedBlock.getSlot()).isEqualTo(UnsignedLong.valueOf(finalizedSlot));

    final Response response = getBySlot(targetSlot);
    assertGone(response);
  }

  @Test
  public void handleMissingState_queryByRoot() throws Exception {
    startRestAPIAtGenesis(StateStorageMode.PRUNE);

    final int targetSlot = 20;
    final int finalizedSlot = 21;
    final List<SignedBlockAndState> blocks = createBlocksAtSlots(targetSlot, finalizedSlot);
    final UnsignedLong finalizedEpoch =
        ChainProperties.computeBestEpochFinalizableAtSlot(finalizedSlot);
    final SignedBlockAndState finalizedBlock = finalizeChainAtEpoch(finalizedEpoch);
    assertThat(finalizedBlock.getSlot()).isEqualTo(UnsignedLong.valueOf(finalizedSlot));

    final Response response = getByRoot(blocks.get(0).getRoot());
    assertNotFound(response);
  }

  private Response getByRoot(final Bytes32 root) throws IOException {
    return getResponse(GetState.ROUTE, Map.of(RestApiConstants.ROOT, root.toHexString()));
  }

  private Response getBySlot(final int slot) throws IOException {
    return getResponse(GetState.ROUTE, Map.of(RestApiConstants.SLOT, Integer.toString(slot, 10)));
  }
}
