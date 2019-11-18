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

package tech.pegasys.artemis.service.serviceutils;

import com.google.common.eventbus.EventBus;
import io.vertx.core.Vertx;
import java.util.Objects;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class ServiceConfig {
  Vertx vertx;
  EventBus eventBus;
  MetricsSystem metricsSystem;
  ArtemisConfiguration config;

  public ServiceConfig(
      EventBus eventBus, Vertx vertx, MetricsSystem metricsSystem, ArtemisConfiguration config) {
    this.eventBus = eventBus;
    this.vertx = vertx;
    this.metricsSystem = metricsSystem;
    this.config = config;
  }

  public EventBus getEventBus() {
    return this.eventBus;
  }

  public void setEventBus(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  public Vertx getVertx() {
    return this.vertx;
  }

  public ArtemisConfiguration getConfig() {
    return this.config;
  }

  public void setConfig(ArtemisConfiguration config) {
    this.config = config;
  }

  public MetricsSystem getMetricsSystem() {
    return metricsSystem;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof ServiceConfig)) {
      return false;
    }
    ServiceConfig serviceConfig = (ServiceConfig) o;
    return Objects.equals(eventBus, serviceConfig.eventBus)
        && Objects.equals(config, serviceConfig.config);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventBus, config);
  }
}
