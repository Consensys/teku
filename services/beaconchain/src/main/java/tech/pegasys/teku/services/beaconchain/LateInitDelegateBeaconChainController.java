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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beaconrestapi.BeaconRestApi;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class LateInitDelegateBeaconChainController extends Service
    implements BeaconChainControllerFacade {

  public static BeaconChainControllerFactory createLateInitFactory(
      final BeaconChainControllerFactory delegateFactory) {
    return (serviceConfig, beaconConfig) ->
        new LateInitDelegateBeaconChainController(serviceConfig, beaconConfig, delegateFactory);
  }

  private static final Logger LOG = LogManager.getLogger();

  private final ServiceConfig serviceConfig;
  private final BeaconChainConfiguration beaconConfig;
  private final BeaconChainControllerFactory delegateFactory;

  private volatile BeaconChainControllerFacade delegate;

  public LateInitDelegateBeaconChainController(
      final ServiceConfig serviceConfig,
      final BeaconChainConfiguration beaconConfig,
      final BeaconChainControllerFactory delegateFactory) {
    this.serviceConfig = serviceConfig;
    this.beaconConfig = beaconConfig;
    this.delegateFactory = delegateFactory;
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOG.info("Starting BeaconChainController...");
    this.delegate = delegateFactory.create(serviceConfig, beaconConfig);

    SafeFuture<?> startFuture = this.delegate.start();
    LOG.info("BeaconChainController start complete");

    return startFuture;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return this.delegate.stop();
  }

  @Override
  public Spec getSpec() {
    return delegate.getSpec();
  }

  @Override
  public TimeProvider getTimeProvider() {
    return delegate.getTimeProvider();
  }

  @Override
  public AsyncRunnerFactory getAsyncRunnerFactory() {
    return delegate.getAsyncRunnerFactory();
  }

  @Override
  public SignatureVerificationService getSignatureVerificationService() {
    return delegate.getSignatureVerificationService();
  }

  @Override
  public RecentChainData getRecentChainData() {
    return delegate.getRecentChainData();
  }

  @Override
  public CombinedChainDataClient getCombinedChainDataClient() {
    return delegate.getCombinedChainDataClient();
  }

  @Override
  public Eth2P2PNetwork getP2pNetwork() {
    return delegate.getP2pNetwork();
  }

  @Override
  public Optional<BeaconRestApi> getBeaconRestAPI() {
    return delegate.getBeaconRestAPI();
  }

  @Override
  public SyncService getSyncService() {
    return delegate.getSyncService();
  }

  @Override
  public ForkChoice getForkChoice() {
    return delegate.getForkChoice();
  }
}
