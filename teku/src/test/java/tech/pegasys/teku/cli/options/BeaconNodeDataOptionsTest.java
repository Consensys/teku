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

package tech.pegasys.teku.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.storage.server.StateStorageMode.ARCHIVE;
import static tech.pegasys.teku.storage.server.StateStorageMode.MINIMAL;
import static tech.pegasys.teku.storage.server.StateStorageMode.PRUNE;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StorageConfiguration;

public class BeaconNodeDataOptionsTest extends AbstractBeaconNodeCommandTest {
  private static final Path TEST_PATH = Path.of("/tmp/teku");
  private static final String GENESIS_STATE =
      "https://221EMZ2YSdriVVdXx:5058f100c7@eth2-beacon-mainnet.infura.io/eth/v1/debug/beacon/states/finalized";

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
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--data-storage-mode", "prune");
    final StorageConfiguration config = tekuConfiguration.storageConfiguration();
    assertThat(config.getDataStorageMode()).isEqualTo(PRUNE);
    assertThat(createConfigBuilder().storageConfiguration(b -> b.dataStorageMode(PRUNE)).build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void dataStorageMode_shouldAcceptArchive() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--data-storage-mode", "archive");
    final StorageConfiguration config = tekuConfiguration.storageConfiguration();
    assertThat(config.getDataStorageMode()).isEqualTo(ARCHIVE);
    assertThat(createConfigBuilder().storageConfiguration(b -> b.dataStorageMode(ARCHIVE)).build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void dataPath_shouldAcceptNonDefaultValues() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--data-path", TEST_PATH.toString());
    assertThat(config.dataConfig().getDataBasePath()).isEqualTo(TEST_PATH);
    assertThat(createConfigBuilder().data(b -> b.dataBasePath(TEST_PATH)).build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  public void dataStorageFrequency_shouldDefault() {
    final StorageConfiguration config = getTekuConfigurationFromArguments().storageConfiguration();
    assertThat(config.getDataStorageFrequency()).isEqualTo(2048L);
  }

  @Test
  public void dataStorageFrequency_shouldAcceptNonDefaultValues() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--data-storage-archive-frequency", "1024000");
    final StorageConfiguration config = tekuConfiguration.storageConfiguration();
    assertThat(config.getDataStorageFrequency()).isEqualTo(1024000L);
    assertThat(
            createConfigBuilder()
                .storageConfiguration(b -> b.dataStorageFrequency(1024000L))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void dataStorageCreateDbVersion_shouldDefault() {
    final StorageConfiguration config = getTekuConfigurationFromArguments().storageConfiguration();
    final DatabaseVersion expectedDefault =
        DatabaseVersion.isLevelDbSupported() ? DatabaseVersion.LEVELDB2 : DatabaseVersion.V5;
    assertThat(config.getDataStorageCreateDbVersion()).isEqualTo(expectedDefault);
  }

  @Test
  public void dataStorageCreateDbVersion_shouldOverrideIfFrequencyIsLowAndSupported() {
    final Supplier<TekuConfiguration> tekuConfigurationSupplier =
        () -> getTekuConfigurationFromArguments("--data-storage-archive-frequency", "1");
    if (DatabaseVersion.isLevelDbSupported()) {
      final StorageConfiguration config = tekuConfigurationSupplier.get().storageConfiguration();
      assertThat(config.getDataStorageCreateDbVersion()).isEqualTo(DatabaseVersion.LEVELDB_TREE);
    } else {
      assertThatThrownBy(tekuConfigurationSupplier::get)
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("Native LevelDB support is required");
    }
  }

  @Test
  public void dataStorageCreateDbVersion_shouldAcceptNonDefaultValues() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xdata-storage-create-db-version", "noop");
    final StorageConfiguration config = tekuConfiguration.storageConfiguration();
    assertThat(config.getDataStorageCreateDbVersion()).isEqualTo(DatabaseVersion.NOOP);
    assertThat(
            createConfigBuilder()
                .storageConfiguration(b -> b.dataStorageCreateDbVersion(DatabaseVersion.NOOP))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void shouldAcceptReconstructHistoricStatesValue() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--data-storage-mode",
            "ARCHIVE",
            "--genesis-state",
            GENESIS_STATE,
            "--reconstruct-historic-states",
            "true");
    assertThat(tekuConfiguration.sync().isReconstructHistoricStatesEnabled()).isEqualTo(true);
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(b -> b.customGenesisState(GENESIS_STATE))
                .storageConfiguration(b -> b.dataStorageMode(ARCHIVE))
                .sync(b -> b.reconstructHistoricStatesEnabled(true))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void missingGenesisState_expectInvalidReconstructHistoricStatesValue() {
    assertThatThrownBy(
            () ->
                createConfigBuilder()
                    .eth2NetworkConfig(b -> b.applyNetworkDefaults(Eth2Network.MINIMAL))
                    .storageConfiguration(b -> b.dataStorageMode(ARCHIVE))
                    .sync(b -> b.reconstructHistoricStatesEnabled(true))
                    .build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Genesis state required when reconstructing historic states");
  }

  @Test
  public void usingDefaultGenesisState_expectValidReconstructHistoricStatesValue() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--data-storage-mode",
            "ARCHIVE",
            "--network",
            "mainnet",
            "--reconstruct-historic-states",
            "true");

    assertThat(tekuConfiguration.sync().isReconstructHistoricStatesEnabled()).isEqualTo(true);
    assertThat(
            createConfigBuilder()
                .eth2NetworkConfig(b -> b.applyNetworkDefaults(Eth2Network.MAINNET))
                .storageConfiguration(b -> b.dataStorageMode(ARCHIVE))
                .sync(b -> b.reconstructHistoricStatesEnabled(true))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void pruneDataStorageMode_expectInvalidReconstructHistoricStatesValue() {
    assertThatThrownBy(
            () ->
                createConfigBuilder()
                    .eth2NetworkConfig(b -> b.customGenesisState(GENESIS_STATE))
                    .storageConfiguration(b -> b.dataStorageMode(PRUNE))
                    .sync(b -> b.reconstructHistoricStatesEnabled(true))
                    .build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Cannot reconstruct historic states without using ARCHIVE data storage mode");
  }

  @Test
  void shouldSetBlockPruningOptions() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--data-storage-mode=MINIMAL", "--Xdata-storage-block-pruning-interval=150");
    assertThat(config.storageConfiguration().getDataStorageMode()).isEqualTo(MINIMAL);
    assertThat(config.storageConfiguration().getBlockPruningInterval())
        .isEqualTo(Duration.ofSeconds(150));
    assertThat(
            createConfigBuilder()
                .storageConfiguration(
                    b -> b.dataStorageMode(MINIMAL).blockPruningInterval(Duration.ofSeconds(150)))
                .sync(b -> b.fetchAllHistoricBlocks(false))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void shouldSetBlobsPruningOptions() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xdata-storage-blobs-pruning-interval=55", "--Xdata-storage-blobs-pruning-limit=10");
    assertThat(config.storageConfiguration().getBlobsPruningInterval())
        .isEqualTo(Duration.ofSeconds(55));
    assertThat(config.storageConfiguration().getBlobsPruningLimit()).isEqualTo(10);
    assertThat(
            createConfigBuilder()
                .storageConfiguration(
                    b -> b.blobsPruningInterval(Duration.ofSeconds(55)).blobsPruningLimit(10))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void shouldNotAllowPruningBlocksAndReconstructingStates() {
    assertThatThrownBy(
            () ->
                createConfigBuilder()
                    .storageConfiguration(b -> b.dataStorageMode(MINIMAL))
                    .sync(b -> b.reconstructHistoricStatesEnabled(true))
                    .build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Cannot reconstruct historic states without using ARCHIVE data storage mode");
  }

  @Test
  void shouldNotAllowPruningBlocksAndFetchingAllHistoricBlocks() {
    assertThatThrownBy(
            () ->
                createConfigBuilder()
                    .storageConfiguration(b -> b.dataStorageMode(MINIMAL))
                    .sync(b -> b.fetchAllHistoricBlocks(true))
                    .build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage("Cannot download all historic blocks with block pruning enabled");
  }
}
