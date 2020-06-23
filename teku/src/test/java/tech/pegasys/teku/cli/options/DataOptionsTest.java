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
import tech.pegasys.teku.util.config.TekuConfiguration;

public class DataOptionsTest extends AbstractBeaconNodeCommandTest {
  private static final String TEST_PATH = "/tmp/teku";

  @Test
  public void dataPath_shouldReadFromConfigurationFile() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromFile("dataOptions_config.yaml");
    assertThat(tekuConfiguration.getDataPath()).isEqualTo(TEST_PATH);
    assertThat(tekuConfiguration.getDataStorageMode()).isEqualTo(ARCHIVE);
    assertThat(tekuConfiguration.getDataStorageCreateDbVersion()).isEqualTo("4");
    assertThat(tekuConfiguration.getDataStorageFrequency()).isEqualTo(128L);
  }

  @Test
  public void dataStorageMode_shouldAcceptPrune() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--data-storage-mode", "prune");
    assertThat(tekuConfiguration.getDataStorageMode()).isEqualTo(PRUNE);
  }

  @Test
  public void dataStorageMode_shouldAcceptArchive() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--data-storage-mode", "archive");
    assertThat(tekuConfiguration.getDataStorageMode()).isEqualTo(ARCHIVE);
  }

  @Test
  public void dataPath_shouldAcceptNonDefaultValues() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--data-path", TEST_PATH);
    assertThat(tekuConfiguration.getDataPath()).isEqualTo(TEST_PATH);
  }

  @Test
  public void dataStorageFrequency_shouldDefault() {
    final TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments();
    assertThat(tekuConfiguration.getDataStorageFrequency()).isEqualTo(2048L);
  }

  @Test
  public void dataStorageFrequency_shouldAcceptNonDefaultValues() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--data-storage-archive-frequency", "1024000");
    assertThat(tekuConfiguration.getDataStorageFrequency()).isEqualTo(1024000L);
  }

  @Test
  public void dataStorageCreateDbVersion_shouldDefault() {
    final TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments();
    assertThat(tekuConfiguration.getDataStorageCreateDbVersion()).isEqualTo("3.0");
  }

  @Test
  public void dataStorageCreateDbVersion_shouldAcceptNonDefaultValues() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xdata-storage-create-db-version", "3.0");
    assertThat(tekuConfiguration.getDataStorageCreateDbVersion()).isEqualTo("3.0");
  }
}
