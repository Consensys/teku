package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import tech.pegasys.teku.api.RewardCalculator;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil;

import javax.inject.Singleton;
import java.util.function.Function;

@Module
public interface SpecModule {

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
  static SchemaSupplier<BeaconBlockBodySchema<?>> provideBeaconBlockBodySchemaSupplier(Spec spec) {
    return slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
  }

  @Provides
  @Singleton
  static RewardCalculator provideRewardCalculator(Spec spec) {
    return new RewardCalculator(spec, new BlockRewardCalculatorUtil(spec));
  }
}
