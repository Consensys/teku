/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.data.publisher;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import org.hyperledger.besu.plugin.services.MetricsSystem;

public class MinimalMetricData extends BaseMetricData {

  private final MetricsSystem metricsSystem;

  @JsonCreator
  public MinimalMetricData(
      @JsonProperty("version") int version,
      @JsonProperty("timestamp") long timestamp,
      @JsonProperty("process") String process,
      MetricsSystem metricsSystem) {
    super(version, timestamp, process);
    this.metricsSystem = metricsSystem;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MinimalMetricData that = (MinimalMetricData) o;
    return metricsSystem.equals(that.metricsSystem);
  }

  @Override
  public int hashCode() {
    return Objects.hash(metricsSystem);
  }
}
