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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.AbstractBeaconRestAPIIntegrationTest;
import tech.pegasys.artemis.beaconrestapi.RestApiConstants;
import tech.pegasys.artemis.beaconrestapi.handlers.beacon.PostValidators;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSKeyGenerator;
import tech.pegasys.artemis.bls.bls.BLSKeyPair;

public class PostValidatorsIntegrationTest extends AbstractBeaconRestAPIIntegrationTest {

  private static final List<BLSKeyPair> keys = BLSKeyGenerator.generateKeyPairs(1);

  @Test
  public void shouldReturnNoContentIfStoreNotDefined() throws Exception {
    when(recentChainData.getStore()).thenReturn(null);
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.ZERO);

    final Response response = post(1, keys);
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentWhenBestBlockRootMissing() throws Exception {
    final Store store = mock(Store.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.ZERO);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.empty());

    final Response response = post(1, keys);
    assertNoContent(response);
  }

  @Test
  public void shouldHandleMissingFinalizedState() throws Exception {
    final int epoch = 1;
    final Bytes32 root = dataStructureUtil.randomBytes32();
    final Store store = mock(Store.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(epoch));
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.of(root));
    when(historicalChainData.getFinalizedStateAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final Response response = post(1, keys);
    assertGone(response);
  }

  @Test
  public void shouldReturnEmptyListIfWhenPubKeysIsEmpty() throws Exception {
    final int epoch = 1;
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(epoch));

    final Response response = post(1, Collections.emptyList());
    assertBodyEquals(response, "[]");
  }

  private Response post(final int epoch, final List<BLSKeyPair> publicKeys) throws IOException {
    final List<String> publicKeyStrings =
        publicKeys.stream()
            .map(k -> k.getPublicKey().toBytes().toHexString())
            .collect(Collectors.toList());

    final Map<String, Object> params =
        Map.of(RestApiConstants.EPOCH, epoch, "pubkeys", publicKeyStrings);
    return post(PostValidators.ROUTE, mapToJson(params));
  }
}
