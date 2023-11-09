/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;

class ValidatorKeysOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldFailWhenNotAllowNoLoadedKeys() {
    assertThatThrownBy(() -> getTekuConfigurationFromArguments("--allow-no-loaded-keys", "false"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("No validator external signer keys are loaded");
  }

  @Test
  public void shouldAllowNoLoadedKeysIfSet() {
    final List<String> validatorExternalSignerPublicKeySources =
        getTekuConfigurationFromArguments("--allow-no-loaded-keys", "true")
            .validatorClient()
            .getValidatorConfig()
            .getValidatorExternalSignerPublicKeySources();
    assertThat(validatorExternalSignerPublicKeySources).isEmpty();
  }
}
