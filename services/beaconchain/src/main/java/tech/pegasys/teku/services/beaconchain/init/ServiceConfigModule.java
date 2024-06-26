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
import java.util.function.IntSupplier;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;

@Module
public interface ServiceConfigModule {

  @Qualifier
  @interface RejectedExecutionCountSupplier {}

  @Provides
  static DataDirLayout dataDirLayout(ServiceConfig config) {
    return config.getDataDirLayout();
  }

  @Provides
  static AsyncRunnerFactory asyncRunnerFactory(ServiceConfig config) {
    return config.getAsyncRunnerFactory();
  }

  @Provides
  static TimeProvider timeProvider(ServiceConfig config) {
    return config.getTimeProvider();
  }

  @Provides
  static EventChannels eventChannels(ServiceConfig config) {
    return config.getEventChannels();
  }

  @Provides
  static MetricsSystem metricsSystem(ServiceConfig config) {
    return config.getMetricsSystem();
  }

  @Provides
  @Singleton
  @RejectedExecutionCountSupplier
  static IntSupplier rejectedExecutionCountSupplier(ServiceConfig config) {
    return config.getRejectedExecutionsSupplier();
  }
}
