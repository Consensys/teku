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

package tech.pegasys.teku.services.executionengine;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class ExecutionEngineConfigurationTest {
  private final ExecutionEngineConfiguration.Builder configBuilder =
      ExecutionEngineConfiguration.builder();
  private final Spec bellatrixSpec = TestSpecFactory.createMinimalBellatrix();

  @Test
  public void shouldThrowExceptionIfNoEeEndpointSpecified() {
    final ExecutionEngineConfiguration config = configBuilder.specProvider(bellatrixSpec).build();

    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(config::getEndpoint)
        .withMessageContaining(
            "Invalid configuration. --ee-endpoint parameter is mandatory when Bellatrix milestone is enabled");
  }

  @Test
  public void noExceptionThrownIfEeEndpointSpecified() {
    final ExecutionEngineConfiguration config =
        configBuilder.specProvider(bellatrixSpec).endpoint("someEndpoint").build();

    Assertions.assertThatCode(config::getEndpoint).doesNotThrowAnyException();
  }
}
