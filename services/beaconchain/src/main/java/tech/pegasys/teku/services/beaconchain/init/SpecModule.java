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
import java.util.function.Function;
import javax.inject.Singleton;
import tech.pegasys.teku.api.RewardCalculator;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil;

@Module
public interface SpecModule {

  interface CurrentSlotProvider {

    UInt64 getCurrentSlot(UInt64 genesisTime);

    UInt64 getCurrentSlot(UInt64 currentTime, UInt64 genesisTime);
  }

  @FunctionalInterface
  interface SchemaSupplier<T extends SszSchema<?>> extends Function<UInt64, T> {

    @Override
    default T apply(UInt64 slot) {
      return getSchemaAtSlot(slot);
    }

    T getSchemaAtSlot(UInt64 slot);
  }

  @Provides
  @Singleton
  static SchemaSupplier<BeaconBlockBodySchema<?>> beaconBlockBodySchemaSupplier(Spec spec) {
    return slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
  }

  @Provides
  @Singleton
  static RewardCalculator rewardCalculator(Spec spec) {
    return new RewardCalculator(spec, new BlockRewardCalculatorUtil(spec));
  }

  @Provides
  @Singleton
  static CurrentSlotProvider currentSlotProvider(Spec spec, TimeProvider timeProvider) {
    return new CurrentSlotProvider() {
      @Override
      public UInt64 getCurrentSlot(UInt64 genesisTime) {
        return getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime);
      }

      @Override
      public UInt64 getCurrentSlot(UInt64 currentTime, UInt64 genesisTime) {
        return spec.getCurrentSlot(currentTime, genesisTime);
      }
    };
  }
}
