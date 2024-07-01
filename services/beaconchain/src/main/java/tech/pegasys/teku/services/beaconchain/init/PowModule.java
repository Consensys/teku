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
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannelSubscriber;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.BeaconAsyncRunner;
import tech.pegasys.teku.services.powchain.PowchainConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.TerminalPowBlockMonitor;
import tech.pegasys.teku.statetransition.genesis.GenesisHandler;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.coordinator.DepositProvider;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;
import tech.pegasys.teku.validator.coordinator.Eth1VotingPeriod;

@Module
public interface PowModule {

  @Qualifier
  @interface ProposerDefaultFeeRecipient {}

  @Provides
  @Singleton
  static Optional<TerminalPowBlockMonitor> terminalPowBlockMonitor(
      final Spec spec,
      @BeaconAsyncRunner final AsyncRunner beaconAsyncRunner,
      final TimeProvider timeProvider,
      final ExecutionLayerChannel executionLayer,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier,
      final EventLogger eventLogger) {
    if (spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      return Optional.of(
          new TerminalPowBlockMonitor(
              executionLayer,
              spec,
              recentChainData,
              forkChoiceNotifier,
              beaconAsyncRunner,
              eventLogger,
              timeProvider));
    } else {
      return Optional.empty();
    }
  }

  @Provides
  @Singleton
  static Eth1DataCache eth1DataCache(final Spec spec, final MetricsSystem metricsSystem) {
    return new Eth1DataCache(spec, metricsSystem, new Eth1VotingPeriod(spec));
  }

  @Provides
  @Singleton
  static DepositProvider depositProvider(
      final Spec spec,
      final PowchainConfiguration powchainConfig,
      final MetricsSystem metricsSystem,
      final RecentChainData recentChainData,
      final Eth1DataCache eth1DataCache,
      final StorageUpdateChannel storageUpdateChannel,
      final Eth1DepositStorageChannel eth1DepositStorageChannel,
      final EventChannelSubscriber<Eth1EventsChannel> eth1EventsChannelSubscriber,
      final EventChannelSubscriber<FinalizedCheckpointChannel> finalizedCheckpointChannelSubscriber,
      final EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber,
      final EventLogger eventLogger) {
    DepositProvider depositProvider =
        new DepositProvider(
            metricsSystem,
            recentChainData,
            eth1DataCache,
            storageUpdateChannel,
            eth1DepositStorageChannel,
            spec,
            eventLogger,
            powchainConfig.useMissingDepositEventLogging());
    eth1EventsChannelSubscriber.subscribe(depositProvider);
    finalizedCheckpointChannelSubscriber.subscribe(depositProvider);
    slotEventsChannelSubscriber.subscribe(depositProvider);

    return depositProvider;
  }

  @Provides
  @Singleton
  @ProposerDefaultFeeRecipient
  static Optional<Eth1Address> proposerDefaultFeeRecipient(
      final Spec spec,
      final ValidatorConfig validatorConfig,
      final BeaconRestApiConfig restApiConfig,
      final StatusLogger statusLogger) {
    if (!spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      return Optional.of(Eth1Address.ZERO);
    }

    final Optional<Eth1Address> defaultFeeRecipient =
        validatorConfig.getProposerDefaultFeeRecipient();

    if (defaultFeeRecipient.isEmpty() && restApiConfig.isRestApiEnabled()) {
      statusLogger.warnMissingProposerDefaultFeeRecipientWithRestAPIEnabled();
    }

    return defaultFeeRecipient;
  }

  @Provides
  @Singleton
  static GenesisHandler genesisHandler(
      final TimeProvider timeProvider,
      final Spec spec,
      final RecentChainData recentChainData,
      final EventChannelSubscriber<Eth1EventsChannel> eth1EventsChannelSubscriber) {
    GenesisHandler genesisHandler = new GenesisHandler(recentChainData, timeProvider, spec);
    eth1EventsChannelSubscriber.subscribe(genesisHandler);
    return genesisHandler;
  }
}
