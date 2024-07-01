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

import dagger.Component;
import javax.inject.Singleton;
import tech.pegasys.teku.services.beaconchain.BeaconChainControllerFacade;

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

  BeaconChainControllerFacade beaconChainController();
}
