/*
 * Copyright Consensys Software Inc., 2024
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.GraffitiProvider;
import tech.pegasys.teku.validator.client.loader.ValidatorSource.ValidatorProvider;

public class MultithreadedValidatorLoaderTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BLSPublicKey key1 = dataStructureUtil.randomPublicKey();
  private final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
  private final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
  private final GraffitiProvider graffitiProvider = Optional::empty;
  private final Signer signer1 = mock(Signer.class);
  private final Signer signer2 = mock(Signer.class);
  private final Signer signer3 = mock(Signer.class);
  private final ValidatorProvider validatorProvider1 = mock(ValidatorProvider.class);
  private final ValidatorProvider validatorProvider2 = mock(ValidatorProvider.class);
  private final ValidatorProvider validatorProvider3 = mock(ValidatorProvider.class);
  private final Map<BLSPublicKey, ValidatorProvider> sources =
      Map.of(key1, validatorProvider1, key2, validatorProvider2, key3, validatorProvider3);
  private OwnedValidators ownedValidators;

  @BeforeEach
  public void setup() {
    this.ownedValidators = new OwnedValidators();
    when(validatorProvider1.getPublicKey()).thenReturn(key1);
    when(validatorProvider1.createSigner()).thenReturn(signer1);
    when(validatorProvider2.getPublicKey()).thenReturn(key2);
    when(validatorProvider2.createSigner()).thenReturn(signer2);
    when(validatorProvider3.getPublicKey()).thenReturn(key3);
    when(validatorProvider3.createSigner()).thenReturn(signer3);
  }

  @Test
  public void shouldLoadValidators() {
    MultithreadedValidatorLoader.loadValidators(ownedValidators, sources, graffitiProvider);
    assertThat(ownedValidators.getPublicKeys()).contains(key1, key2, key3);
  }

  @Test
  public void shouldThrowIfValidatorIsNotLoaded() {
    when(validatorProvider3.createSigner()).thenThrow(new RuntimeException("123"));
    assertThatThrownBy(
            () ->
                MultithreadedValidatorLoader.loadValidators(
                    ownedValidators, sources, graffitiProvider))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("123");
    assertThat(ownedValidators.getPublicKeys()).isEmpty();
  }
}
