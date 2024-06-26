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

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.services.beaconchain.WeakSubjectivityInitializer;
import tech.pegasys.teku.services.beaconchain.init.SpecModule.CurrentSlotProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityCalculator;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

@Module
public interface WSModule {

  record WeakSubjectivityFinalizedConfig(WeakSubjectivityConfig config) {}

  interface WeakSubjectivityPeriodValidator {
    void validate(RecentChainData recentChainData);
  }

  interface WeakSubjectivityStoreChainValidator {
    void validate(UInt64 currentSlot);
  }

  @Provides
  @Singleton
  static WeakSubjectivityInitializer weakSubjectivityInitializer() {
    return new WeakSubjectivityInitializer();
  }

  @Provides
  @Singleton
  static SafeFuture<WeakSubjectivityFinalizedConfig> weakSubjectivityFinalizedConfigFuture(
      WeakSubjectivityInitializer weakSubjectivityInitializer,
      WeakSubjectivityConfig weakSubjectivityConfig,
      StorageQueryChannel storageQueryChannel,
      StorageUpdateChannel storageUpdateChannel) {
    SafeFuture<WeakSubjectivityConfig> finalizedConfig =
        weakSubjectivityInitializer.finalizeAndStoreConfig(
            weakSubjectivityConfig, storageQueryChannel, storageUpdateChannel);
    return finalizedConfig.thenApply(WeakSubjectivityFinalizedConfig::new);
  }

  @Provides
  @Singleton
  // TODO producer ?
  static WeakSubjectivityFinalizedConfig weakSubjectivityConfig(
      SafeFuture<WeakSubjectivityFinalizedConfig> weakSubjectivityConfigFuture) {
    return weakSubjectivityConfigFuture.join();
  }

  @Provides
  @Singleton
  static WeakSubjectivityValidator weakSubjectivityValidator(
      WeakSubjectivityFinalizedConfig weakSubjectivityConfig) {
    return WeakSubjectivityValidator.moderate(weakSubjectivityConfig.config());
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
      final UInt64 currentSlot = currentSlotProvider.getCurrentSlot(client.getGenesisTime());
      final WeakSubjectivityCalculator wsCalculator =
          WeakSubjectivityCalculator.create(weakSubjectivityConfig);
      weakSubjectivityInitializer.validateAnchorIsWithinWeakSubjectivityPeriod(
          latestFinalizedAnchor, currentSlot, spec, wsCalculator);
    };
  }

  @Provides
  @Singleton
  static WeakSubjectivityStoreChainValidator weakSubjectivityStoreChainValidator(
      WeakSubjectivityValidator weakSubjectivityValidator,
      CombinedChainDataClient combinedChainDataClient,
      RecentChainData recentChainData) {
    return currentSlot -> {
      weakSubjectivityValidator
          .validateChainIsConsistentWithWSCheckpoint(combinedChainDataClient)
          .thenCompose(
              __ ->
                  SafeFuture.of(
                      () -> recentChainData.getStore().retrieveFinalizedCheckpointAndState()))
          .thenAccept(
              finalizedCheckpointState -> {
                final UInt64 slot = currentSlot.max(recentChainData.getCurrentSlot().orElse(ZERO));
                weakSubjectivityValidator.validateLatestFinalizedCheckpoint(
                    finalizedCheckpointState, slot);
              })
          .finish(
              err -> {
                weakSubjectivityValidator.handleValidationFailure(
                    "Encountered an error while trying to validate latest finalized checkpoint",
                    err);
                throw new RuntimeException(err);
              });
    };
  }
}
