/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.metrics;

import java.util.function.DoubleSupplier;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

public class StubGauge extends StubMetric {

  private final DoubleSupplier supplier;

  protected StubGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final DoubleSupplier supplier) {
    super(category, name, help);
    this.supplier = supplier;
  }

  public double getValue() {
    return supplier.getAsDouble();
  }
}
