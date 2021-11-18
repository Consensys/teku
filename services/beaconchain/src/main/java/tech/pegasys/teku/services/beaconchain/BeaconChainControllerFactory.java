package tech.pegasys.teku.services.beaconchain;

import tech.pegasys.teku.service.serviceutils.ServiceConfig;

public interface BeaconChainControllerFactory {

  BeaconChainControllerFactory DEFAULT = BeaconChainController::new;

  BeaconChainController create(
      final ServiceConfig serviceConfig, final BeaconChainConfiguration beaconConfig);
}
