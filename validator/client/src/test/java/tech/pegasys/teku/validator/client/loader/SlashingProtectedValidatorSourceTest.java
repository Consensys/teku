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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.signatures.SlashingProtector;

public class SlashingProtectedValidatorSourceTest {
  private final ValidatorSource delegate = mock(ValidatorSource.class);
  private final SlashingProtector slashingProtector = mock(SlashingProtector.class);
  private final ValidatorSource validatorSource =
      new SlashingProtectedValidatorSource(delegate, slashingProtector);

  @Test
  void shouldDelegateAddValidator() {
    validatorSource.addValidator(null, "pass");
    verify(delegate).addValidator(null, "pass");
  }

  @Test
  void shouldDelegateCanAddValidator() {
    validatorSource.canAddValidator();
    verify(delegate).canAddValidator();
  }

  @Test
  void availableValidators_shouldBeReadOnly() {
    final ValidatorSource.ValidatorProvider provider =
        mock(ValidatorSource.ValidatorProvider.class);
    when(provider.isReadOnly()).thenReturn(true);
    when(delegate.getAvailableValidators()).thenReturn(List.of(provider));
    assertThat(validatorSource.getAvailableValidators().get(0).isReadOnly()).isTrue();
  }
}
