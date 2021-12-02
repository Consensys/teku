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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.signatures.SlashingProtector;

class MutableLocalValidatorSourceTest {
  private final LocalValidatorSource delegate = mock(LocalValidatorSource.class);
  private final SlashingProtector slashingProtector = mock(SlashingProtector.class);
  final MutableLocalValidatorSource validatorSource =
      new MutableLocalValidatorSource(delegate, slashingProtector);

  @Test
  void shouldCreateMutableValidatorSource() {
    assertThatThrownBy(() -> validatorSource.addValidator(null, "pass"))
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  void canAddValidator_shouldBeTrue() {
    assertThat(validatorSource.canAddValidator()).isTrue();
  }

  @Test
  void availableValidators_shouldNotBeReadOnly() {
    when(delegate.getAvailableValidators())
        .thenReturn(List.of(mock(ValidatorSource.ValidatorProvider.class)));
    assertThat(validatorSource.getAvailableValidators().get(0).isReadOnly()).isFalse();
  }
}
