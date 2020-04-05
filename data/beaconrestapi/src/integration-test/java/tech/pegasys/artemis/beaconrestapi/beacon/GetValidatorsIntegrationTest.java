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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.AbstractBeaconRestAPIIntegrationTest;
import tech.pegasys.artemis.beaconrestapi.RestApiConstants;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetValidators;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.async.SafeFuture;

public class GetValidatorsIntegrationTest extends AbstractBeaconRestAPIIntegrationTest {

  @Test
  public void shouldReturnNoContentIfStoreNotDefined_implicitlyQueryLatest() throws Exception {
    when(recentChainData.getStore()).thenReturn(null);

    final Response response = getLatest();
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfHeadRootMissing_implicitlyQueryLatest() throws Exception {
    final Store store = mock(Store.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.empty());

    final Response response = getLatest();
    assertNoContent(response);
  }

  @Test
  public void handleMissingState_implicitlyQueryLatest() throws Exception {
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();

    final Store store = mock(Store.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.of(headRoot));
    when(recentChainData.getBlockState(headRoot)).thenReturn(Optional.empty());
    when(historicalChainData.getFinalizedStateByBlockRoot(headRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final Response response = getLatest();
    assertNotFound(response);
  }

  @Test
  public void shouldReturnNoContentIfStoreNotDefined_queryByEpoch() throws Exception {
    when(recentChainData.getStore()).thenReturn(null);
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.ZERO);

    final Response response = getByEpoch(1);
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfHeadRootMissing_queryByEpoch() throws Exception {
    final Store store = mock(Store.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.ZERO);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.empty());

    final Response response = getByEpoch(1);
    assertNoContent(response);
  }

  @Test
  public void handleMissingFinalizedState_queryByEpoch() throws Exception {
    final int epoch = 1;
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();

    final Store store = mock(Store.class);
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(epoch));
    when(recentChainData.getStore()).thenReturn(store);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.of(headRoot));
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(epoch));
    when(historicalChainData.getFinalizedStateAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final Response response = getByEpoch(epoch);
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
