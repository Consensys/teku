package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration;
import tech.pegasys.teku.services.powchain.PowchainConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

@Module
public interface BeaconConfigModule {

  @Provides
  static Eth2NetworkConfiguration eth2NetworkConfig(BeaconChainConfiguration config){
    return config.eth2NetworkConfig();
  }

  @Provides
  static StoreConfig storeConfig(BeaconChainConfiguration config){
    return config.storeConfig();
  }

  @Provides
  static PowchainConfiguration powchainConfig(BeaconChainConfiguration config){
    return config.powchainConfig();
  }

  @Provides
  static P2PConfig p2pConfig(BeaconChainConfiguration config){
    return config.p2pConfig();
  }

  @Provides
  static ValidatorConfig validatorConfig(BeaconChainConfiguration config){
    return config.validatorConfig();
  }

  @Provides
  static BeaconRestApiConfig beaconRestApiConfig(BeaconChainConfiguration config){
    return config.beaconRestApiConfig();
  }

  @Provides
  static Spec spec(BeaconChainConfiguration config){
    return config.getSpec();
  }

  @Provides
  static WeakSubjectivityConfig weakSubjectivityConfig(BeaconChainConfiguration config) {
    return config.weakSubjectivity();
  }
}
