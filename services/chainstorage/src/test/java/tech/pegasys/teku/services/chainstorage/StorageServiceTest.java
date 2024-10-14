/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.services.chainstorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.StorageConfiguration;
import tech.pegasys.teku.storage.server.pruner.StatePruner;

class StorageServiceTest {

  private final ServiceConfig serviceConfig = mock(ServiceConfig.class);
  private final StorageConfiguration storageConfiguration = mock(StorageConfiguration.class);
  private final MetricsSystem metricsSystem = mock(MetricsSystem.class);
  private final DataDirLayout dataDirLayout = mock(DataDirLayout.class);
  private final File file = mock(File.class);
  private final Eth1Address eth1DepositContract = mock(Eth1Address.class);
  private final Spec spec = mock(Spec.class);
  private final EventChannels eventChannels = mock(EventChannels.class);
  private StorageService storageService;

  @BeforeEach
  void setUp(@TempDir final Path tempDir) {
    when(serviceConfig.getMetricsSystem()).thenReturn(metricsSystem);
    when(dataDirLayout.getBeaconDataDirectory()).thenReturn(tempDir);
    when(serviceConfig.getDataDirLayout()).thenReturn(dataDirLayout);
    when(storageConfiguration.getDataStorageCreateDbVersion()).thenReturn(DatabaseVersion.NOOP);
    when(storageConfiguration.getMaxKnownNodeCacheSize())
        .thenReturn(StorageConfiguration.DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE);
    when(storageConfiguration.getDataStorageFrequency())
        .thenReturn(StorageConfiguration.DEFAULT_STORAGE_FREQUENCY);
    when(storageConfiguration.getEth1DepositContract()).thenReturn(eth1DepositContract);
    when(storageConfiguration.isStoreNonCanonicalBlocksEnabled()).thenReturn(false);
    when(storageConfiguration.getSpec()).thenReturn(spec);
    when(file.toPath()).thenReturn(tempDir);

    when(eventChannels.subscribe(any(), any())).thenReturn(eventChannels);
    when(serviceConfig.getEventChannels()).thenReturn(eventChannels);

    final StubAsyncRunnerFactory asyncRunnerFactory = new StubAsyncRunnerFactory();
    when(serviceConfig.getAsyncRunnerFactory()).thenReturn(asyncRunnerFactory);

    final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner();
    when(serviceConfig.createAsyncRunner(any(), anyInt(), anyInt(), anyInt()))
        .thenReturn(stubAsyncRunner);

    storageService = new StorageService(serviceConfig, storageConfiguration, false, false);
  }

  @Test
  void shouldNotSetupStatePrunerWhenArchiveMode() {
    when(storageConfiguration.getDataStorageMode()).thenReturn(StateStorageMode.ARCHIVE);
    final SafeFuture<?> future = storageService.doStart();
    final Optional<StatePruner> statePruner = storageService.getStatePruner();
    assertThat(future).isCompleted();
    assertThat(statePruner).isEmpty();
  }

  @Test
  void shouldSetupStatePrunerWhenArchiveModeAndRetentionSlotsEnabled() {
    when(storageConfiguration.getDataStorageMode()).thenReturn(StateStorageMode.ARCHIVE);
    when(storageConfiguration.getRetainedSlots()).thenReturn(5L);
    final SafeFuture<?> future = storageService.doStart();
    final Optional<StatePruner> statePruner = storageService.getStatePruner();
    assertThat(future).isCompleted();
    assertThat(statePruner).isPresent();
    assertThat(storageService.getStatePruner().get().isRunning()).isTrue();
  }

  @Test
  void shouldSetupStatePrunerWhenPruneMode() {
    when(storageConfiguration.getDataStorageMode()).thenReturn(StateStorageMode.PRUNE);
    final SafeFuture<?> future = storageService.doStart();
    final Optional<StatePruner> statePruner = storageService.getStatePruner();
    assertThat(future).isCompleted();
    assertThat(statePruner).isPresent();
    assertThat(storageService.getStatePruner().get().isRunning()).isTrue();
  }

  @Test
  void shouldSetupStatePrunerWhenMinimalMode() {
    when(storageConfiguration.getDataStorageMode()).thenReturn(StateStorageMode.MINIMAL);
    final SafeFuture<?> future = storageService.doStart();
    final Optional<StatePruner> statePruner = storageService.getStatePruner();
    assertThat(future).isCompleted();
    assertThat(statePruner).isPresent();
    assertThat(storageService.getStatePruner().get().isRunning()).isTrue();
  }
}
