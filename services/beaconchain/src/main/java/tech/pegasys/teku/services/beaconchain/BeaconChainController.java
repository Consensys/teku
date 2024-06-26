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
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.services.beaconchain.init.BeaconChainControllerComponent;
import tech.pegasys.teku.services.beaconchain.init.DaggerBeaconChainControllerComponent;
import tech.pegasys.teku.services.beaconchain.init.ExternalDependenciesModule;
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
public class BeaconChainController extends AbstractBeaconChainController {

  private static final Logger LOG = LogManager.getLogger();

  private final ServiceConfig serviceConfig;
  private final BeaconChainConfiguration beaconConfig;

  private Spec spec;
  private TimeProvider timeProvider;
  private AsyncRunnerFactory asyncRunnerFactory;
  private ForkChoice forkChoice;
  private RecentChainData recentChainData;
  private Eth2P2PNetwork p2pNetwork;
  private Optional<BeaconRestApi> beaconRestAPI;
  private SyncService syncService;
  private SignatureVerificationService signatureVerificationService;
  private CombinedChainDataClient combinedChainDataClient;

  private Supplier<SafeFuture<?>> starter;
  private Supplier<SafeFuture<?>> stopper;

  public BeaconChainController(ServiceConfig serviceConfig, BeaconChainConfiguration beaconConfig) {
    this.serviceConfig = serviceConfig;
    this.beaconConfig = beaconConfig;
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOG.info("Starting BeaconChainController...");
    BeaconChainControllerComponent component =
        DaggerBeaconChainControllerComponent.builder()
            .externalDependenciesModule(new ExternalDependenciesModule(serviceConfig, beaconConfig))
            .build();

    this.spec = component.getSpec();
    this.timeProvider = component.getTimeProvider();
    this.asyncRunnerFactory = component.getAsyncRunnerFactory();
    this.forkChoice = component.getForkChoice();
    this.recentChainData = component.getRecentChainData();
    this.p2pNetwork = component.getP2pNetwork();
    this.beaconRestAPI = component.getBeaconRestAPI();
    this.syncService = component.getSyncService();
    this.signatureVerificationService = component.getSignatureVerificationService();
    this.combinedChainDataClient = component.getCombinedChainDataClient();
    this.starter = () -> component.starter().start();
    this.stopper = () -> component.stopper().stop();

    SafeFuture<?> startFuture = this.starter.get();
    LOG.info("BeaconChainController start complete");

    return startFuture;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return this.stopper.get();
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
