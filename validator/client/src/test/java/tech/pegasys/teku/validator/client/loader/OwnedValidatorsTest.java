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

package tech.pegasys.teku.validator.client.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.core.signatures.NoOpSigner.NO_OP_SIGNER;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.Validator;

class OwnedValidatorsTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(SpecFactory.createMinimal());
  private final OwnedValidators validators = new OwnedValidators();

  @Test
  void shouldAddNewValidator() {
    final Validator validator =
        new Validator(dataStructureUtil.randomPublicKey(), NO_OP_SIGNER, Optional::empty);
    assertThat(validators.getValidatorCount()).isZero();
    validators.addValidator(validator);

    assertThat(validators.getValidatorCount()).isEqualTo(1);
    assertThat(validators.getValidator(validator.getPublicKey())).contains(validator);
  }

  @Test
  void shouldRejectReplacingAnExistingValidator() {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    final Validator validator = new Validator(publicKey, NO_OP_SIGNER, Optional::empty);
    final Validator validator2 = new Validator(publicKey, NO_OP_SIGNER, Optional::empty);
    // Sanity check - if we ever add an equals method we'll have to find a new way to differentiate
    assertThat(validator2).isNotEqualTo(validator);
    validators.addValidator(validator);

    assertThatThrownBy(() -> validators.addValidator(validator2))
        .isInstanceOf(IllegalStateException.class);
    assertThat(validators.getValidator(publicKey)).contains(validator);
  }
}
