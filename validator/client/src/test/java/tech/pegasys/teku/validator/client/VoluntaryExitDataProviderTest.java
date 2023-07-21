/*
 * Copyright ConsenSys Software Inc., 2023
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class VoluntaryExitDataProviderTest {
  private final Spec spec = TestSpecFactory.createMinimal(SpecMilestone.CAPELLA);
  private final KeyManager keyManager = mock(KeyManager.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100000);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private VoluntaryExitDataProvider provider;

  @BeforeEach
  void setUp() {
    provider = new VoluntaryExitDataProvider(spec, keyManager, timeProvider);
  }

  @Test
  void calculateCurrentEpoch_shouldReturnEpoch() {
    final UInt64 epoch = provider.calculateCurrentEpoch(UInt64.ZERO);
    final UInt64 expectedEpoch = UInt64.valueOf(2083);
    assertThat(epoch).isEqualTo(expectedEpoch);
  }

  @Test
  void createSignedVoluntaryExit_shouldReturnSignedVoluntaryExit() {
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final Validator activeValidator = getValidator(signature);
    final int validatorIndex = 0;
    when(keyManager.getActiveValidatorKeys()).thenReturn(List.of(activeValidator));

    final UInt64 epoch = dataStructureUtil.randomEpoch();
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();

    final SignedVoluntaryExit signedVoluntaryExit =
        provider.createSignedVoluntaryExit(
            validatorIndex, activeValidator.getPublicKey(), epoch, forkInfo);

    final SignedVoluntaryExit expected =
        new SignedVoluntaryExit(
            new VoluntaryExit(epoch, UInt64.valueOf(validatorIndex)), signature);
    assertThat(signedVoluntaryExit).isEqualTo(expected);
  }

  private Validator getValidator(final BLSSignature signature) {
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    Signer signer = mock(Signer.class);
    when(signer.signVoluntaryExit(any(), any())).thenReturn(SafeFuture.completedFuture(signature));
    return new Validator(keyPair1.getPublicKey(), signer, Optional::empty, true);
  }
}
