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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.AbstractBeaconRestAPIIntegrationTest;
import tech.pegasys.artemis.beaconrestapi.RestApiConstants;
import tech.pegasys.artemis.beaconrestapi.handlers.validator.PostDuties;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.bls.BLSKeyGenerator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

public class PostDutiesIntegrationTest extends AbstractBeaconRestAPIIntegrationTest {

  private static final List<BLSKeyPair> keys = BLSKeyGenerator.generateKeyPairs(1);

  @Test
  public void shouldReturnNoContentIfStoreNotDefined() throws Exception {
    final UnsignedLong epoch = UnsignedLong.ONE;
    when(chainStorageClient.getStore()).thenReturn(null);
    when(chainStorageClient.getFinalizedEpoch()).thenReturn(epoch);

    final Response response = post(epoch.intValue(), keys);
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentWhenBestBlockRootMissing() throws Exception {
    final UnsignedLong epoch = UnsignedLong.ONE;

    final Store store = mock(Store.class);
    when(chainStorageClient.getStore()).thenReturn(store);
    when(chainStorageClient.getFinalizedEpoch()).thenReturn(epoch);
    when(chainStorageClient.getBestBlockRoot()).thenReturn(Optional.empty());

    final Response response = post(epoch.intValue(), keys);
    assertNoContent(response);
  }

  @Test
  public void shouldReturnEmptyListWhenNoPubKeysSupplied() throws Exception {
    final UnsignedLong epoch = UnsignedLong.ONE;
    when(chainStorageClient.getFinalizedEpoch()).thenReturn(epoch);

    final Response response = post(epoch.intValue(), Collections.emptyList());
    assertBodyEquals(response, "[]");
  }

  private Response post(final int epoch, final List<BLSKeyPair> publicKeys) throws IOException {
    final List<String> publicKeyStrings =
        publicKeys.stream()
            .map(k -> k.getPublicKey().toBytes().toHexString())
            .collect(Collectors.toList());

    final Map<String, Object> params =
        Map.of(RestApiConstants.EPOCH, epoch, "pubkeys", publicKeyStrings);
    return post(PostDuties.ROUTE, mapToJson(params));
  }
}
