package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration;

@Module
public class ExternalDependenciesModule {

  private final ServiceConfig serviceConfig;
  private final BeaconChainConfiguration beaconConfig;

  public ExternalDependenciesModule(ServiceConfig serviceConfig, BeaconChainConfiguration beaconConfig) {
    this.serviceConfig = serviceConfig;
    this.beaconConfig = beaconConfig;
  }

  @Provides
  ServiceConfig provideServiceConfig() {
    return serviceConfig;
  }

  @Provides
  BeaconChainConfiguration provideBeaconChainConfiguration() {
    return beaconConfig;
  }
}
