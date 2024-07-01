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

import dagger.Module;
import dagger.Provides;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;

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
      final AsyncRunnerFactory asyncRunnerFactory,
      final Eth2NetworkConfiguration eth2NetworkConfig) {
    return asyncRunnerFactory.create(
        "beaconchain",
        eth2NetworkConfig.getAsyncBeaconChainMaxThreads(),
        eth2NetworkConfig.getAsyncBeaconChainMaxQueue());
  }

  @Provides
  @Singleton
  @NetworkAsyncRunner
  static AsyncRunner networkAsyncRunner(
      final AsyncRunnerFactory asyncRunnerFactory,
      final Eth2NetworkConfiguration eth2NetworkConfig) {
    return asyncRunnerFactory.create(
        "p2p", eth2NetworkConfig.getAsyncP2pMaxThreads(), eth2NetworkConfig.getAsyncP2pMaxQueue());
  }

  @Provides
  @Singleton
  @EventAsyncRunner
  static AsyncRunner eventAsyncRunner(final AsyncRunnerFactory asyncRunnerFactory) {
    return asyncRunnerFactory.create("events", 10);
  }

  @Provides
  @Singleton
  @OperationPoolAsyncRunner
  static AsyncRunner operationPoolAsyncRunner(final AsyncRunnerFactory asyncRunnerFactory) {
    return asyncRunnerFactory.create("operationPoolUpdater", 1);
  }

  @Provides
  @Singleton
  @ForkChoiceExecutor
  static AsyncRunnerEventThread forkChoiceExecutor(final AsyncRunnerFactory asyncRunnerFactory) {
    AsyncRunnerEventThread forkChoiceExecutor =
        new AsyncRunnerEventThread("forkchoice", asyncRunnerFactory);
    forkChoiceExecutor.start();
    return forkChoiceExecutor;
  }

  @Provides
  @Singleton
  @ForkChoiceNotifierExecutor
  static AsyncRunnerEventThread forkChoiceNotifierExecutor(
      final AsyncRunnerFactory asyncRunnerFactory) {
    AsyncRunnerEventThread forkChoiceNotifierExecutor =
        new AsyncRunnerEventThread("forkChoiceNotifier", asyncRunnerFactory);
    forkChoiceNotifierExecutor.start();
    return forkChoiceNotifierExecutor;
  }
}
