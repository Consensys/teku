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

package tech.pegasys.teku.services.remotevalidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.common.util.concurrent.AtomicDouble;
import org.assertj.core.util.introspection.FieldSupport;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.metrics.SettableGauge;

class RemoteValidatorMetricsTest {

  private final MetricsSystem metricsSystem = mock(MetricsSystem.class);

  private final RemoteValidatorMetrics metrics = new RemoteValidatorMetrics(metricsSystem);

  @Test
  public void newMetricsInstance_ShouldCreateConnectedValidatorGauge() {
    // field is initialized during RemoteValidatorMetrics construction
    final SettableGauge connectedValidatorsGauge = getConnectedValidatorsGauge();
    assertThat(connectedValidatorsGauge).isNotNull();
  }

  @Test
  public void updateConnectedValidators_ShouldSetCorrectGaugeValue() {
    metrics.updateConnectedValidators(123);

    assertThat(getConnectedValidatorsGaugeValue()).isEqualTo(123.0);
  }

  private SettableGauge getConnectedValidatorsGauge() {
    return FieldSupport.EXTRACTION.fieldValue(
        "connectedValidatorsGauge", SettableGauge.class, metrics);
  }

  private double getConnectedValidatorsGaugeValue() {
    return FieldSupport.EXTRACTION
        .fieldValue("valueHolder", AtomicDouble.class, getConnectedValidatorsGauge())
        .get();
  }
}
