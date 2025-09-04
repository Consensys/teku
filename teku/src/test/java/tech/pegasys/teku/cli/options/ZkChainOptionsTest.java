/*
 * Copyright Consensys Software Inc., 2025
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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;

class ZkChainOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void statelessValidationEnabled_true() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xstateless-validation-enabled=true");
    assertThat(config.zkChainConfiguration().isStatelessValidationEnabled()).isTrue();
  }

  @Test
  public void generateExecutionProofsEnabled_true() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xgenerate-execution-proofs-enabled=true");
    assertThat(config.zkChainConfiguration().isGenerateExecutionProofsEnabled()).isTrue();
  }

  @Test
  public void statelessMinProofsRequired_receivesCorrectValue() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xstateless-min-proofs-required=2");
    assertThat(config.zkChainConfiguration().getStatelessMinProofsRequired()).isEqualTo(2);
  }

  @Test
  public void statelessMinProofsRequired_receivesDefaultValue() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.zkChainConfiguration().getStatelessMinProofsRequired()).isEqualTo(1);
  }

  @Test
  public void statelessValidationEnabled_isDisabledByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.zkChainConfiguration().isStatelessValidationEnabled()).isFalse();
  }

  @Test
  public void generateExecutionProofsEnabled_isDisabledByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.zkChainConfiguration().isGenerateExecutionProofsEnabled()).isFalse();
  }
}
