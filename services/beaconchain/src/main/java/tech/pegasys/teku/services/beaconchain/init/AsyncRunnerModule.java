package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;

import javax.inject.Qualifier;
import javax.inject.Singleton;

@Module
public interface AsyncRunnerModule {

  @Qualifier
  @interface BeaconAsyncRunner {}

  @Qualifier
  @interface EventAsyncRunner {}

  @Qualifier
  @interface NetworkAsyncRunner {}

  @Qualifier
  @interface OperationPoolAsyncRunner {}

  @Qualifier
  @interface ForkChoiceExecutor {}

  @Qualifier
  @interface ForkChoiceNotifierExecutor {}

  @Provides
  @Singleton
  @BeaconAsyncRunner
  static AsyncRunner beaconAsyncRunner(
      AsyncRunnerFactory asyncRunnerFactory, Eth2NetworkConfiguration eth2NetworkConfig) {
    return asyncRunnerFactory.create(
        "beaconchain",
        eth2NetworkConfig.getAsyncBeaconChainMaxThreads(),
        eth2NetworkConfig.getAsyncBeaconChainMaxQueue());
  }

  @Provides
  @Singleton
  @NetworkAsyncRunner
  static AsyncRunner networkAsyncRunner(
      AsyncRunnerFactory asyncRunnerFactory, Eth2NetworkConfiguration eth2NetworkConfig) {
    return asyncRunnerFactory.create(
        "p2p",
        eth2NetworkConfig.getAsyncP2pMaxThreads(),
        eth2NetworkConfig.getAsyncP2pMaxQueue());
  }

  @Provides
  @Singleton
  @EventAsyncRunner
  static AsyncRunner eventAsyncRunner(AsyncRunnerFactory asyncRunnerFactory) {
    return asyncRunnerFactory.create("events", 10);
  }

  @Provides
  @Singleton
  @OperationPoolAsyncRunner
  static AsyncRunner operationPoolAsyncRunner(AsyncRunnerFactory asyncRunnerFactory) {
    return asyncRunnerFactory.create("operationPoolUpdater", 1);
  }

  @Provides
  @Singleton
  @ForkChoiceExecutor
  static AsyncRunnerEventThread forkChoiceExecutor(AsyncRunnerFactory asyncRunnerFactory) {
    return new AsyncRunnerEventThread("forkchoice", asyncRunnerFactory);
  }

  @Provides
  @Singleton
  @ForkChoiceNotifierExecutor
  static AsyncRunnerEventThread forkChoiceNotifierExecutor(AsyncRunnerFactory asyncRunnerFactory) {
    return new AsyncRunnerEventThread("forkChoiceNotifier", asyncRunnerFactory);
  }


}
