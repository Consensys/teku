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
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.RestApiConstants;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.GetValidators;
import tech.pegasys.teku.core.ChainProperties;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

public class GetValidatorsIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldReturnNoContentIfStoreNotDefined_implicitlyQueryLatest() throws Exception {
    startPreGenesisRestAPI();

    final Response response = getLatest();
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfPreForkChoice_implicitlyQueryLatest() throws Exception {
    startPreForkChoiceRestAPI();

    final Response response = getLatest();
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfStoreNotDefined_queryByEpoch() throws Exception {
    startPreGenesisRestAPI();

    final Response response = getByEpoch(1);
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfPreForkChoice_queryByEpoch() throws Exception {
    startPreForkChoiceRestAPI();

    final Response response = getByEpoch(1);
    assertNoContent(response);
  }

  @Test
  public void handleMissingFinalizedState_queryByEpoch() throws Exception {
    startRestAPIAtGenesis(StateStorageMode.PRUNE);
    final int outOfRangeSlot = 20;
    final int finalizedSlot = 20 + Constants.SLOTS_PER_HISTORICAL_ROOT;
    createBlocksAtSlots(outOfRangeSlot, finalizedSlot);
    final UnsignedLong finalizedEpoch =
        ChainProperties.computeBestEpochFinalizableAtSlot(finalizedSlot);
    final SignedBlockAndState finalizedBlock = finalizeChainAtEpoch(finalizedEpoch);
    assertThat(finalizedBlock.getSlot()).isEqualTo(UnsignedLong.valueOf(finalizedSlot));

    final int targetEpoch = finalizedEpoch.minus(UnsignedLong.ONE).intValue();
    final Response response = getByEpoch(targetEpoch);
    assertGone(response);
  }

  private Response getLatest() throws IOException {
    return getResponse(GetValidators.ROUTE);
  }

  private Response getByEpoch(final int epoch) throws IOException {
    return getResponse(
        GetValidators.ROUTE, Map.of(RestApiConstants.EPOCH, Integer.toString(epoch, 10)));
  }
}
