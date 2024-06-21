package tech.pegasys.teku.services.beaconchain.init;

import dagger.Component;
import javax.inject.Singleton;

import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beaconrestapi.BeaconRestApi;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.services.beaconchain.BeaconChainController;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

import java.util.Optional;

@Singleton
@Component(
    modules = {
      AsyncRunnerModule.class,
      BeaconConfigModule.class,
      BeaconModule.class,
      BlobModule.class,
      ChannelsModule.class,
      CryptoModule.class,
      DataProviderModule.class,
      ExternalDependenciesModule.class,
      ForkChoiceModule.class,
      LoggingModule.class,
      MainModule.class,
      MetricsModule.class,
      NetworkModule.class,
      PoolAndCachesModule.class,
      PowModule.class,
      ServiceConfigModule.class,
      SpecModule.class,
      StorageModule.class,
      SubnetsModule.class,
      SyncModule.class,
      ValidatorModule.class,
      VerifyModule.class,
      WSModule.class
    })
public interface BeaconChainControllerComponent {

  MainModule.ServiceStarter starter();

  MainModule.ServiceStopper stopper();

  Spec getSpec();

  TimeProvider getTimeProvider();

  AsyncRunnerFactory getAsyncRunnerFactory();

  SignatureVerificationService getSignatureVerificationService();

  RecentChainData getRecentChainData();

  CombinedChainDataClient getCombinedChainDataClient();

  Eth2P2PNetwork getP2pNetwork();

  Optional<BeaconRestApi> getBeaconRestAPI();

  SyncService getSyncService();

  ForkChoice getForkChoice();
}
