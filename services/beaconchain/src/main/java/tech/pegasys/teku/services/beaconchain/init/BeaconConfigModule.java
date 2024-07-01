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
import tech.pegasys.teku.beacon.sync.SyncConfig;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig;
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
  static Spec spec(final BeaconChainConfiguration config) {
    return config.getSpec();
  }

  @Provides
  static Eth2NetworkConfiguration eth2NetworkConfig(final BeaconChainConfiguration config) {
    return config.eth2NetworkConfig();
  }

  @Provides
  static StoreConfig storeConfig(final BeaconChainConfiguration config) {
    return config.storeConfig();
  }

  @Provides
  static PowchainConfiguration powchainConfig(final BeaconChainConfiguration config) {
    return config.powchainConfig();
  }

  @Provides
  static P2PConfig p2pConfig(final BeaconChainConfiguration config) {
    return config.p2pConfig();
  }

  @Provides
  static ValidatorConfig validatorConfig(final BeaconChainConfiguration config) {
    return config.validatorConfig();
  }

  @Provides
  static SyncConfig syncConfig(final BeaconChainConfiguration config) {
    return config.syncConfig();
  }

  @Provides
  static BeaconRestApiConfig beaconRestApiConfig(final BeaconChainConfiguration config) {
    return config.beaconRestApiConfig();
  }

  @Provides
  static WeakSubjectivityConfig weakSubjectivityConfig(final BeaconChainConfiguration config) {
    return config.weakSubjectivity();
  }

  @Provides
  static MetricsConfig metricsConfig(final BeaconChainConfiguration config) {
    return config.getMetricsConfig();
  }
}
