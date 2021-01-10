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

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.services.chainstorage.StorageConfiguration;
import tech.pegasys.teku.storage.server.DatabaseVersion;

public class DataOptionsTest extends AbstractBeaconNodeCommandTest {
  private static final Path TEST_PATH = Path.of("/tmp/teku");

  @Test
  public void dataPath_shouldReadFromConfigurationFile() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromFile("dataOptions_config.yaml");
    final StorageConfiguration config = tekuConfiguration.storageConfiguration();
    assertThat(tekuConfiguration.dataConfig().getDataBasePath()).isEqualTo(TEST_PATH);
    assertThat(config.getDataStorageMode()).isEqualTo(ARCHIVE);
    assertThat(config.getDataStorageCreateDbVersion()).isEqualTo(DatabaseVersion.V4);
    assertThat(config.getDataStorageFrequency()).isEqualTo(128L);
  }

  @Test
  public void dataStorageMode_shouldAcceptPrune() {
    final StorageConfiguration config =
        getTekuConfigurationFromArguments("--data-storage-mode", "prune").storageConfiguration();
    assertThat(config.getDataStorageMode()).isEqualTo(PRUNE);
  }

  @Test
  public void dataStorageMode_shouldAcceptArchive() {
    final StorageConfiguration config =
        getTekuConfigurationFromArguments("--data-storage-mode", "archive").storageConfiguration();
    assertThat(config.getDataStorageMode()).isEqualTo(ARCHIVE);
  }

  @Test
  public void dataPath_shouldAcceptNonDefaultValues() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--data-path", TEST_PATH.toString());
    assertThat(config.dataConfig().getDataBasePath()).isEqualTo(TEST_PATH);
  }

  @Test
  public void dataStorageFrequency_shouldDefault() {
    final StorageConfiguration config = getTekuConfigurationFromArguments().storageConfiguration();
    assertThat(config.getDataStorageFrequency()).isEqualTo(2048L);
  }

  @Test
  public void dataStorageFrequency_shouldAcceptNonDefaultValues() {
    final StorageConfiguration config =
        getTekuConfigurationFromArguments("--data-storage-archive-frequency", "1024000")
            .storageConfiguration();
    assertThat(config.getDataStorageFrequency()).isEqualTo(1024000L);
  }

  @Test
  public void dataStorageCreateDbVersion_shouldDefault() {
    final StorageConfiguration config = getTekuConfigurationFromArguments().storageConfiguration();
    assertThat(config.getDataStorageCreateDbVersion()).isEqualTo(DatabaseVersion.V5);
  }

  @Test
  public void dataStorageCreateDbVersion_shouldAcceptNonDefaultValues() {
    final StorageConfiguration config =
        getTekuConfigurationFromArguments("--Xdata-storage-create-db-version", "noop")
            .storageConfiguration();
    assertThat(config.getDataStorageCreateDbVersion()).isEqualTo(DatabaseVersion.NOOP);
  }
}
