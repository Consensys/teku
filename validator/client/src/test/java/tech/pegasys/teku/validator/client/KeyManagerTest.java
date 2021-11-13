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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ActiveValidator;

class KeyManagerTest {

  final ValidatorLoader validatorLoader = Mockito.mock(ValidatorLoader.class);
  final OwnedValidators ownedValidators = Mockito.mock(OwnedValidators.class);

  @Test
  void shouldReturnActiveValidatorsList() {
    final KeyManager keyManager = new KeyManager(validatorLoader);
    List<ActiveValidator> validatorsList = generateActiveValidatosList();
    when(ownedValidators.getActiveValidators()).thenReturn(validatorsList);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    List<ActiveValidator> activeValidatorList = keyManager.getActiveValidatorKeys();

    assertThat(activeValidatorList).isEqualTo(validatorsList);
  }

  @Test
  void shouldReturnEmptyKeyList() {
    final KeyManager keyManager = new KeyManager(validatorLoader);
    List<ActiveValidator> validatorsList = Collections.emptyList();
    when(ownedValidators.getActiveValidators()).thenReturn(validatorsList);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    List<ActiveValidator> activeValidatorList = keyManager.getActiveValidatorKeys();

    assertThat(activeValidatorList).isEmpty();
  }

  private List<ActiveValidator> generateActiveValidatosList() {
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    List<ActiveValidator> activeValidatorList = new ArrayList<>();
    activeValidatorList.add(new ActiveValidator(keyPair1.getPublicKey(), true));
    activeValidatorList.add(new ActiveValidator(keyPair2.getPublicKey(), true));
    return activeValidatorList;
  }
}
