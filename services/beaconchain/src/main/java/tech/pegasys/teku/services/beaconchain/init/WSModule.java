package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.services.beaconchain.WeakSubjectivityInitializer;
import tech.pegasys.teku.services.beaconchain.init.SpecModule.CurrentSlotProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityCalculator;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

import javax.inject.Singleton;

@Module
public interface WSModule {

  interface WeakSubjectivityPeriodValidator {
    void validate(RecentChainData recentChainData);
  }

  @Provides
  @Singleton
  static WeakSubjectivityInitializer weakSubjectivityInitializer() {
    return new WeakSubjectivityInitializer();
  }

  @Provides
  @Singleton
  static SafeFuture<WeakSubjectivityConfig> weakSubjectivityConfigFuture(
      WeakSubjectivityInitializer weakSubjectivityInitializer,
      WeakSubjectivityConfig weakSubjectivityConfig,
      StorageQueryChannel storageQueryChannel,
      StorageUpdateChannel storageUpdateChannel
  ) {
    return weakSubjectivityInitializer.finalizeAndStoreConfig(
        weakSubjectivityConfig, storageQueryChannel, storageUpdateChannel);
  }

  @Provides
  @Singleton
  // TODO producer ?
  static WeakSubjectivityConfig weakSubjectivityConfig(
      SafeFuture<WeakSubjectivityConfig> weakSubjectivityConfigFuture
  ) {
    return weakSubjectivityConfigFuture.join();
  }

  @Provides
  @Singleton
  static WeakSubjectivityValidator weakSubjectivityValidator(
      WeakSubjectivityConfig weakSubjectivityConfig
  ) {
    return WeakSubjectivityValidator.moderate(weakSubjectivityConfig);
  }

  @Provides
  @Singleton
  static WeakSubjectivityPeriodValidator weakSubjectivityPeriodValidator(
      Spec spec,
      CurrentSlotProvider currentSlotProvider,
      WeakSubjectivityConfig weakSubjectivityConfig,
      WeakSubjectivityInitializer weakSubjectivityInitializer) {
    return client -> {
      final AnchorPoint latestFinalizedAnchor = client.getStore().getLatestFinalized();
      final UInt64 currentSlot =
          currentSlotProvider.getCurrentSlot(client.getGenesisTime());
      final WeakSubjectivityCalculator wsCalculator =
          WeakSubjectivityCalculator.create(weakSubjectivityConfig);
      weakSubjectivityInitializer.validateAnchorIsWithinWeakSubjectivityPeriod(
          latestFinalizedAnchor, currentSlot, spec, wsCalculator);
    };
  }
}
