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
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration;

@Module
public class ExternalDependenciesModule {

  private final ServiceConfig serviceConfig;
  private final BeaconChainConfiguration beaconConfig;

  public ExternalDependenciesModule(
      final ServiceConfig serviceConfig, final BeaconChainConfiguration beaconConfig) {
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
