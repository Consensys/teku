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

package tech.pegasys.teku.validator.remote;

import okhttp3.HttpUrl;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.beaconnode.metrics.MetricRecordingValidatorApiChannel;

public class RemoteMetricRecordingValidatorApiChannel extends MetricRecordingValidatorApiChannel
    implements RemoteValidatorApiChannel {

  private final RemoteValidatorApiChannel delegate;

  public RemoteMetricRecordingValidatorApiChannel(
      final MetricsSystem metricsSystem, final RemoteValidatorApiChannel delegate) {
    super(metricsSystem, delegate);
    this.delegate = delegate;
  }

  @Override
  public LabelledMetric<Counter> createCounter(final MetricsSystem metricsSystem) {
    return metricsSystem.createLabelledCounter(
        TekuMetricCategory.VALIDATOR,
        BEACON_NODE_REQUESTS_COUNTER_NAME,
        "Counter recording the number of remote requests sent to the configured beacon node(s)",
        "endpoint",
        "method",
        "outcome");
  }

  @Override
  public void recordRequest(final String methodLabel, final RequestOutcome outcome) {
    beaconNodeRequestsCounter
        .labels(getEndpoint().toString(), methodLabel, outcome.toString())
        .inc();
  }

  @Override
  public HttpUrl getEndpoint() {
    return delegate.getEndpoint();
  }

  @Override
  public SafeFuture<SyncingStatus> getSyncingStatus() {
    return delegate.getSyncingStatus();
  }
}
