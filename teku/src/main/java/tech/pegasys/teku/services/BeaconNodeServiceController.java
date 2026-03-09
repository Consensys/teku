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

package tech.pegasys.teku.services;

import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.ethereum.executionclient.web3j.ExecutionWeb3jClientProvider;
import tech.pegasys.teku.networking.nat.NatService;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainService;
import tech.pegasys.teku.services.chainstorage.StorageService;
import tech.pegasys.teku.services.executionlayer.ExecutionLayerService;
import tech.pegasys.teku.services.powchain.PowchainService;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.client.ValidatorClientService;
import tech.pegasys.teku.validator.client.slashingriskactions.DoppelgangerDetectionShutDown;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashedValidatorShutDown;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashingRiskAction;

public class BeaconNodeServiceController extends ServiceController {

  public BeaconNodeServiceController(
      final TekuConfiguration tekuConfig, final ServiceConfig serviceConfig) {
    // Note services will be started in the order they are added here.
    services.add(
        new StorageService(
            serviceConfig,
            tekuConfig.storageConfiguration(),
            tekuConfig
                .powchain()
                .getDepositTreeSnapshotConfiguration()
                .isBundledDepositSnapshotEnabled(),
            tekuConfig.metricsConfig().isBlobSidecarsStorageCountersEnabled(),
            tekuConfig.metricsConfig().isDataColumnSidecarsStorageCountersEnabled(),
            tekuConfig.beaconChain().eth2NetworkConfig().getEth2Network()));
    Optional<ExecutionWeb3jClientProvider> maybeExecutionWeb3jClientProvider = Optional.empty();
    if (tekuConfig.executionLayer().isEnabled()) {
      // Need to make sure the execution engine is listening before starting the beacon chain
      final ExecutionLayerService executionLayerService =
          ExecutionLayerService.create(
              serviceConfig,
              tekuConfig.executionLayer(),
              tekuConfig.eth2NetworkConfiguration().getSpec());
      services.add(executionLayerService);
      maybeExecutionWeb3jClientProvider = executionLayerService.getEngineWeb3jClientProvider();
    }
    final BeaconChainService beaconChainService =
        new BeaconChainService(serviceConfig, tekuConfig.beaconChain());
    services.add(beaconChainService);
    final NetworkConfig networkConfig = tekuConfig.network();
    services.add(
        new NatService(
            tekuConfig.natConfiguration(),
            networkConfig.getListenPort(),
            networkConfig.getNetworkInterfaces().size() == 2
                // IPv4 and IPv6 (dual-stack)
                ? Optional.of(networkConfig.getListenPortIpv6())
                : Optional.empty(),
            tekuConfig.discovery().isDiscoveryEnabled()));
    // making it a Supplier ensures that BeaconChainService has been started and RecentChainData has
    // been initialized when `get()` is called
    final Supplier<Optional<BeaconState>> finalizedStateSupplier =
        () -> {
          final RecentChainData recentChainData =
              beaconChainService.getBeaconChainController().getRecentChainData();
          if (recentChainData.isPreGenesis()) {
            return Optional.empty();
          }
          return Optional.of(recentChainData.getStore().getLatestFinalized().getState());
        };
    powchainService(
            tekuConfig, serviceConfig, maybeExecutionWeb3jClientProvider, finalizedStateSupplier)
        .ifPresent(services::add);

    final Optional<SlashingRiskAction> maybeValidatorSlashedAction =
        tekuConfig.validatorClient().getValidatorConfig().isShutdownWhenValidatorSlashedEnabled()
            ? Optional.of(new SlashedValidatorShutDown())
            : Optional.empty();

    services.add(
        ValidatorClientService.create(
            serviceConfig,
            tekuConfig.validatorClient(),
            new DoppelgangerDetectionShutDown(),
            maybeValidatorSlashedAction));
  }

  private Optional<PowchainService> powchainService(
      final TekuConfiguration tekuConfig,
      final ServiceConfig serviceConfig,
      final Optional<ExecutionWeb3jClientProvider> maybeExecutionWeb3jClientProvider,
      final Supplier<Optional<BeaconState>> finalizedStateSupplier) {
    if (tekuConfig.beaconChain().interopConfig().isInteropEnabled()
        || (!tekuConfig.powchain().isEnabled() && maybeExecutionWeb3jClientProvider.isEmpty())) {
      return Optional.empty();
    }
    if (!tekuConfig.powchain().isDepositContractLogsSyncingEnabled()) {
      // PowchainService is only used for deposit contract logs syncing, so no need to initialize it
      // if disabled
      EVENT_LOG.depositContractLogsSyncingDisabled();
      return Optional.empty();
    }
    final PowchainService powchainService =
        new PowchainService(
            serviceConfig,
            tekuConfig.powchain(),
            maybeExecutionWeb3jClientProvider,
            finalizedStateSupplier);
    serviceConfig.getEventChannels().subscribe(FinalizedCheckpointChannel.class, powchainService);
    return Optional.of(powchainService);
  }
}
