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
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.GetCommittees;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.async.SafeFuture;

public class GetCommitteesIntegrationTest extends AbstractBeaconRestAPIIntegrationTest {

  @Test
  public void shouldReturnNoContentIfStoreNotDefined() throws Exception {
    final UnsignedLong epoch = UnsignedLong.ONE;
    when(recentChainData.getStore()).thenReturn(null);
    when(recentChainData.getFinalizedEpoch()).thenReturn(epoch);

    final Response response = getByEpoch(epoch.intValue());
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfHeadRootUnavailable() throws Exception {
    final UnsignedLong epoch = UnsignedLong.ONE;

    final Store store = mock(Store.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(recentChainData.getFinalizedEpoch()).thenReturn(epoch);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.empty());

    final Response response = getByEpoch(epoch.intValue());
    assertNoContent(response);
  }

  @Test
  public void handleMissingState() throws Exception {
    final int epoch = 1;
    final Bytes32 root = dataStructureUtil.randomBytes32();
    final SafeFuture<Optional<BeaconState>> emptyStateResult =
        SafeFuture.completedFuture(Optional.empty());

    final Store store = mock(Store.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.of(root));
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(epoch));
    when(historicalChainData.getFinalizedStateByBlockRoot(root)).thenReturn(emptyStateResult);

    final Response response = getByEpoch(epoch);
    assertGone(response);
  }

  private Response getByEpoch(final int epoch) throws IOException {
    return getResponse(
        GetCommittees.ROUTE, Map.of(RestApiConstants.EPOCH, Integer.toString(epoch, 10)));
  }
}
