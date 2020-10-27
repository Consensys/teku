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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.events.WeakSubjectivityState;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.store.MemKeyValueStore;
import tech.pegasys.teku.util.config.GlobalConfiguration;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class BeaconChainControllerTest {

  private final ServiceConfig serviceConfig = mock(ServiceConfig.class);
  private final EventChannels eventChannels = mock(EventChannels.class);
  private P2PConfig p2pConfig = P2PConfig.builder().build();

  @TempDir public Path dataDir;

  @BeforeEach
  public void setup() {
    when(serviceConfig.getConfig()).thenReturn(GlobalConfiguration.builder().build());
    when(eventChannels.getPublisher(any())).thenReturn(mock(SlotEventsChannel.class));
    when(serviceConfig.getEventChannels()).thenReturn(eventChannels);
    when(serviceConfig.getDataDirLayout())
        .thenReturn(
            DataDirLayout.createFrom(
                DataConfig.builder().dataBasePath(dataDir).beaconDataPath(dataDir).build()));
  }

  private BeaconChainConfiguration beaconChainConfiguration() {
    return new BeaconChainConfiguration(
        WeakSubjectivityConfig.builder().build(), ValidatorConfig.builder().build(), p2pConfig);
  }

  @Test
  void getP2pPrivateKeyBytes_generatedKeyTest() throws IOException {
    BeaconChainController controller =
        new BeaconChainController(serviceConfig, beaconChainConfiguration());

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
    Path customPKFile = dataDir.resolve("customPK.hex");
    Files.writeString(customPKFile, generatedPK.toHexString());

    p2pConfig = P2PConfig.builder().p2pPrivateKeyFile(customPKFile.toString()).build();
    BeaconChainController controller1 =
        new BeaconChainController(serviceConfig, beaconChainConfiguration());
    Bytes customPK = controller1.getP2pPrivateKeyBytes(store);
    assertThat(customPK).isEqualTo(generatedPK);
  }

  @Test
  public void initWeakSubjectivity_nothingStored_noNewArgs() {
    final BeaconChainController controller =
        new BeaconChainController(serviceConfig, beaconChainConfiguration());

    // Mock storage channels
    final StorageQueryChannel queryChannel = mock(StorageQueryChannel.class);
    final StorageUpdateChannel updateChannel = mock(StorageUpdateChannel.class);
    when(eventChannels.getPublisher(eq(StorageQueryChannel.class), any())).thenReturn(queryChannel);
    when(eventChannels.getPublisher(eq(StorageUpdateChannel.class), any()))
        .thenReturn(updateChannel);

    // Nothing is stored
    when(queryChannel.getWeakSubjectivityState())
        .thenReturn(SafeFuture.completedFuture(WeakSubjectivityState.empty()));

    assertThat(controller.initWeakSubjectivity()).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel, never()).onWeakSubjectivityUpdate(any());

    final WeakSubjectivityValidator expectedValidator =
        WeakSubjectivityValidator.moderate(WeakSubjectivityConfig.defaultConfig());
    assertThat(controller.getWeakSubjectivityValidator())
        .usingRecursiveComparison()
        .isEqualTo(expectedValidator);
  }

  @Test
  public void initWeakSubjectivity_nothingStored_withNewArgs() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint();
    final WeakSubjectivityConfig cliConfig =
        WeakSubjectivityConfig.builder().weakSubjectivityCheckpoint(cliCheckpoint).build();
    final BeaconChainConfiguration beaconChainConfiguration =
        new BeaconChainConfiguration(cliConfig, ValidatorConfig.builder().build(), p2pConfig);
    final BeaconChainController controller =
        new BeaconChainController(serviceConfig, beaconChainConfiguration);

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

    assertThat(controller.initWeakSubjectivity()).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel)
        .onWeakSubjectivityUpdate(
            WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(cliCheckpoint));

    final WeakSubjectivityValidator expectedValidator =
        WeakSubjectivityValidator.moderate(cliConfig);
    assertThat(controller.getWeakSubjectivityValidator())
        .usingRecursiveComparison()
        .isEqualTo(expectedValidator);
  }

  @Test
  public void initWeakSubjectivity_withStoredCheckpoint_noNewArgs() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final BeaconChainController controller =
        new BeaconChainController(serviceConfig, beaconChainConfiguration());

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

    assertThat(controller.initWeakSubjectivity()).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel, never()).onWeakSubjectivityUpdate(any());

    final WeakSubjectivityValidator expectedValidator =
        WeakSubjectivityValidator.moderate(WeakSubjectivityConfig.from(storedState));
    assertThat(controller.getWeakSubjectivityValidator())
        .usingRecursiveComparison()
        .isEqualTo(expectedValidator);
  }

  @Test
  public void initWeakSubjectivity_withStoredCheckpoint_withNewDistinctArgs() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint();
    final WeakSubjectivityConfig cliConfig =
        WeakSubjectivityConfig.builder()
            .weakSubjectivityCheckpoint(cliCheckpoint)
            .suppressWSPeriodChecksUntilEpoch(UInt64.valueOf(123))
            .safetyDecay(UInt64.valueOf(5))
            .build();
    final BeaconChainConfiguration beaconChainConfiguration =
        new BeaconChainConfiguration(cliConfig, ValidatorConfig.builder().build(), p2pConfig);
    final BeaconChainController controller =
        new BeaconChainController(serviceConfig, beaconChainConfiguration);

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

    assertThat(controller.initWeakSubjectivity()).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel)
        .onWeakSubjectivityUpdate(
            WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(cliCheckpoint));

    final WeakSubjectivityValidator expectedValidator =
        WeakSubjectivityValidator.moderate(cliConfig);
    assertThat(controller.getWeakSubjectivityValidator())
        .usingRecursiveComparison()
        .isEqualTo(expectedValidator);
  }

  @Test
  public void initWeakSubjectivity_withStoredCheckpoint_withConsistentCLIArgs() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint();
    final WeakSubjectivityConfig cliConfig =
        WeakSubjectivityConfig.builder().weakSubjectivityCheckpoint(cliCheckpoint).build();
    final BeaconChainConfiguration beaconChainConfiguration =
        new BeaconChainConfiguration(cliConfig, ValidatorConfig.builder().build(), p2pConfig);
    final BeaconChainController controller =
        new BeaconChainController(serviceConfig, beaconChainConfiguration);

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

    assertThat(controller.initWeakSubjectivity()).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    verify(updateChannel, never()).onWeakSubjectivityUpdate(any());

    final WeakSubjectivityValidator expectedValidator =
        WeakSubjectivityValidator.moderate(cliConfig);
    assertThat(controller.getWeakSubjectivityValidator())
        .usingRecursiveComparison()
        .isEqualTo(expectedValidator);
  }

  @Test
  public void initWeakSubjectivity_anchorAtSameEpochAsCLIWSCheckpoint() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();

    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint(10);
    final SignedBlockAndState anchorBlockAndState =
        dataStructureUtil.randomSignedBlockAndState(cliCheckpoint.getEpochStartSlot());
    final AnchorPoint anchor = AnchorPoint.fromInitialBlockAndState(anchorBlockAndState);

    final Optional<Checkpoint> configuredCheckpoint =
        testInitWeakSubjectivityWithAnchor(
            Optional.empty(), Optional.of(cliCheckpoint), Optional.of(anchor));
    // Sanity check
    assertThat(configuredCheckpoint).isEmpty();
  }

  @Test
  public void initWeakSubjectivity_anchorAfterCLIWSCheckpoint() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();

    final UInt64 wsCheckpointEpoch = UInt64.valueOf(10);
    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint(10);
    final SignedBlockAndState anchorBlockAndState =
        dataStructureUtil.randomSignedBlockAndState(
            compute_start_slot_at_epoch(wsCheckpointEpoch.plus(2)));
    final AnchorPoint anchor = AnchorPoint.fromInitialBlockAndState(anchorBlockAndState);

    final Optional<Checkpoint> configuredCheckpoint =
        testInitWeakSubjectivityWithAnchor(
            Optional.empty(), Optional.of(cliCheckpoint), Optional.of(anchor));
    // Sanity check
    assertThat(configuredCheckpoint).isEmpty();
  }

  @Test
  public void initWeakSubjectivity_anchorPriorToCLIWSCheckpoint() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();

    final UInt64 wsCheckpointEpoch = UInt64.valueOf(10);
    final Checkpoint cliCheckpoint = dataStructureUtil.randomCheckpoint(10);
    final SignedBlockAndState anchorBlockAndState =
        dataStructureUtil.randomSignedBlockAndState(
            compute_start_slot_at_epoch(wsCheckpointEpoch.minus(2)));
    final AnchorPoint anchor = AnchorPoint.fromInitialBlockAndState(anchorBlockAndState);

    final Optional<Checkpoint> configuredCheckpoint =
        testInitWeakSubjectivityWithAnchor(
            Optional.empty(), Optional.of(cliCheckpoint), Optional.of(anchor));
    // Sanity check
    assertThat(configuredCheckpoint).isPresent();
  }

  @Test
  public void initWeakSubjectivity_anchorAtSameEpochAsStoredWSCheckpoint() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();

    final Checkpoint storedCheckpoint = dataStructureUtil.randomCheckpoint(10);
    final SignedBlockAndState anchorBlockAndState =
        dataStructureUtil.randomSignedBlockAndState(storedCheckpoint.getEpochStartSlot());
    final AnchorPoint anchor = AnchorPoint.fromInitialBlockAndState(anchorBlockAndState);

    final Optional<Checkpoint> configuredCheckpoint =
        testInitWeakSubjectivityWithAnchor(
            Optional.of(storedCheckpoint), Optional.empty(), Optional.of(anchor));
    // Sanity check
    assertThat(configuredCheckpoint).isEmpty();
  }

  @Test
  public void initWeakSubjectivity_anchorAfterStoredWSCheckpoint() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();

    final UInt64 wsCheckpointEpoch = UInt64.valueOf(10);
    final Checkpoint storedCheckpoint = dataStructureUtil.randomCheckpoint(10);
    final SignedBlockAndState anchorBlockAndState =
        dataStructureUtil.randomSignedBlockAndState(
            compute_start_slot_at_epoch(wsCheckpointEpoch.plus(2)));
    final AnchorPoint anchor = AnchorPoint.fromInitialBlockAndState(anchorBlockAndState);

    final Optional<Checkpoint> configuredCheckpoint =
        testInitWeakSubjectivityWithAnchor(
            Optional.of(storedCheckpoint), Optional.empty(), Optional.of(anchor));
    // Sanity check
    assertThat(configuredCheckpoint).isEmpty();
  }

  @Test
  public void initWeakSubjectivity_anchorPriorToStoredWSCheckpoint() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();

    final UInt64 wsCheckpointEpoch = UInt64.valueOf(10);
    final Checkpoint storedCheckpoint = dataStructureUtil.randomCheckpoint(10);
    final SignedBlockAndState anchorBlockAndState =
        dataStructureUtil.randomSignedBlockAndState(
            compute_start_slot_at_epoch(wsCheckpointEpoch.minus(2)));
    final AnchorPoint anchor = AnchorPoint.fromInitialBlockAndState(anchorBlockAndState);

    final Optional<Checkpoint> configuredCheckpoint =
        testInitWeakSubjectivityWithAnchor(
            Optional.of(storedCheckpoint), Optional.empty(), Optional.of(anchor));
    // Sanity check
    assertThat(configuredCheckpoint).isPresent();
  }

  private Optional<Checkpoint> testInitWeakSubjectivityWithAnchor(
      Optional<Checkpoint> storedCheckpoint,
      Optional<Checkpoint> cliCheckpoint,
      Optional<AnchorPoint> wsInitialAnchor) {
    // Set up expectations
    final Optional<Checkpoint> expectedWsCheckpoint =
        cliCheckpoint
            .or(() -> storedCheckpoint)
            .filter(
                c ->
                    wsInitialAnchor.isEmpty()
                        || wsInitialAnchor.get().getEpoch().isLessThan(c.getEpoch()));
    final boolean shouldPersistWsCheckpoint =
        cliCheckpoint.isPresent() && expectedWsCheckpoint.isPresent();
    final boolean shouldClearWsCheckpoint =
        storedCheckpoint.isPresent() && expectedWsCheckpoint.isEmpty();
    final WeakSubjectivityConfig expectedConfig =
        WeakSubjectivityConfig.builder().weakSubjectivityCheckpoint(expectedWsCheckpoint).build();

    final WeakSubjectivityConfig cliConfig =
        WeakSubjectivityConfig.builder().weakSubjectivityCheckpoint(cliCheckpoint).build();
    final BeaconChainConfiguration beaconChainConfiguration =
        new BeaconChainConfiguration(cliConfig, ValidatorConfig.builder().build());
    final BeaconChainController controller =
        new BeaconChainController(serviceConfig, beaconChainConfiguration);

    // Mock storage channels
    final StorageQueryChannel queryChannel = mock(StorageQueryChannel.class);
    final StorageUpdateChannel updateChannel = mock(StorageUpdateChannel.class);
    when(eventChannels.getPublisher(eq(StorageQueryChannel.class), any())).thenReturn(queryChannel);
    when(eventChannels.getPublisher(eq(StorageUpdateChannel.class), any()))
        .thenReturn(updateChannel);
    when(updateChannel.onWeakSubjectivityUpdate(any())).thenReturn(SafeFuture.COMPLETE);

    // Setup storage
    final WeakSubjectivityState storedState = WeakSubjectivityState.create(storedCheckpoint);
    when(queryChannel.getWeakSubjectivityState())
        .thenReturn(SafeFuture.completedFuture(storedState));

    assertThat(controller.initWeakSubjectivity(wsInitialAnchor)).isCompleted();
    verify(queryChannel).getWeakSubjectivityState();
    if (shouldPersistWsCheckpoint) {
      verify(updateChannel)
          .onWeakSubjectivityUpdate(
              WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(
                  expectedWsCheckpoint.orElseThrow()));
    } else if (shouldClearWsCheckpoint) {
      verify(updateChannel)
          .onWeakSubjectivityUpdate(WeakSubjectivityUpdate.clearWeakSubjectivityCheckpoint());
    } else {
      verify(updateChannel, never()).onWeakSubjectivityUpdate(any());
    }

    final WeakSubjectivityValidator expectedValidator =
        WeakSubjectivityValidator.moderate(expectedConfig);
    assertThat(controller.getWeakSubjectivityValidator())
        .usingRecursiveComparison()
        .isEqualTo(expectedValidator);

    return expectedWsCheckpoint;
  }
}
