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

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.AbstractBeaconRestAPIIntegrationTest;
import tech.pegasys.artemis.beaconrestapi.RestApiConstants;
import tech.pegasys.artemis.beaconrestapi.handlers.validator.PostDuties;
import tech.pegasys.artemis.util.bls.BLSKeyGenerator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

public class PostDutiesIntegrationTest extends AbstractBeaconRestAPIIntegrationTest {

  private static final List<BLSKeyPair> keys = BLSKeyGenerator.generateKeyPairs(1);

  @Test
  public void shouldReturnNoContentIfStoreNotDefined_queryByEpoch() throws Exception {
    when(chainStorageClient.getStore()).thenReturn(null);

    final Response response = post(1, keys);
    assertThat(response.code()).isEqualTo(SC_NO_CONTENT);
    assertThat(response.body().string()).isEmpty();
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
