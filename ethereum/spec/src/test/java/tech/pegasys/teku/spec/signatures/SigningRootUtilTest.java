/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.signatures;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ValidatorRegistration;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SigningRootUtilTest {

  @Test
  void signingRootForValidatorRegistrationCalculatesSameValueForForks() {
    final Spec altairSpec = TestSpecFactory.createMinimalWithAltairForkEpoch(UInt64.valueOf(10));
    final SigningRootUtil signingRootUtil = new SigningRootUtil(altairSpec);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(altairSpec);

    final ValidatorRegistration validatorRegistration =
        dataStructureUtil.randomValidatorRegistration();
    final Bytes signingRootAtEpochOne =
        signingRootUtil.signingRootForValidatorRegistration(
            validatorRegistration, Optional.of(UInt64.ONE));
    final Bytes signingRootAtEpochAltair =
        signingRootUtil.signingRootForValidatorRegistration(
            validatorRegistration, Optional.of(UInt64.valueOf(11)));
    final Bytes signingRootWithoutEpoch =
        signingRootUtil.signingRootForValidatorRegistration(
            validatorRegistration, Optional.empty());

    assertThat(signingRootAtEpochOne)
        .isEqualTo(signingRootAtEpochAltair)
        .isEqualTo(signingRootWithoutEpoch);
  }
}
