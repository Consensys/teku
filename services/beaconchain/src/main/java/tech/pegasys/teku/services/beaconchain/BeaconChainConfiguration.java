/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.services.beaconchain;

import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.services.powchain.PowchainConfiguration;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class BeaconChainConfiguration {
  private final Eth2NetworkConfiguration eth2NetworkConfiguration;
  private final WeakSubjectivityConfig weakSubjectivityConfig;
  private final ValidatorConfig validatorConfig;
  private final InteropConfig interopConfig;
  private final P2PConfig p2pConfig;
  private final BeaconRestApiConfig beaconRestApiConfig;
  private final LoggingConfig loggingConfig;
  private final StoreConfig storeConfig;
  private final PowchainConfiguration powchainConfiguration;

  public BeaconChainConfiguration(
      final Eth2NetworkConfiguration eth2NetworkConfiguration,
      final WeakSubjectivityConfig weakSubjectivityConfig,
      final ValidatorConfig validatorConfig,
      final InteropConfig interopConfig,
      final P2PConfig p2pConfig,
      final BeaconRestApiConfig beaconRestApiConfig,
      final PowchainConfiguration powchainConfiguration,
      final LoggingConfig loggingConfig,
      final StoreConfig storeConfig) {
    this.eth2NetworkConfiguration = eth2NetworkConfiguration;
    this.weakSubjectivityConfig = weakSubjectivityConfig;
    this.validatorConfig = validatorConfig;
    this.interopConfig = interopConfig;
    this.p2pConfig = p2pConfig;
    this.beaconRestApiConfig = beaconRestApiConfig;
    this.powchainConfiguration = powchainConfiguration;
    this.loggingConfig = loggingConfig;
    this.storeConfig = storeConfig;
  }

  public Eth2NetworkConfiguration eth2NetworkConfig() {
    return eth2NetworkConfiguration;
  }

  public WeakSubjectivityConfig weakSubjectivity() {
    return weakSubjectivityConfig;
  }

  public ValidatorConfig validatorConfig() {
    return validatorConfig;
  }

  public InteropConfig interopConfig() {
    return interopConfig;
  }

  public P2PConfig p2pConfig() {
    return p2pConfig;
  }

  public BeaconRestApiConfig beaconRestApiConfig() {
    return beaconRestApiConfig;
  }

  public PowchainConfiguration powchainConfig() {
    return powchainConfiguration;
  }

  public LoggingConfig loggingConfig() {
    return loggingConfig;
  }

  public StoreConfig storeConfig() {
    return storeConfig;
  }
}
