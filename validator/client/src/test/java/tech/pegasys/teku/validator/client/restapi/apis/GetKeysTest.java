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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.core.signatures.NoOpSigner.NO_OP_SIGNER;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.Validator;

class GetKeysTest {
  final KeyManager keyManager = Mockito.mock(KeyManager.class);

  @Test
  void shouldListValidatorKeys() throws Exception {
    final List<Validator> activeValidatorList = getValidatorList();
    when(keyManager.getActiveValidatorKeys()).thenReturn(activeValidatorList);
    final GetKeys endpoint = new GetKeys(keyManager);
    final RestApiRequest request = mock(RestApiRequest.class);
    endpoint.handle(request);

    verify(request).respondOk(activeValidatorList);
  }

  @Test
  void shouldListEmpytValidatorKeys() throws Exception {
    final List<Validator> activeValidatorList = Collections.emptyList();
    when(keyManager.getActiveValidatorKeys()).thenReturn(activeValidatorList);
    final GetKeys endpoint = new GetKeys(keyManager);
    final RestApiRequest request = mock(RestApiRequest.class);
    endpoint.handle(request);

    verify(request).respondOk(activeValidatorList);
  }

  private List<Validator> getValidatorList() {
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    Validator validator1 =
        new Validator(keyPair1.getPublicKey(), NO_OP_SIGNER, Optional::empty, true);
    Validator validator2 =
        new Validator(keyPair2.getPublicKey(), NO_OP_SIGNER, Optional::empty, false);
    return Arrays.asList(validator1, validator2);
  }
}
