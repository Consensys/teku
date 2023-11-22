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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.validator.beaconnode.metrics.MetricRecordingValidatorApiChannel.BEACON_NODE_REQUESTS_COUNTER_NAME;

import okhttp3.HttpUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.validator.beaconnode.metrics.MetricRecordingValidatorApiChannel.RequestOutcome;

public class RemoteMetricRecordingValidatorApiChannelTest {

  private static final String DELEGATE_ENDPOINT = "http://delegate.com/";

  private final RemoteValidatorApiChannel delegate = mock(RemoteValidatorApiChannel.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final RemoteMetricRecordingValidatorApiChannel apiChannel =
      new RemoteMetricRecordingValidatorApiChannel(metricsSystem, delegate);

  @BeforeEach
  public void setup() {
    when(delegate.getEndpoint()).thenReturn(HttpUrl.get(DELEGATE_ENDPOINT));
  }

  @Test
  public void recordsRequest() {
    final String methodLabel = "foo";
    final RequestOutcome outcome = RequestOutcome.SUCCESS;

    apiChannel.recordRequest(methodLabel, outcome);

    assertThat(getCounterValue(methodLabel, outcome)).isEqualTo(1);
  }

  private long getCounterValue(final String methodLabel, final RequestOutcome outcome) {
    return metricsSystem
        .getCounter(TekuMetricCategory.VALIDATOR, BEACON_NODE_REQUESTS_COUNTER_NAME)
        .getValue(DELEGATE_ENDPOINT, methodLabel, outcome.toString());
  }
}
