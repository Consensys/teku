/*
 * Copyright Consensys Software Inc., 2022
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

import java.util.Optional;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.ethereum.executionclient.web3j.ExecutionWeb3jClientProvider;
import tech.pegasys.teku.networking.nat.NatService;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainService;
import tech.pegasys.teku.services.chainstorage.StorageService;
import tech.pegasys.teku.services.executionlayer.ExecutionLayerService;
import tech.pegasys.teku.services.powchain.PowchainService;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
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
            tekuConfig.metricsConfig().isBlobSidecarsStorageCountersEnabled()));
    Optional<ExecutionWeb3jClientProvider> maybeExecutionWeb3jClientProvider = Optional.empty();
    if (tekuConfig.executionLayer().isEnabled()) {
      // Need to make sure the execution engine is listening before starting the beacon chain
      ExecutionLayerService executionLayerService =
          ExecutionLayerService.create(serviceConfig, tekuConfig.executionLayer());
      services.add(executionLayerService);
      maybeExecutionWeb3jClientProvider = executionLayerService.getEngineWeb3jClientProvider();
    }
    final BeaconChainService beaconChainService =
        new BeaconChainService(serviceConfig, tekuConfig.beaconChain());
    services.add(beaconChainService);
    services.add(
        new NatService(
            tekuConfig.natConfiguration(),
            tekuConfig.network().getListenPort(),
            tekuConfig.discovery().isDiscoveryEnabled()));
    powchainService(
            tekuConfig,
            serviceConfig,
            maybeExecutionWeb3jClientProvider,
            beaconChainService.getBeaconChainController().getRecentChainData())
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
      final RecentChainData recentChainData) {
    if (tekuConfig.beaconChain().interopConfig().isInteropEnabled()
        || (!tekuConfig.powchain().isEnabled() && maybeExecutionWeb3jClientProvider.isEmpty())) {
      return Optional.empty();
    }
    final BeaconState finalizedState = recentChainData.getStore().getLatestFinalized().getState();
    // no need of initializing PowchainService if Eth1Data polling is not needed
    if (tekuConfig.beaconChain().getSpec().isFormerDepositMechanismDisabled(finalizedState)) {
      return Optional.empty();
    }
    return Optional.of(
        new PowchainService(
            serviceConfig, tekuConfig.powchain(), maybeExecutionWeb3jClientProvider));
  }
}
