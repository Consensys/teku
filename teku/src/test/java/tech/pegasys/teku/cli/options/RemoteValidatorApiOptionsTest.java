/*
 * Copyright 2020 ConsenSys AG.
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
import tech.pegasys.teku.util.config.GlobalConfiguration;

public class RemoteValidatorApiOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadFromConfigurationFile() {
    final GlobalConfiguration config =
        getGlobalConfigurationFromFile("remoteValidatorApiOptions_config.yaml");

    assertThat(config.getRemoteValidatorApiInterface()).isEqualTo("127.0.0.123");
    assertThat(config.getRemoteValidatorApiPort()).isEqualTo(1234);
    assertThat(config.getRemoteValidatorApiMaxSubscribers()).isEqualTo(9_876);
    assertThat(config.isRemoteValidatorApiEnabled()).isTrue();
  }

  @Test
  public void shouldHaveExpectedDefaultValues() {
    final GlobalConfiguration config = getGlobalConfigurationFromArguments();

    assertThat(config.getRemoteValidatorApiInterface()).isEqualTo("127.0.0.1");
    assertThat(config.getRemoteValidatorApiPort()).isEqualTo(9999);
    assertThat(config.getRemoteValidatorApiMaxSubscribers()).isEqualTo(1_000);
    assertThat(config.isRemoteValidatorApiEnabled()).isFalse();
  }

  @Test
  public void remoteValidatorApiEnabled_shouldNotRequireAValue() {
    final GlobalConfiguration globalConfiguration =
        getGlobalConfigurationFromArguments("--Xremote-validator-api-enabled");
    assertThat(globalConfiguration.isRemoteValidatorApiEnabled()).isTrue();
  }

  @Test
  public void remoteValidatorApiInterface_shouldOverrideValue() {
    final GlobalConfiguration globalConfiguration =
        getGlobalConfigurationFromArguments("--Xremote-validator-api-interface", "127.0.9.9");
    assertThat(globalConfiguration.getRemoteValidatorApiInterface()).isEqualTo("127.0.9.9");
  }

  @Test
  public void remoteValidatorApiPort_shouldOverrideValue() {
    final GlobalConfiguration globalConfiguration =
        getGlobalConfigurationFromArguments("--Xremote-validator-api-port", "8888");
    assertThat(globalConfiguration.getRemoteValidatorApiPort()).isEqualTo(8888);
  }

  @Test
  public void remoteValidatorApiMaxConnections_shouldOverrideValue() {
    final GlobalConfiguration globalConfiguration =
        getGlobalConfigurationFromArguments("--Xremote-validator-api-max-subscribers", "9876");
    assertThat(globalConfiguration.getRemoteValidatorApiMaxSubscribers()).isEqualTo(9_876);
  }

  @Test
  public void remoteValidatorApiEnabled_shouldOverrideValue() {
    final GlobalConfiguration globalConfiguration =
        getGlobalConfigurationFromArguments("--Xremote-validator-api-enabled", "true");
    assertThat(globalConfiguration.isRemoteValidatorApiEnabled()).isTrue();
  }
}
