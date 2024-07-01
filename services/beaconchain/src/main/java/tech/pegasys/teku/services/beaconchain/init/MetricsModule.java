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

import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.BEACON;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import dagger.Module;
import dagger.Provides;
import java.util.Optional;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionAndPublishingPerformanceFactory;
import tech.pegasys.teku.infrastructure.events.EventChannelSubscriber;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.services.beaconchain.BeaconChainMetrics;
import tech.pegasys.teku.services.beaconchain.SlotProcessor;
import tech.pegasys.teku.services.beaconchain.SyncCommitteeMetrics;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.block.BlockImportMetrics;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessingPerformance;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.api.ValidatorPerformanceTrackingMode;
import tech.pegasys.teku.validator.coordinator.ActiveValidatorTracker;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;
import tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.NoOpPerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.SyncCommitteePerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.ValidatorPerformanceMetrics;

@Module
public interface MetricsModule {

  @FunctionalInterface
  interface TickProcessingPerformanceRecordFactory {
    Optional<TickProcessingPerformance> create();
  }

  @Qualifier
  @interface FutureItemsMetric {}

  @Qualifier
  @interface SubnetSubscriptionsMetric {}

  @Qualifier
  @interface PerformanceTrackerTimings {}

  @Provides
  @Singleton
  @FutureItemsMetric
  static SettableLabelledGauge futureItemsMetric(final MetricsSystem metricsSystem) {
    return SettableLabelledGauge.create(
        metricsSystem,
        BEACON,
        "future_items_size",
        "Current number of items held for future slots, labelled by type",
        "type");
  }

  @Provides
  @Singleton
  @SubnetSubscriptionsMetric
  static SettableLabelledGauge subnetSubscriptionsMetric(final MetricsSystem metricsSystem) {
    return SettableLabelledGauge.create(
        metricsSystem,
        TekuMetricCategory.NETWORK,
        "subnet_subscriptions",
        "Tracks attestations subnet subscriptions",
        "type");
  }

  @Provides
  @Singleton
  @PerformanceTrackerTimings
  static SettableGauge performanceTrackerTimings(final MetricsSystem metricsSystem) {
    return SettableGauge.create(
        metricsSystem,
        BEACON,
        "performance_tracker_timings",
        "Tracks how much time (in millis) performance tracker takes to perform calculations");
  }

  @Provides
  @Singleton
  static FutureItems<BlobSidecar> futureBlobSidecars(
      @FutureItemsMetric final SettableLabelledGauge futureItemsMetric) {
    return FutureItems.create(BlobSidecar::getSlot, futureItemsMetric, "blob_sidecars");
  }

  @Provides
  @Singleton
  static ValidatorPerformanceMetrics validatorPerformanceMetrics(
      final MetricsSystem metricsSystem) {
    return new ValidatorPerformanceMetrics(metricsSystem);
  }

  @Provides
  @Singleton
  static PerformanceTracker performanceTracker(
      final Spec spec,
      final ValidatorConfig validatorConfig,
      final CombinedChainDataClient combinedChainDataClient,
      @PerformanceTrackerTimings final SettableGauge performanceTrackerTimings,
      final EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber,
      final ValidatorPerformanceMetrics validatorPerformanceMetrics,
      final ActiveValidatorTracker activeValidatorTracker,
      final StatusLogger statusLogger) {
    ValidatorPerformanceTrackingMode mode = validatorConfig.getValidatorPerformanceTrackingMode();
    if (mode.isEnabled()) {
      DefaultPerformanceTracker performanceTracker =
          new DefaultPerformanceTracker(
              combinedChainDataClient,
              statusLogger,
              validatorPerformanceMetrics,
              validatorConfig.getValidatorPerformanceTrackingMode(),
              activeValidatorTracker,
              new SyncCommitteePerformanceTracker(spec, combinedChainDataClient),
              spec,
              performanceTrackerTimings);
      slotEventsChannelSubscriber.subscribe(performanceTracker);
      return performanceTracker;
    } else {
      return new NoOpPerformanceTracker();
    }
  }

  // TODO not used
  @Provides
  @Singleton
  static SyncCommitteeMetrics syncCommitteeMetrics(
      final Spec spec,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber,
      final EventChannelSubscriber<ChainHeadChannel> chainHeadChannelSubscriber) {
    SyncCommitteeMetrics syncCommitteeMetrics =
        new SyncCommitteeMetrics(spec, recentChainData, metricsSystem);
    slotEventsChannelSubscriber.subscribe(syncCommitteeMetrics);
    chainHeadChannelSubscriber.subscribe(syncCommitteeMetrics);
    return syncCommitteeMetrics;
  }

  // TODO not used
  @Provides
  @Singleton
  static BeaconChainMetrics beaconChainMetrics(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final RecentChainData recentChainData,
      final SlotProcessor slotProcessor,
      final Eth2P2PNetwork p2pNetwork,
      final Eth1DataCache eth1DataCache,
      final EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber) {

    final BeaconChainMetrics beaconChainMetrics =
        new BeaconChainMetrics(
            spec,
            recentChainData,
            slotProcessor.getNodeSlot(),
            metricsSystem,
            p2pNetwork,
            eth1DataCache);
    slotEventsChannelSubscriber.subscribe(beaconChainMetrics);
    return beaconChainMetrics;
  }

  @Provides
  @Singleton
  static BlockProductionAndPublishingPerformanceFactory
      blockProductionAndPublishingPerformanceFactory(
          final TimeProvider timeProvider,
          final RecentChainData recentChainData,
          final MetricsConfig metricsConfig) {
    return new BlockProductionAndPublishingPerformanceFactory(
        timeProvider,
        (slot) -> secondsToMillis(recentChainData.computeTimeAtSlot(slot)),
        metricsConfig.isBlockProductionAndPublishingPerformanceEnabled(),
        metricsConfig.getBlockProductionPerformanceWarningLocalThreshold(),
        metricsConfig.getBlockProductionPerformanceWarningBuilderThreshold(),
        metricsConfig.getBlockPublishingPerformanceWarningLocalThreshold(),
        metricsConfig.getBlockPublishingPerformanceWarningBuilderThreshold());
  }

  @Provides
  @Singleton
  static DutyMetrics dutyMetrics(
      final MetricsSystem metricsSystem,
      final Spec spec,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData) {
    return DutyMetrics.create(metricsSystem, timeProvider, recentChainData, spec);
  }

  @Provides
  @Singleton
  static FutureItems<ValidatableAttestation> futureAttestations(
      @FutureItemsMetric final SettableLabelledGauge futureItemsMetric) {
    return FutureItems.create(
        ValidatableAttestation::getEarliestSlotForForkChoiceProcessing,
        UInt64.valueOf(3),
        futureItemsMetric,
        "attestations");
  }

  @Provides
  @Singleton
  static FutureItems<SignedBeaconBlock> futureBlocks(
      @FutureItemsMetric final SettableLabelledGauge futureItemsMetric) {
    return FutureItems.create(SignedBeaconBlock::getSlot, futureItemsMetric, "blocks");
  }

  @Provides
  @Singleton
  static Optional<BlockImportMetrics> blockImportMetrics(
      final MetricsConfig metricsConfig, final MetricsSystem metricsSystem) {
    return metricsConfig.isBlockPerformanceEnabled()
        ? Optional.of(BlockImportMetrics.create(metricsSystem))
        : Optional.empty();
  }

  @Provides
  @Singleton
  static TickProcessingPerformanceRecordFactory tickProcessingPerformanceRecordFactory(
      final TimeProvider timeProvider, final MetricsConfig metricsConfig) {
    return () ->
        metricsConfig.isTickPerformanceEnabled()
            ? Optional.of(
                new TickProcessingPerformance(timeProvider, timeProvider.getTimeInMillis()))
            : Optional.empty();
  }
}
