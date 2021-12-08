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

package tech.pegasys.teku.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ExecutionEngineOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadExecutionEngineOptionsFromConfigurationFile() {
    final TekuConfiguration config =
        getTekuConfigurationFromFile("executionEngineOptions_config.yaml");

    // Spec doesn't include the merge so execution engine is disabled
    assertThat(config.executionEngine().isEnabled()).isFalse();
    assertThat(config.executionEngine().getEndpoint()).isEqualTo("http://example.com:1234/path/");
  }

  @Test
  public void shouldReportEEEnabledIfSpecEnablesMerge() {
    final String[] args = {
      "--Xnetwork-altair-fork-epoch",
      "0",
      "--Xnetwork-merge-fork-epoch",
      "1",
      "--Xee-endpoint",
      "http://example.com:1234/path/"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.executionEngine().isEnabled()).isTrue();

    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(b -> b.altairForkEpoch(UInt64.ZERO).mergeForkEpoch(UInt64.ONE))
                .executionEngine(b -> b.endpoint("http://example.com:1234/path/"))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  public void shouldReportEEDisabledIfEndpointNotSpecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.executionEngine().isEnabled()).isFalse();
  }

  @Test
  void shouldThrowInvalidConfigurationExceptionIfEndpointRequiredButNotSpecified() {
    final String[] args = {"--Xnetwork-altair-fork-epoch", "0", "--Xnetwork-merge-fork-epoch", "1"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThatThrownBy(config.executionEngine()::getEndpoint)
        .isInstanceOf(InvalidConfigurationException.class);
  }
}
