/*
 * Copyright Consensys Software Inc., 2022
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


import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beaconrestapi.BeaconRestApi;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * The central class which assembles together and initializes Beacon Chain components
 *
 * <p>CAUTION: This class can be overridden by custom implementation to tweak creation and
 * initialization behavior (see {@link BeaconChainControllerFactory}} however this class may change
 * in a backward incompatible manner and either break compilation or runtime behavior
 */
public class BeaconChainController extends Service implements BeaconChainControllerFacade {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final TimeProvider timeProvider;
  private final AsyncRunnerFactory asyncRunnerFactory;
  private final ForkChoice forkChoice;
  private final RecentChainData recentChainData;
  private final Eth2P2PNetwork p2pNetwork;
  private final Optional<BeaconRestApi> beaconRestAPI;
  private final SyncService syncService;
  private final SignatureVerificationService signatureVerificationService;
  private final CombinedChainDataClient combinedChainDataClient;

  private final Supplier<SafeFuture<?>> starter;
  private final Supplier<SafeFuture<?>> stopper;

  public BeaconChainController(
      Spec spec,
      TimeProvider timeProvider,
      AsyncRunnerFactory asyncRunnerFactory,
      ForkChoice forkChoice,
      RecentChainData recentChainData,
      Eth2P2PNetwork p2pNetwork,
      Optional<BeaconRestApi> beaconRestAPI,
      SyncService syncService,
      SignatureVerificationService signatureVerificationService,
      CombinedChainDataClient combinedChainDataClient,
      Supplier<SafeFuture<?>> starter,
      Supplier<SafeFuture<?>> stopper) {
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.asyncRunnerFactory = asyncRunnerFactory;
    this.forkChoice = forkChoice;
    this.recentChainData = recentChainData;
    this.p2pNetwork = p2pNetwork;
    this.beaconRestAPI = beaconRestAPI;
    this.syncService = syncService;
    this.signatureVerificationService = signatureVerificationService;
    this.combinedChainDataClient = combinedChainDataClient;
    this.starter = starter;
    this.stopper = stopper;
  }

  @Override
  protected SafeFuture<?> doStart() {
    return starter.get();
  }

  @Override
  protected SafeFuture<?> doStop() {
    return stopper.get();
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
    return beaconRestAPI;
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
