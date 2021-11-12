/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.restapi.apis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.restapi.JsonTestUtil;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.KeyManager;

class GetKeysTest {
  final KeyManager keyManager = Mockito.mock(KeyManager.class);

  @Test
  void metadata_shouldProduceCorrectOpenApi() throws Exception {
    final GetKeys endpoint = new GetKeys(keyManager);
    final String json = JsonTestUtil.serializeEndpointMetadata(endpoint);
    final Map<String, Object> result = JsonTestUtil.parse(json);

    final Map<String, Object> expected =
        JsonTestUtil.parseJsonResource(GetKeysTest.class, "GetKeys.json");

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void shouldListValidatorKeys() throws Exception {
    final Set<BLSPublicKey> validatorKeys = getList();
    when(keyManager.getValidatorKeys()).thenReturn(validatorKeys);
    final GetKeys endpoint = new GetKeys(keyManager);
    final RestApiRequest request = mock(RestApiRequest.class);
    endpoint.handle(request);

    verify(request).respondOk(List.copyOf(validatorKeys));
  }

  @Test
  void shouldListEmpytValidatorKeys() throws Exception {
    final List<BLSPublicKey> validatorKeys = Collections.emptyList();
    final GetKeys endpoint = new GetKeys(keyManager);
    final RestApiRequest request = mock(RestApiRequest.class);
    endpoint.handle(request);

    verify(request).respondOk(List.copyOf(validatorKeys));
  }

  private Set<BLSPublicKey> getList() {
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    BLSKeyPair keyPair3 = BLSTestUtil.randomKeyPair(3);

    return new HashSet<>(
        Arrays.asList(keyPair1.getPublicKey(), keyPair2.getPublicKey(), keyPair3.getPublicKey()));
  }
}
