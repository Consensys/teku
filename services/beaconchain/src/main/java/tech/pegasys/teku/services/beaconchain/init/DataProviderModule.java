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

package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import java.util.Optional;
import java.util.function.IntSupplier;
import javax.inject.Singleton;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ExecutionClientDataProvider;
import tech.pegasys.teku.api.RewardCalculator;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beaconrestapi.BeaconRestApi;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.beaconrestapi.JsonTypeDefinitionBeaconRestApi;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannelSubscriber;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.EventAsyncRunner;
import tech.pegasys.teku.services.beaconchain.init.LoggingModule.InitLogger;
import tech.pegasys.teku.services.beaconchain.init.ServiceConfigModule.RejectedExecutionCountSupplier;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorCache;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.coordinator.DepositProvider;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;

@Module
public interface DataProviderModule {

  record LivenessTrackingStatus(boolean enabled) {}

  @Provides
  @Singleton
  static Eth1DataProvider eth1DataProvider(
      final Eth1DataCache eth1DataCache, final DepositProvider depositProvider) {
    return new Eth1DataProvider(eth1DataCache, depositProvider);
  }

  @Provides
  @Singleton
  static ExecutionClientDataProvider executionClientDataProvider(
      final DataProvider dataProvider,
      final EventChannelSubscriber<ExecutionClientEventsChannel>
          executionClientEventsChannelSubscriber) {

    final ExecutionClientDataProvider executionClientDataProvider =
        dataProvider.getExecutionClientDataProvider();
    executionClientEventsChannelSubscriber.subscribe(executionClientDataProvider);
    return executionClientDataProvider;
  }

  @Provides
  @Singleton
  static Optional<BeaconRestApi> beaconRestApi(
      final InitLogger initLogger,
      final Spec spec,
      final BeaconRestApiConfig beaconRestApiConfig,
      @EventAsyncRunner final AsyncRunner eventAsyncRunner,
      final TimeProvider timeProvider,
      final Eth1DataProvider eth1DataProvider,
      final DataProvider dataProvider,
      final EventChannels eventChannels,
      final LivenessTrackingStatus livenessTrackingStatus) {
    if (!beaconRestApiConfig.isRestApiEnabled()) {
      initLogger.logger().info("rest-api-enabled is false, not starting rest api.");
      return Optional.empty();
    }

    BeaconRestApi beaconRestApi =
        new JsonTypeDefinitionBeaconRestApi(
            dataProvider,
            eth1DataProvider,
            beaconRestApiConfig,
            eventChannels,
            eventAsyncRunner,
            timeProvider,
            spec);

    if (livenessTrackingStatus.enabled()) {
      final int initialValidatorsCount =
          spec.getGenesisSpec().getConfig().getMinGenesisActiveValidatorCount();
      eventChannels.subscribe(
          ActiveValidatorChannel.class, new ActiveValidatorCache(spec, initialValidatorsCount));
    }
    return Optional.of(beaconRestApi);
  }

  @Provides
  @Singleton
  static DataProvider dataProvider(
      final Spec spec,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient,
      final RewardCalculator rewardCalculator,
      final Eth2P2PNetwork p2pNetwork,
      final SyncService syncService,
      final ValidatorApiChannel validatorApiChannel,
      final ActiveValidatorChannel activeValidatorChannel,
      final AggregatingAttestationPool attestationPool,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final AttestationManager attestationManager,
      final OperationPool<AttesterSlashing> attesterSlashingPool,
      final OperationPool<ProposerSlashing> proposerSlashingPool,
      final OperationPool<SignedVoluntaryExit> voluntaryExitPool,
      final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool,
      final SyncCommitteeContributionPool syncCommitteeContributionPool,
      final ProposersDataManager proposersDataManager,
      final ForkChoiceNotifier forkChoiceNotifier,
      final LivenessTrackingStatus livenessTrackingStatus,
      @RejectedExecutionCountSupplier final IntSupplier rejectedExecutionCountSupplier) {

    // TODO adopt Dagger instead of DataProvider.builder()
    return DataProvider.builder()
        .spec(spec)
        .recentChainData(recentChainData)
        .combinedChainDataClient(combinedChainDataClient)
        .rewardCalculator(rewardCalculator)
        .p2pNetwork(p2pNetwork)
        .syncService(syncService)
        .validatorApiChannel(validatorApiChannel)
        .attestationPool(attestationPool)
        .blockBlobSidecarsTrackersPool(blockBlobSidecarsTrackersPool)
        .attestationManager(attestationManager)
        .isLivenessTrackingEnabled(livenessTrackingStatus.enabled())
        .activeValidatorChannel(activeValidatorChannel)
        .attesterSlashingPool(attesterSlashingPool)
        .proposerSlashingPool(proposerSlashingPool)
        .voluntaryExitPool(voluntaryExitPool)
        .blsToExecutionChangePool(blsToExecutionChangePool)
        .syncCommitteeContributionPool(syncCommitteeContributionPool)
        .proposersDataManager(proposersDataManager)
        .forkChoiceNotifier(forkChoiceNotifier)
        .rejectedExecutionSupplier(rejectedExecutionCountSupplier)
        .build();
  }

  @Provides
  static LivenessTrackingStatus livenessTrackingStatus(
      final BeaconChainConfiguration beaconConfig) {
    return new LivenessTrackingStatus(
        beaconConfig.beaconRestApiConfig().isBeaconLivenessTrackingEnabled()
            || beaconConfig.validatorConfig().isDoppelgangerDetectionEnabled());
  }
}
