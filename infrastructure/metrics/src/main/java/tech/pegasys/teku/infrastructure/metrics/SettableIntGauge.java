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

package tech.pegasys.teku.infrastructure.metrics;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.concurrent.atomic.AtomicInteger;

public class SettableIntGauge {

  private final AtomicInteger valueHolder;

  private SettableIntGauge(AtomicInteger valueHolder) {
    this.valueHolder = valueHolder;
  }

  public static SettableIntGauge create(
          MetricsSystem metricsSystem, MetricCategory category, String name, String help) {
    AtomicInteger valueHolder = new AtomicInteger();
    metricsSystem.createGauge(category, name, help, valueHolder::get);
    return new SettableIntGauge(valueHolder);
  }

  public void set(int value) {
    valueHolder.set(value);
  }
}
