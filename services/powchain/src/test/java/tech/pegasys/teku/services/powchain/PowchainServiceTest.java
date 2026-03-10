/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.services.powchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.pow.DepositSnapshotFileLoader;
import tech.pegasys.teku.beacon.pow.DepositSnapshotFileLoader.DepositSnapshotResource;
import tech.pegasys.teku.beacon.pow.Eth1DepositManager;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.executionclient.web3j.ExecutionWeb3jClientProvider;
import tech.pegasys.teku.ethereum.executionclient.web3j.Web3JClient;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;

public class PowchainServiceTest {
  private final ServiceConfig serviceConfig = mock(ServiceConfig.class);
  private final PowchainConfiguration powConfig = mock(PowchainConfiguration.class);
  private final DepositTreeSnapshotConfiguration depositTreeSnapshotConfiguration =
      mock(DepositTreeSnapshotConfiguration.class);
  private final ExecutionWeb3jClientProvider engineWeb3jClientProvider =
      mock(ExecutionWeb3jClientProvider.class);
  private final Web3JClient web3JClient = mock(Web3JClient.class);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private Supplier<Optional<BeaconState>> latestFinalizedState;
  private final MetricsSystem metricsSystem = new StubMetricsSystem();

  @BeforeEach
  public void setup() {
    when(serviceConfig.getMetricsSystem()).thenReturn(metricsSystem);
    when(serviceConfig.createAsyncRunner("powchain")).thenReturn(mock(AsyncRunner.class));
    when(serviceConfig.getTimeProvider()).thenReturn(mock(TimeProvider.class));
    when(powConfig.isEnabled()).thenReturn(false);
    when(powConfig.getSpec()).thenReturn(spec);
    when(powConfig.getDepositContract()).thenReturn(Eth1Address.ZERO);
    when(powConfig.getDepositTreeSnapshotConfiguration())
        .thenReturn(depositTreeSnapshotConfiguration);
    when(engineWeb3jClientProvider.getWeb3JClient()).thenReturn(web3JClient);

    final EventChannels eventChannels = mock(EventChannels.class);
    when(eventChannels.getPublisher(Eth1EventsChannel.class))
        .thenReturn(mock(Eth1EventsChannel.class));
    when(eventChannels.getPublisher(eq(Eth1DepositStorageChannel.class), any(AsyncRunner.class)))
        .thenReturn(mock(Eth1DepositStorageChannel.class));
    when(serviceConfig.getEventChannels()).thenReturn(eventChannels);
    latestFinalizedState = Optional::empty;
  }

  @Test
  public void shouldFail_WhenNeitherEth1NorExecutionEndpoint() {
    assertThatThrownBy(() -> createAndInitializePowchainService(Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldFail_WhenNoEth1EndpointButWebsocketsExecutionEndpoint() {
    when(web3JClient.isWebsocketsClient()).thenReturn(true);
    assertThatThrownBy(
            () -> createAndInitializePowchainService(Optional.of(engineWeb3jClientProvider)))
        .hasMessageContaining("not compatible with Websockets");
  }

  @Test
  public void shouldFallbackToNonWebsocketsEndpoint_WhenEth1EndpointNotProvided() {
    when(web3JClient.isWebsocketsClient()).thenReturn(false);
    when(serviceConfig.getTimeProvider()).thenReturn(mock(TimeProvider.class));
    when(powConfig.getDepositContract()).thenReturn(Eth1Address.ZERO);
    final EventChannels eventChannels = mock(EventChannels.class);
    when(eventChannels.getPublisher(Eth1EventsChannel.class))
        .thenReturn(mock(Eth1EventsChannel.class));
    when(eventChannels.getPublisher(eq(Eth1DepositStorageChannel.class), any(AsyncRunner.class)))
        .thenReturn(mock(Eth1DepositStorageChannel.class));
    when(serviceConfig.getEventChannels()).thenReturn(eventChannels);
    assertThatNoException()
        .isThrownBy(
            () -> createAndInitializePowchainService(Optional.of(engineWeb3jClientProvider)));
  }

  @Test
  public void shouldUseCustomDepositSnapshotPathOnlyWhenPresent() {
    when(depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath())
        .thenReturn(Optional.of("/foo/custom"));
    when(depositTreeSnapshotConfiguration.getBundledDepositSnapshotPath())
        .thenReturn(Optional.of("/foo/bundled"));

    final PowchainService powchainService =
        createAndInitializePowchainService(Optional.of(engineWeb3jClientProvider));

    verifyExpectedOrderOfDepositSnapshotPathResources(
        powchainService, List.of(new DepositSnapshotResource("/foo/custom", true)));
  }

  @Test
  public void shouldUseBundledDepositSnapshotPathWhenAvailableAndCustomPathNotPresent() {
    when(depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath())
        .thenReturn(Optional.empty());
    when(depositTreeSnapshotConfiguration.getBundledDepositSnapshotPath())
        .thenReturn(Optional.of("/foo/bundled"));
    when(depositTreeSnapshotConfiguration.isBundledDepositSnapshotEnabled()).thenReturn(true);

    final PowchainService powchainService =
        createAndInitializePowchainService(Optional.of(engineWeb3jClientProvider));

    verifyExpectedOrderOfDepositSnapshotPathResources(
        powchainService, List.of(new DepositSnapshotResource("/foo/bundled", true)));
  }

  @Test
  public void shouldNotUseBundledDepositSnapshotPathWhenDepositSnapshotDisabled() {
    when(depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath())
        .thenReturn(Optional.empty());
    when(depositTreeSnapshotConfiguration.getBundledDepositSnapshotPath())
        .thenReturn(Optional.of("/foo/bundled"));
    when(depositTreeSnapshotConfiguration.isBundledDepositSnapshotEnabled()).thenReturn(false);

    final PowchainService powchainService =
        createAndInitializePowchainService(Optional.of(engineWeb3jClientProvider));

    verifyExpectedOrderOfDepositSnapshotPathResources(powchainService, List.of());
  }

  @Test
  public void shouldUseCheckpointSyncAndBundledDepositSnapshotPath() {
    when(depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath())
        .thenReturn(Optional.empty());
    when(depositTreeSnapshotConfiguration.getCheckpointSyncDepositSnapshotUrl())
        .thenReturn(Optional.of("/foo/checkpoint"));
    when(depositTreeSnapshotConfiguration.getBundledDepositSnapshotPath())
        .thenReturn(Optional.of("/foo/bundled"));
    when(depositTreeSnapshotConfiguration.isBundledDepositSnapshotEnabled()).thenReturn(true);

    final PowchainService powchainService =
        createAndInitializePowchainService(Optional.of(engineWeb3jClientProvider));

    verifyExpectedOrderOfDepositSnapshotPathResources(
        powchainService,
        List.of(
            new DepositSnapshotResource("/foo/checkpoint", false),
            new DepositSnapshotResource("/foo/bundled", true)));
  }

  @Test
  public void shouldNotUseCheckpointSyncAndBundledDepositSnapshotPathWhenSnapshotDisabled() {
    when(depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath())
        .thenReturn(Optional.empty());
    when(depositTreeSnapshotConfiguration.getCheckpointSyncDepositSnapshotUrl())
        .thenReturn(Optional.of("/foo/checkpoint"));
    when(depositTreeSnapshotConfiguration.getBundledDepositSnapshotPath())
        .thenReturn(Optional.of("/foo/bundled"));
    when(depositTreeSnapshotConfiguration.isBundledDepositSnapshotEnabled()).thenReturn(false);

    final PowchainService powchainService =
        createAndInitializePowchainService(Optional.of(engineWeb3jClientProvider));

    verifyExpectedOrderOfDepositSnapshotPathResources(powchainService, List.of());
  }

  @Test
  public void shouldUseCustomDepositSnapshotPathEvenWhenCheckpointSyncIsPresent() {
    when(depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath())
        .thenReturn(Optional.of("/foo/custom"));
    when(depositTreeSnapshotConfiguration.getCheckpointSyncDepositSnapshotUrl())
        .thenReturn(Optional.of("/foo/checkpoint"));

    final PowchainService powchainService =
        createAndInitializePowchainService(Optional.of(engineWeb3jClientProvider));

    verifyExpectedOrderOfDepositSnapshotPathResources(
        powchainService, List.of(new DepositSnapshotResource("/foo/custom", true)));
  }

  @Test
  public void shouldHaveNoDepositSnapshotPathWhenNoneIsAvailable() {
    when(depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath())
        .thenReturn(Optional.empty());
    when(depositTreeSnapshotConfiguration.getBundledDepositSnapshotPath())
        .thenReturn(Optional.empty());

    final PowchainService powchainService =
        createAndInitializePowchainService(Optional.of(engineWeb3jClientProvider));

    assertThat(
            powchainService
                .getEth1DepositManager()
                .getDepositSnapshotFileLoader()
                .getDepositSnapshotResources())
        .isEmpty();
  }

  @Test
  public void shouldNotInitializeIfEth1PollingHasBeenDisabled() {
    final Spec spec = mock(Spec.class);
    when(powConfig.getSpec()).thenReturn(spec);
    final BeaconState finalizedState = mock(BeaconState.class);
    when(spec.isFormerDepositMechanismDisabled(finalizedState)).thenReturn(true);

    latestFinalizedState = () -> Optional.of(finalizedState);

    final PowchainService powchainService =
        new PowchainService(
            serviceConfig, powConfig, Optional.of(engineWeb3jClientProvider), latestFinalizedState);

    powchainService.start().join();

    assertThat(powchainService.isRunning()).isTrue();
    assertThat(powchainService.getEth1DepositManager()).isNull();
  }

  @Test
  public void shouldStopServiceIfEth1PollingHasBeenDisabledOnFinalizedCheckpoint() {
    final Spec spec = mock(Spec.class);
    when(powConfig.getSpec()).thenReturn(spec);
    final BeaconState finalizedState = mock(BeaconState.class);
    when(spec.isFormerDepositMechanismDisabled(finalizedState)).thenReturn(true);

    latestFinalizedState = () -> Optional.of(finalizedState);

    final PowchainService powchainService =
        new PowchainService(
            serviceConfig, powConfig, Optional.of(engineWeb3jClientProvider), latestFinalizedState);

    powchainService.start().join();

    assertThat(powchainService.isRunning()).isTrue();

    powchainService.onNewFinalizedCheckpoint(mock(Checkpoint.class), false);

    assertThat(powchainService.isRunning()).isFalse();
  }

  private PowchainService createAndInitializePowchainService(
      final Optional<ExecutionWeb3jClientProvider> maybeExecutionWeb3jClientProvider) {
    final PowchainService powchainService =
        new PowchainService(
            serviceConfig, powConfig, maybeExecutionWeb3jClientProvider, latestFinalizedState);
    powchainService.initialize();
    return powchainService;
  }

  private void verifyExpectedOrderOfDepositSnapshotPathResources(
      final PowchainService powchainService,
      final List<DepositSnapshotResource> expectedResources) {
    final Eth1DepositManager eth1DepositManager = powchainService.getEth1DepositManager();
    final DepositSnapshotFileLoader depositSnapshotFileLoader =
        eth1DepositManager.getDepositSnapshotFileLoader();
    assertThat(depositSnapshotFileLoader.getDepositSnapshotResources())
        .isEqualTo(expectedResources);
  }
}
