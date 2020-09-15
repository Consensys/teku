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
import static tech.pegasys.teku.util.config.StateStorageMode.ARCHIVE;
import static tech.pegasys.teku.util.config.StateStorageMode.PRUNE;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.util.config.GlobalConfiguration;

public class DataOptionsTest extends AbstractBeaconNodeCommandTest {
  private static final String TEST_PATH = "/tmp/teku";

  @Test
  public void dataPath_shouldReadFromConfigurationFile() {
    final GlobalConfiguration globalConfiguration =
        getTekuConfigurationFromFile("dataOptions_config.yaml");
    assertThat(globalConfiguration.getDataPath()).isEqualTo(TEST_PATH);
    assertThat(globalConfiguration.getDataStorageMode()).isEqualTo(ARCHIVE);
    assertThat(globalConfiguration.getDataStorageCreateDbVersion()).isEqualTo("4");
    assertThat(globalConfiguration.getDataStorageFrequency()).isEqualTo(128L);
  }

  @Test
  public void dataStorageMode_shouldAcceptPrune() {
    final GlobalConfiguration globalConfiguration =
        getTekuConfigurationFromArguments("--data-storage-mode", "prune");
    assertThat(globalConfiguration.getDataStorageMode()).isEqualTo(PRUNE);
  }

  @Test
  public void dataStorageMode_shouldAcceptArchive() {
    final GlobalConfiguration globalConfiguration =
        getTekuConfigurationFromArguments("--data-storage-mode", "archive");
    assertThat(globalConfiguration.getDataStorageMode()).isEqualTo(ARCHIVE);
  }

  @Test
  public void dataPath_shouldAcceptNonDefaultValues() {
    final GlobalConfiguration globalConfiguration =
        getTekuConfigurationFromArguments("--data-path", TEST_PATH);
    assertThat(globalConfiguration.getDataPath()).isEqualTo(TEST_PATH);
  }

  @Test
  public void dataStorageFrequency_shouldDefault() {
    final GlobalConfiguration globalConfiguration = getTekuConfigurationFromArguments();
    assertThat(globalConfiguration.getDataStorageFrequency()).isEqualTo(2048L);
  }

  @Test
  public void dataStorageFrequency_shouldAcceptNonDefaultValues() {
    final GlobalConfiguration globalConfiguration =
        getTekuConfigurationFromArguments("--data-storage-archive-frequency", "1024000");
    assertThat(globalConfiguration.getDataStorageFrequency()).isEqualTo(1024000L);
  }

  @Test
  public void dataStorageCreateDbVersion_shouldDefault() {
    final GlobalConfiguration globalConfiguration = getTekuConfigurationFromArguments();
    assertThat(globalConfiguration.getDataStorageCreateDbVersion()).isEqualTo("5");
  }

  @Test
  public void dataStorageCreateDbVersion_shouldAcceptNonDefaultValues() {
    final GlobalConfiguration globalConfiguration =
        getTekuConfigurationFromArguments("--Xdata-storage-create-db-version", "3.0");
    assertThat(globalConfiguration.getDataStorageCreateDbVersion()).isEqualTo("3.0");
  }
}
