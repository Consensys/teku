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

package tech.pegasys.artemis.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.util.config.StateStorageMode.ARCHIVE;
import static tech.pegasys.artemis.util.config.StateStorageMode.PRUNE;

import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class DataOptionsTest extends AbstractBeaconNodeCommandTest {
  private static final String TEST_PATH = "/tmp/teku";

  @Test
  public void dataPath_shouldReadFromConfigurationFile() {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromFile("dataOptions_config.yaml");
    assertThat(artemisConfiguration.getDataPath()).isEqualTo(TEST_PATH);
    assertThat(artemisConfiguration.getDataStorageMode()).isEqualTo(ARCHIVE);
  }

  @Test
  public void dataStorageMode_shouldAcceptPrune() {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromArguments("--data-storage-mode", "prune");
    assertThat(artemisConfiguration.getDataStorageMode()).isEqualTo(PRUNE);
  }

  @Test
  public void dataStorageMode_shouldAcceptArchive() {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromArguments("--data-storage-mode", "archive");
    assertThat(artemisConfiguration.getDataStorageMode()).isEqualTo(ARCHIVE);
  }

  @Test
  public void dataPath_shouldAcceptNonDefaultValues() {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromArguments("--data-path", TEST_PATH);
    assertThat(artemisConfiguration.getDataPath()).isEqualTo(TEST_PATH);
  }
}
