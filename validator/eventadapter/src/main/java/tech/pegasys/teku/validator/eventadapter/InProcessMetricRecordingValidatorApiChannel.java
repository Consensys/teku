/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.validator.eventadapter;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.metrics.MetricRecordingValidatorApiChannel;

public class InProcessMetricRecordingValidatorApiChannel
    extends MetricRecordingValidatorApiChannel {

  static final String BEACON_NODE_REQUESTS_COUNTER_NAME = "beacon_node_requests_total";

  public InProcessMetricRecordingValidatorApiChannel(
      final MetricsSystem metricsSystem, final ValidatorApiChannel delegate) {
    super(metricsSystem, delegate);
  }

  @Override
  public LabelledMetric<Counter> createCounter(final MetricsSystem metricsSystem) {
    return metricsSystem.createLabelledCounter(
        TekuMetricCategory.VALIDATOR,
        BEACON_NODE_REQUESTS_COUNTER_NAME,
        "Counter recording the number of requests sent to the beacon node",
        "method",
        "outcome");
  }

  @Override
  public void recordRequest(final String methodLabel, final RequestOutcome outcome) {
    beaconNodeRequestsCounter.labels(methodLabel, outcome.toString()).inc();
  }
}
