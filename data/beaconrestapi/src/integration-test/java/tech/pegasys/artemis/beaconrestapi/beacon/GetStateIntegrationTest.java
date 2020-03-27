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
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetState;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.async.SafeFuture;

public class GetStateIntegrationTest extends AbstractBeaconRestAPIIntegrationTest {

  @Test
  public void shouldReturnNoContentIfStoreNotDefined_queryByRoot() throws Exception {
    when(chainStorageClient.getStore()).thenReturn(null);
    when(chainStorageClient.getFinalizedEpoch()).thenReturn(UnsignedLong.ZERO);

    final Response response = getByRoot(Bytes32.ZERO);
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfStoreNotDefined_queryBySlot() throws Exception {
    when(chainStorageClient.getStore()).thenReturn(null);
    when(chainStorageClient.getFinalizedEpoch()).thenReturn(UnsignedLong.ZERO);

    final Response response = getBySlot(1);
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfHeadRootMissing_queryBySlot() throws Exception {
    final Store store = mock(Store.class);
    when(chainStorageClient.getStore()).thenReturn(store);
    when(chainStorageClient.getFinalizedEpoch()).thenReturn(UnsignedLong.ZERO);
    when(chainStorageClient.getBestBlockRoot()).thenReturn(Optional.empty());

    final Response response = getBySlot(1);
    assertNoContent(response);
  }

  @Test
  public void handleMissingFinalizedState_queryBySlot() throws Exception {
    final int slot = 1;
    final int finalizedEpoch = 2;
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();
    final SafeFuture<Optional<BeaconState>> emptyStateResult =
        SafeFuture.completedFuture(Optional.empty());

    final Store store = mock(Store.class);
    when(chainStorageClient.getStore()).thenReturn(store);
    when(chainStorageClient.getBestBlockRoot()).thenReturn(Optional.of(headRoot));
    when(chainStorageClient.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(finalizedEpoch));
    when(historicalChainData.getFinalizedStateAtSlot(UnsignedLong.valueOf(slot)))
        .thenReturn(emptyStateResult);

    final Response response = getBySlot(slot);
    assertGone(response);
  }

  @Test
  public void handleMissingNonFinalizedState_queryBySlot() throws Exception {
    final int slot = 1;
    final int finalizedEpoch = 0;
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();

    final Store store = mock(Store.class);
    when(chainStorageClient.getStore()).thenReturn(store);
    when(chainStorageClient.getBestBlockRoot()).thenReturn(Optional.of(headRoot));
    when(chainStorageClient.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(finalizedEpoch));
    when(store.getBlockState(headRoot)).thenReturn(dataStructureUtil.randomBeaconState(100));
    when(chainStorageClient.getStateBySlot(UnsignedLong.valueOf(slot)))
        .thenReturn(Optional.empty());

    final Response response = getBySlot(slot);
    assertNotFound(response);
  }

  @Test
  public void handleMissingState_queryByRoot() throws Exception {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    final SafeFuture<Optional<BeaconState>> emptyStateResult =
        SafeFuture.completedFuture(Optional.empty());

    final Store store = mock(Store.class);
    when(chainStorageClient.getStore()).thenReturn(store);
    when(chainStorageClient.getBlockState(root)).thenReturn(Optional.empty());
    when(historicalChainData.getFinalizedStateByBlockRoot(root)).thenReturn(emptyStateResult);

    final Response response = getByRoot(root);
    assertNotFound(response);
  }

  private Response getByRoot(final Bytes32 root) throws IOException {
    return getResponse(GetState.ROUTE, Map.of(RestApiConstants.ROOT, root.toHexString()));
  }

  private Response getBySlot(final int slot) throws IOException {
    return getResponse(GetState.ROUTE, Map.of(RestApiConstants.SLOT, Integer.toString(slot, 10)));
  }
}
