/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.validator.beaconnode.metrics;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class BeaconChainRequestCounter {

  private final LabelledMetric<Counter> counter;

  public BeaconChainRequestCounter(final LabelledMetric<Counter> counter) {
    this.counter = counter;
  }

  public static BeaconChainRequestCounter create(
      final MetricsSystem metricsSystem, final String name, final String help) {
    return new BeaconChainRequestCounter(
        metricsSystem.createLabelledCounter(TekuMetricCategory.VALIDATOR, name, help, "outcome"));
  }

  public void onSuccess() {
    recordRequest(RequestOutcome.SUCCESS);
  }

  public void onDataUnavailable() {
    recordRequest(RequestOutcome.DATA_UNAVAILABLE);
  }

  public void onError() {
    recordRequest(RequestOutcome.ERROR);
  }

  private void recordRequest(final RequestOutcome outcome) {
    counter.labels(outcome.name()).inc();
  }

  public enum RequestOutcome {
    SUCCESS,
    DATA_UNAVAILABLE,
    ERROR
  }
}
