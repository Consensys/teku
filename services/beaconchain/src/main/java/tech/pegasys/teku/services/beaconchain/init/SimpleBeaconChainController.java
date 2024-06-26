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

import java.util.Optional;
import javax.inject.Inject;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beaconrestapi.BeaconRestApi;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.services.beaconchain.BeaconChainControllerFacade;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SimpleBeaconChainController implements BeaconChainControllerFacade {
  @Inject MainModule.ServiceStarter starter;
  @Inject MainModule.ServiceStopper stopper;
  @Inject Spec spec;
  @Inject TimeProvider timeProvider;
  @Inject AsyncRunnerFactory asyncRunnerFactory;
  @Inject SignatureVerificationService signatureVerificationService;
  @Inject RecentChainData recentChainData;
  @Inject CombinedChainDataClient combinedChainDataClient;
  @Inject Eth2P2PNetwork p2pNetwork;
  @Inject Optional<BeaconRestApi> beaconRestApi;
  @Inject SyncService syncService;
  @Inject ForkChoice forkChoice;

  @Inject
  public SimpleBeaconChainController() {}

  @Override
  public SafeFuture<?> start() {
    return starter.start();
  }

  @Override
  public SafeFuture<?> stop() {
    return stopper.stop();
  }

  @Override
  public boolean isRunning() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Spec getSpec() {
    return spec;
  }

  @Override
  public TimeProvider getTimeProvider() {
    return timeProvider;
  }

  @Override
  public AsyncRunnerFactory getAsyncRunnerFactory() {
    return asyncRunnerFactory;
  }

  @Override
  public SignatureVerificationService getSignatureVerificationService() {
    return signatureVerificationService;
  }

  @Override
  public RecentChainData getRecentChainData() {
    return recentChainData;
  }

  @Override
  public CombinedChainDataClient getCombinedChainDataClient() {
    return combinedChainDataClient;
  }

  @Override
  public Eth2P2PNetwork getP2pNetwork() {
    return p2pNetwork;
  }

  @Override
  public Optional<BeaconRestApi> getBeaconRestAPI() {
    return beaconRestApi;
  }

  @Override
  public SyncService getSyncService() {
    return syncService;
  }

  @Override
  public ForkChoice getForkChoice() {
    return forkChoice;
  }
}
