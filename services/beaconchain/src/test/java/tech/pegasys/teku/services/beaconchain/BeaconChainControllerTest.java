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

package tech.pegasys.teku.services.beaconchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.events.WeakSubjectivityState;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.store.MemKeyValueStore;
import tech.pegasys.teku.util.config.GlobalConfiguration;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class BeaconChainControllerTest {

  private final ServiceConfig serviceConfig = mock(ServiceConfig.class);
  private final EventChannels eventChannels = mock(EventChannels.class);
  private final AtomicReference<GlobalConfiguration> globalConfig =
      new AtomicReference<>(GlobalConfiguration.builder().build());
  @TempDir public File dataDir;

  @BeforeEach
  public void setup() {
    when(serviceConfig.getConfig()).thenAnswer(__ -> globalConfig.get());
    when(eventChannels.getPublisher(any())).thenReturn(mock(SlotEventsChannel.class));
    when(serviceConfig.getEventChannels()).thenReturn(eventChannels);
  }

  private BeaconChainConfiguration beaconChainConfiguration() {
    return new BeaconChainConfiguration(WeakSubjectivityConfig.builder().build());
  }

  @Test
  void getP2pPrivateKeyBytes_generatedKeyTest() throws IOException {
    GlobalConfiguration globalConfiguration =
        GlobalConfiguration.builder().setDataPath(dataDir.getCanonicalPath()).build();
    globalConfig.set(globalConfiguration);
    BeaconChainController controller =
        new BeaconChainController(beaconChainConfiguration(), serviceConfig);

    MemKeyValueStore<String, Bytes> store = new MemKeyValueStore<>();

    // check that new key is generated
    Bytes generatedPK = controller.getP2pPrivateKeyBytes(store);
    assertThat(generatedPK).isNotNull();
    assertThat(generatedPK.size()).isGreaterThanOrEqualTo(32);

    // check the same key loaded next time
    Bytes loadedPK = controller.getP2pPrivateKeyBytes(store);
    assertThat(loadedPK).isEqualTo(generatedPK);

    store = new MemKeyValueStore<>();

    // check that another key is generated after old key is deleted
    Bytes generatedAnotherPK = controller.getP2pPrivateKeyBytes(store);
    assertThat(generatedAnotherPK).isNotNull();
    assertThat(generatedAnotherPK.size()).isGreaterThanOrEqualTo(32);
    assertThat(generatedAnotherPK).isNotEqualTo(generatedPK);

    // check that user supplied private key file has precedence over generated file
    Path customPKFile = dataDir.toPath().resolve("customPK.hex");
    Files.writeString(customPKFile, generatedPK.toHexString());

    GlobalConfiguration globalConfiguration1 =
        GlobalConfiguration.builder()
            .setDataPath(dataDir.getCanonicalPath())
            .setP2pPrivateKeyFile(customPKFile.toString())
            .build();
    globalConfig.set(globalConfiguration1);
    BeaconChainController controller1 =
        new BeaconChainController(beaconChainConfiguration(), serviceConfig);
    Bytes customPK = controller1.getP2pPrivateKeyBytes(store);
    assertThat(customPK).isEqualTo(generatedPK);
  }

  @Test
  public void initWeakSubjectivityValidator_nothingStored_noNewArgs() {
    final BeaconChainController controller =
        new BeaconChainController(beaconChainConfiguration(), serviceConfig);

    // Mock storage channels
    final StorageQueryChannel queryChannel = mock(StorageQueryChannel.class);
    final StorageUpdateChannel updateChannel = mock(StorageUpdateChannel.class);
    when(eventChannels.getPublisher(eq(StorageQueryChannel.class), any())).thenReturn(queryChannel);
    when(eventChannels.getPublisher(eq(StorageUpdateChannel.class), any()))
        .thenReturn(updateChannel);

    // Nothing is stored
    when(queryChannel.getWeakSubjectivityState())
        .thenReturn(SafeFuture.completedFuture(WeakSubjectivityState.empty()));

    assertThat(controller.initWeakSubjectivityValidator()).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel, never()).onWeakSubjectivityUpdate(any());

    final WeakSubjectivityValidator expectedValidator = WeakSubjectivityValidator.lenient();
    assertThat(controller.getWeakSubjectivityValidator()).isEqualTo(expectedValidator);
  }

  @Test
  public void initWeakSubjectivityValidator_nothingStored_withNewArgs() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint();
    final WeakSubjectivityConfig cliConfig =
        WeakSubjectivityConfig.builder().weakSubjectivityCheckpoint(cliCheckpoint).build();
    final BeaconChainConfiguration beaconChainConfiguration =
        new BeaconChainConfiguration(cliConfig);
    final BeaconChainController controller =
        new BeaconChainController(beaconChainConfiguration, serviceConfig);

    // Mock storage channels
    final StorageQueryChannel queryChannel = mock(StorageQueryChannel.class);
    final StorageUpdateChannel updateChannel = mock(StorageUpdateChannel.class);
    when(eventChannels.getPublisher(eq(StorageQueryChannel.class), any())).thenReturn(queryChannel);
    when(eventChannels.getPublisher(eq(StorageUpdateChannel.class), any()))
        .thenReturn(updateChannel);
    when(updateChannel.onWeakSubjectivityUpdate(any())).thenReturn(SafeFuture.COMPLETE);

    // Nothing is stored
    when(queryChannel.getWeakSubjectivityState())
        .thenReturn(SafeFuture.completedFuture(WeakSubjectivityState.empty()));

    assertThat(controller.initWeakSubjectivityValidator()).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel)
        .onWeakSubjectivityUpdate(
            WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(cliCheckpoint));

    final WeakSubjectivityValidator expectedValidator =
        WeakSubjectivityValidator.lenient(cliConfig);
    assertThat(controller.getWeakSubjectivityValidator()).isEqualTo(expectedValidator);
  }

  @Test
  public void initWeakSubjectivityValidator_withStoredCheckpoint_noNewArgs() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final BeaconChainController controller =
        new BeaconChainController(beaconChainConfiguration(), serviceConfig);

    // Mock storage channels
    final StorageQueryChannel queryChannel = mock(StorageQueryChannel.class);
    final StorageUpdateChannel updateChannel = mock(StorageUpdateChannel.class);
    when(eventChannels.getPublisher(eq(StorageQueryChannel.class), any())).thenReturn(queryChannel);
    when(eventChannels.getPublisher(eq(StorageUpdateChannel.class), any()))
        .thenReturn(updateChannel);

    // Setup storage
    final Checkpoint storedCheckpoint = dataStructureUtil.randomCheckpoint();
    final WeakSubjectivityState storedState =
        WeakSubjectivityState.create(Optional.of(storedCheckpoint));
    when(queryChannel.getWeakSubjectivityState())
        .thenReturn(SafeFuture.completedFuture(storedState));

    assertThat(controller.initWeakSubjectivityValidator()).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel, never()).onWeakSubjectivityUpdate(any());

    final WeakSubjectivityValidator expectedValidator =
        WeakSubjectivityValidator.lenient(WeakSubjectivityConfig.from(storedState));
    assertThat(controller.getWeakSubjectivityValidator()).isEqualTo(expectedValidator);
  }

  @Test
  public void initWeakSubjectivityValidator_withStoredCheckpoint_withNewDistinctArgs() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint();
    final WeakSubjectivityConfig cliConfig =
        WeakSubjectivityConfig.builder().weakSubjectivityCheckpoint(cliCheckpoint).build();
    final BeaconChainConfiguration beaconChainConfiguration =
        new BeaconChainConfiguration(cliConfig);
    final BeaconChainController controller =
        new BeaconChainController(beaconChainConfiguration, serviceConfig);

    // Mock storage channels
    final StorageQueryChannel queryChannel = mock(StorageQueryChannel.class);
    final StorageUpdateChannel updateChannel = mock(StorageUpdateChannel.class);
    when(eventChannels.getPublisher(eq(StorageQueryChannel.class), any())).thenReturn(queryChannel);
    when(eventChannels.getPublisher(eq(StorageUpdateChannel.class), any()))
        .thenReturn(updateChannel);
    when(updateChannel.onWeakSubjectivityUpdate(any())).thenReturn(SafeFuture.COMPLETE);

    // Setup storage
    final Checkpoint storedCheckpoint = dataStructureUtil.randomCheckpoint();
    final WeakSubjectivityState storedState =
        WeakSubjectivityState.create(Optional.of(storedCheckpoint));
    when(queryChannel.getWeakSubjectivityState())
        .thenReturn(SafeFuture.completedFuture(storedState));

    assertThat(controller.initWeakSubjectivityValidator()).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel)
        .onWeakSubjectivityUpdate(
            WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(cliCheckpoint));

    final WeakSubjectivityValidator expectedValidator =
        WeakSubjectivityValidator.lenient(cliConfig);
    assertThat(controller.getWeakSubjectivityValidator()).isEqualTo(expectedValidator);
  }

  @Test
  public void initWeakSubjectivityValidator_withStoredCheckpoint_withConsistentCLIArgs() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint();
    final WeakSubjectivityConfig cliConfig =
        WeakSubjectivityConfig.builder().weakSubjectivityCheckpoint(cliCheckpoint).build();
    final BeaconChainConfiguration beaconChainConfiguration =
        new BeaconChainConfiguration(cliConfig);
    final BeaconChainController controller =
        new BeaconChainController(beaconChainConfiguration, serviceConfig);

    // Mock storage channels
    final StorageQueryChannel queryChannel = mock(StorageQueryChannel.class);
    final StorageUpdateChannel updateChannel = mock(StorageUpdateChannel.class);
    when(eventChannels.getPublisher(eq(StorageQueryChannel.class), any())).thenReturn(queryChannel);
    when(eventChannels.getPublisher(eq(StorageUpdateChannel.class), any()))
        .thenReturn(updateChannel);

    // Setup storage
    final WeakSubjectivityState storedState =
        WeakSubjectivityState.create(Optional.of(cliCheckpoint));
    when(queryChannel.getWeakSubjectivityState())
        .thenReturn(SafeFuture.completedFuture(storedState));

    assertThat(controller.initWeakSubjectivityValidator()).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel, never()).onWeakSubjectivityUpdate(any());

    final WeakSubjectivityValidator expectedValidator =
        WeakSubjectivityValidator.lenient(cliConfig);
    assertThat(controller.getWeakSubjectivityValidator()).isEqualTo(expectedValidator);
  }
}
