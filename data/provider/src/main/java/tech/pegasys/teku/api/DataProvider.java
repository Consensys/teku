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

package tech.pegasys.teku.api;

import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class DataProvider {
  private final SpecProvider specProvider;
  private final NetworkDataProvider networkDataProvider;
  private final ChainDataProvider chainDataProvider;
  private final SyncDataProvider syncDataProvider;
  private final ValidatorDataProvider validatorDataProvider;
  private final NodeDataProvider nodeDataProvider;

  public DataProvider(
      final SpecProvider specProvider,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient,
      final Eth2Network p2pNetwork,
      final SyncService syncService,
      final ValidatorApiChannel validatorApiChannel,
      final AggregatingAttestationPool attestationPool,
      final OperationPool<AttesterSlashing> attesterSlashingPool,
      final OperationPool<ProposerSlashing> proposerSlashingPool,
      final OperationPool<SignedVoluntaryExit> voluntaryExitPool) {
    this.specProvider = specProvider;
    networkDataProvider = new NetworkDataProvider(p2pNetwork);
    nodeDataProvider =
        new NodeDataProvider(
            attestationPool, attesterSlashingPool, proposerSlashingPool, voluntaryExitPool);
    chainDataProvider = new ChainDataProvider(recentChainData, combinedChainDataClient);
    syncDataProvider = new SyncDataProvider(syncService);
    this.validatorDataProvider =
        new ValidatorDataProvider(validatorApiChannel, combinedChainDataClient);
  }

  public SpecProvider getSpecProvider() {
    return specProvider;
  }

  public NetworkDataProvider getNetworkDataProvider() {
    return networkDataProvider;
  }

  public ChainDataProvider getChainDataProvider() {
    return chainDataProvider;
  }

  public SyncDataProvider getSyncDataProvider() {
    return syncDataProvider;
  }

  public ValidatorDataProvider getValidatorDataProvider() {
    return validatorDataProvider;
  }

  public NodeDataProvider getNodeDataProvider() {
    return nodeDataProvider;
  }
}
