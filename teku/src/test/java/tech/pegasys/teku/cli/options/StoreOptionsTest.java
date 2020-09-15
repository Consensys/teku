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

public class StoreOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void hotStatePersistenceFrequency_shouldRespectCLIArg() {
    final String[] args = {
      "--Xhot-state-persistence-frequency", "99",
    };
    final GlobalConfiguration globalConfiguration = getGlobalConfigurationFromArguments(args);
    assertThat(globalConfiguration.getHotStatePersistenceFrequencyInEpochs()).isEqualTo(99);
  }

  @Test
  public void hotStatePersistenceFrequency_shouldSetDefaultValue() {
    final GlobalConfiguration globalConfiguration = getGlobalConfigurationFromArguments();
    assertThat(globalConfiguration.getHotStatePersistenceFrequencyInEpochs()).isEqualTo(1);
  }

  @Test
  public void hotStatePersistenceFrequency_invalidNumber() {
    final String[] args = {
      "--Xhot-state-persistence-frequency", "1.5",
    };
    beaconNodeCommand.parse(args);
    final String output = getCommandLineOutput();

    assertThat(output).isNotEmpty();
    assertThat(output).contains("Invalid value");
  }

  @Test
  public void hotStatePersistenceFrequency_missingValue() {
    final String[] args = {
      "--Xhot-state-persistence-frequency", "",
    };
    beaconNodeCommand.parse(args);
    final String output = getCommandLineOutput();

    assertThat(output).isNotEmpty();
    assertThat(output).contains("Invalid value");
  }

  @Test
  public void disableBlockProcessingAtStartup_shouldRespectCLIArg_true() {
    final String[] args = {
      "--Xdisable-block-processing-at-startup", "true",
    };
    final GlobalConfiguration globalConfiguration = getGlobalConfigurationFromArguments(args);
    assertThat(globalConfiguration.isBlockProcessingAtStartupDisabled()).isTrue();
  }

  @Test
  public void disableBlockProcessingAtStartup_shouldRespectCLIArg_false() {
    final String[] args = {
      "--Xdisable-block-processing-at-startup", "false",
    };
    final GlobalConfiguration globalConfiguration = getGlobalConfigurationFromArguments(args);
    assertThat(globalConfiguration.isBlockProcessingAtStartupDisabled()).isFalse();
  }

  @Test
  public void disableBlockProcessingAtStartup_shouldRespectCLIArg_implicit() {
    final String[] args = {
      "--Xdisable-block-processing-at-startup",
    };
    final GlobalConfiguration globalConfiguration = getGlobalConfigurationFromArguments(args);
    assertThat(globalConfiguration.isBlockProcessingAtStartupDisabled()).isTrue();
  }

  @Test
  public void disableBlockProcessingAtStartup_default() {
    final GlobalConfiguration globalConfiguration = getGlobalConfigurationFromArguments();
    assertThat(globalConfiguration.isBlockProcessingAtStartupDisabled()).isTrue();
  }
}
