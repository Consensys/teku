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
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.services.powchain.PowchainConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.sync.SyncConfig;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class BeaconChainConfiguration {
  private final Eth2NetworkConfiguration eth2NetworkConfiguration;
  private final WeakSubjectivityConfig weakSubjectivityConfig;
  private final ValidatorConfig validatorConfig;
  private final InteropConfig interopConfig;
  private final P2PConfig p2pConfig;
  private final SyncConfig syncConfig;
  private final BeaconRestApiConfig beaconRestApiConfig;
  private final StoreConfig storeConfig;
  private final PowchainConfiguration powchainConfiguration;
  private final Spec spec;

  private final BeaconChainControllerFactory beaconChainControllerFactory;

  public BeaconChainConfiguration(
      final Eth2NetworkConfiguration eth2NetworkConfiguration,
      final WeakSubjectivityConfig weakSubjectivityConfig,
      final ValidatorConfig validatorConfig,
      final InteropConfig interopConfig,
      final P2PConfig p2pConfig,
      final SyncConfig syncConfig,
      final BeaconRestApiConfig beaconRestApiConfig,
      final PowchainConfiguration powchainConfiguration,
      final StoreConfig storeConfig,
      final Spec spec,
      final BeaconChainControllerFactory beaconChainControllerFactory) {
    this.eth2NetworkConfiguration = eth2NetworkConfiguration;
    this.weakSubjectivityConfig = weakSubjectivityConfig;
    this.validatorConfig = validatorConfig;
    this.interopConfig = interopConfig;
    this.p2pConfig = p2pConfig;
    this.syncConfig = syncConfig;
    this.beaconRestApiConfig = beaconRestApiConfig;
    this.powchainConfiguration = powchainConfiguration;
    this.storeConfig = storeConfig;
    this.spec = spec;
    this.beaconChainControllerFactory = beaconChainControllerFactory;
  }

  public Spec getSpec() {
    return spec;
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

  public SyncConfig syncConfig() {
    return syncConfig;
  }

  public BeaconRestApiConfig beaconRestApiConfig() {
    return beaconRestApiConfig;
  }

  public PowchainConfiguration powchainConfig() {
    return powchainConfiguration;
  }

  public StoreConfig storeConfig() {
    return storeConfig;
  }

  public BeaconChainControllerFactory getBeaconChainControllerFactory() {
    return beaconChainControllerFactory;
  }
}
