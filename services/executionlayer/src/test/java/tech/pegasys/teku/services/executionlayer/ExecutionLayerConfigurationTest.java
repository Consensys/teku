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

package tech.pegasys.teku.services.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class ExecutionLayerConfigurationTest {
  private final ExecutionLayerConfiguration.Builder configBuilder =
      ExecutionLayerConfiguration.builder();
  private final Spec bellatrixSpec = TestSpecFactory.createMinimalBellatrix();

  @Test
  public void shouldThrowExceptionIfNoEeEndpointSpecified() {
    final ExecutionLayerConfiguration config = configBuilder.specProvider(bellatrixSpec).build();

    assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(config::getEngineEndpoint)
        .withMessageContaining(
            "Invalid configuration. --ee-endpoint parameter is mandatory when Bellatrix milestone is enabled");
  }

  @Test
  public void shouldThrowExceptionIfBidChallengePercentageNegative() {
    final ExecutionLayerConfiguration.Builder builder =
        configBuilder.specProvider(bellatrixSpec).builderBidChallengePercentage("-100");

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(builder::build)
        .withMessageContaining("Builder bid value challenge percentage should be >= 0");
  }

  @Test
  public void shouldThrowExceptionIfBidChallengePercentageWrongWord() {
    final ExecutionLayerConfiguration.Builder builder =
        configBuilder.specProvider(bellatrixSpec).builderBidChallengePercentage("OUCH");

    assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Expecting number, percentage or NEVER keyword for Builder bid challenge percentage");
  }

  @Test
  public void shouldParseBidChallengePercentage() {
    final ExecutionLayerConfiguration.Builder builder1 =
        configBuilder.specProvider(bellatrixSpec).builderBidChallengePercentage("50");
    assertThat(builder1.build().getBuilderBidChallengePercentage()).contains(50);
    final ExecutionLayerConfiguration.Builder builder2 =
        configBuilder.specProvider(bellatrixSpec).builderBidChallengePercentage("50%");
    assertThat(builder2.build().getBuilderBidChallengePercentage()).contains(50);
    final ExecutionLayerConfiguration.Builder builder3 =
        configBuilder.specProvider(bellatrixSpec).builderBidChallengePercentage("NeveR");
    assertThat(builder3.build().getBuilderBidChallengePercentage()).isEmpty();
  }

  @Test
  public void shouldHaveCorrectDefaultBidChallengePercentage() {
    final ExecutionLayerConfiguration.Builder builder1 = configBuilder.specProvider(bellatrixSpec);
    assertThat(builder1.build().getBuilderBidChallengePercentage()).contains(100);
  }

  @Test
  public void noExceptionThrownIfEeEndpointSpecified() {
    final ExecutionLayerConfiguration config =
        configBuilder.specProvider(bellatrixSpec).engineEndpoint("someEndpoint").build();

    assertThatCode(config::getEngineEndpoint).doesNotThrowAnyException();
  }

  @Test
  public void exchangeCapabilitiesToggleIsOffByDefault() {
    final ExecutionLayerConfiguration config = configBuilder.build();

    assertThat(config.isExchangeCapabilitiesEnabled()).isFalse();
  }

  @Test
  public void exchangeCapabilitiesToggleCanBeToggledOn() {
    final ExecutionLayerConfiguration config =
        configBuilder.exchangeCapabilitiesEnabled(true).build();

    assertThat(config.isExchangeCapabilitiesEnabled()).isTrue();
  }
}
