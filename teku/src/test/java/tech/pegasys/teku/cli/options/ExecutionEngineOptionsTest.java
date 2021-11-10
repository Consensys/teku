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

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;

public class ExecutionEngineOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadExecutionEngineOptionsFromConfigurationFile() {
    final TekuConfiguration config =
        getTekuConfigurationFromFile("executionEngineOptions_config.yaml");

    assertThat(config.executionEngine().isEnabled()).isTrue();
    assertThat(config.executionEngine().getEndpoints())
        .containsExactly("http://example.com:1234/path/", "http://example2.com:1234/path/");
  }

  @Test
  public void shouldReportEEEnabledIfEndpointSpecified() {
    final String[] args = {"--Xee-endpoint", "http://example.com:1234/path/"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.executionEngine().isEnabled()).isTrue();
  }

  @Test
  public void shouldReportEEDisabledIfEndpointNotSpecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.executionEngine().isEnabled()).isFalse();
  }

  @Test
  public void shouldReportEEDisabledIfEndpointIsEmpty() {
    final String[] args = {"--Xee-endpoint", "   "};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.executionEngine().isEnabled()).isFalse();
  }

  @Test
  public void multiple_eeEndpoints_areSupported() {
    final String[] args = {
      "--Xee-endpoints",
      "http://example.com:1234/path/,http://example-2.com:1234/path/",
      "http://example-3.com:1234/path/"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.executionEngine().getEndpoints())
        .containsExactlyInAnyOrder(
            "http://example.com:1234/path/",
            "http://example-2.com:1234/path/",
            "http://example-3.com:1234/path/");
    assertThat(config.executionEngine().isEnabled()).isTrue();
  }

  @Test
  public void multiple_eeEndpoints_areSupported_mixedParams() {
    final String[] args = {
      "--Xee-endpoint",
      "http://example-single.com:1234/path/",
      "--Xee-endpoints",
      "http://example.com:1234/path/,http://example-2.com:1234/path/",
      "http://example-3.com:1234/path/"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.executionEngine().getEndpoints())
        .containsExactlyInAnyOrder(
            "http://example-single.com:1234/path/",
            "http://example.com:1234/path/",
            "http://example-2.com:1234/path/",
            "http://example-3.com:1234/path/");
    assertThat(config.executionEngine().isEnabled()).isTrue();
  }

  @Test
  public void ShouldReportEmptyIfFeeRecipientNotSpecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.executionEngine().getFeeRecipient()).isEmpty();
  }

  @Test
  public void ShouldReportAddressIfFeeRecipientSpecified() {
    final String[] args = {
      "--Xee-fee-recipient-address", "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.executionEngine().getFeeRecipient())
        .isEqualTo(
            Optional.of(Eth1Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73")));
  }
}
