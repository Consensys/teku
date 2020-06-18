/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.service.serviceutils;

import com.google.common.eventbus.EventBus;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.events.EventChannels;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.config.TekuConfiguration;
import tech.pegasys.teku.util.time.TimeProvider;

public class ServiceConfig {

  private final AsyncRunnerFactory asyncRunnerFactory;
  private final TimeProvider timeProvider;
  private final EventBus eventBus;
  private final EventChannels eventChannels;
  private final MetricsSystem metricsSystem;
  private final TekuConfiguration config;

  public ServiceConfig(
      final AsyncRunnerFactory asyncRunnerFactory,
      final TimeProvider timeProvider,
      final EventBus eventBus,
      final EventChannels eventChannels,
      final MetricsSystem metricsSystem,
      final TekuConfiguration config) {
    this.asyncRunnerFactory = asyncRunnerFactory;
    this.timeProvider = timeProvider;
    this.eventBus = eventBus;
    this.eventChannels = eventChannels;
    this.metricsSystem = metricsSystem;
    this.config = config;
  }

  public TimeProvider getTimeProvider() {
    return timeProvider;
  }

  public EventBus getEventBus() {
    return this.eventBus;
  }

  public EventChannels getEventChannels() {
    return eventChannels;
  }

  public TekuConfiguration getConfig() {
    return this.config;
  }

  public MetricsSystem getMetricsSystem() {
    return metricsSystem;
  }

  public AsyncRunner createAsyncRunner(final String name) {
    return createAsyncRunner(name, Math.max(Runtime.getRuntime().availableProcessors(), 2));
  }

  public AsyncRunner createAsyncRunner(final String name, final int maxThreads) {
    return asyncRunnerFactory.create(name, maxThreads, metricsSystem);
  }
}
