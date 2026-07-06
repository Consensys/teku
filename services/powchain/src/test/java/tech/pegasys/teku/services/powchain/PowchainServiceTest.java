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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;

class PowchainServiceTest {
  private static final Spec SPEC = TestSpecFactory.createDefault();

  private final ServiceConfig serviceConfig = mock(ServiceConfig.class);
  private final EventChannels eventChannels = mock(EventChannels.class);
  private final Eth1EventsChannel eth1EventsChannel = mock(Eth1EventsChannel.class);
  private final CombinedStorageChannel storageQueryChannel = mock(CombinedStorageChannel.class);

  @Test
  void doStart_seedsDepositProviderWithBundledSnapshot() {
    setUpChannels();
    // No snapshot in the database, so it falls back to the bundled file snapshot.
    when(storageQueryChannel.getFinalizedDepositSnapshot())
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final PowchainService service =
        new PowchainService(serviceConfig, configForNetwork(Eth2Network.MAINNET, true));

    assertThat(service.doStart()).isCompleted();
    verify(eth1EventsChannel).onInitialDepositTreeSnapshot(any());
  }

  @Test
  void doStart_doesNotSeedWhenNoSnapshotAvailable() {
    setUpChannels();
    when(storageQueryChannel.getFinalizedDepositSnapshot())
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    // Snapshots disabled and no custom path: nothing to load.
    final PowchainService service =
        new PowchainService(serviceConfig, configForNetwork(Eth2Network.MAINNET, false));

    assertThat(service.doStart()).isCompleted();
    verify(eth1EventsChannel, never()).onInitialDepositTreeSnapshot(any());
  }

  private void setUpChannels() {
    when(serviceConfig.getEventChannels()).thenReturn(eventChannels);
    when(serviceConfig.createAsyncRunner(anyString())).thenReturn(new StubAsyncRunner());
    when(eventChannels.getPublisher(eq(Eth1EventsChannel.class))).thenReturn(eth1EventsChannel);
    when(eventChannels.getPublisher(eq(CombinedStorageChannel.class), any()))
        .thenReturn(storageQueryChannel);
  }

  private PowchainConfiguration configForNetwork(
      final Eth2Network network, final boolean depositSnapshotEnabled) {
    return PowchainConfiguration.builder()
        .specProvider(SPEC)
        .depositSnapshotEnabled(depositSnapshotEnabled)
        .setDepositSnapshotPathForNetwork(Optional.of(network))
        .build();
  }
}
