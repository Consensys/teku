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

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.infrastructure.metrics.MetricsPublishCategories;

public class ValidatorMetricData extends GeneralProcessMetricData {

  @JsonProperty("validator_total")
  private final Integer validatorTotal;

  @JsonProperty("validator_active")
  private final Integer validatorActive;

  public ValidatorMetricData(final long timestamp, final MetricsPublisherReader reader) {
    super(timestamp, MetricsPublishCategories.VALIDATOR.getDisplayName(), reader);
    this.validatorActive = reader.getValidatorsActive();
    this.validatorTotal = reader.getValidatorsTotal();
  }

  public Integer getValidatorTotal() {
    return validatorTotal;
  }

  public Integer getValidatorActive() {
    return validatorActive;
  }
}
