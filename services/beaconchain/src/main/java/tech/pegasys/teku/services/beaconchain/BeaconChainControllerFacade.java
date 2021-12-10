package tech.pegasys.teku.services.beaconchain;

import java.util.Optional;
import tech.pegasys.teku.beaconrestapi.BeaconRestApi;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public interface BeaconChainControllerFacade {

  Spec getSpec();

  TimeProvider getTimeProvider();

  AsyncRunnerFactory getAsyncRunnerFactory();

  SignatureVerificationService getSignatureVerificationService();

  RecentChainData getRecentChainData();

  CombinedChainDataClient getCombinedChainDataClient();

  Eth2P2PNetwork getP2pNetwork();

  Optional<BeaconRestApi> getBeaconRestAPI();
}
