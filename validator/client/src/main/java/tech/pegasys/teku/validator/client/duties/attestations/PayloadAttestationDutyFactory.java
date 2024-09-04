/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.validator.client.duties.attestations;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.DutyFactory;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;

public class PayloadAttestationDutyFactory implements DutyFactory<PayloadAttestationDuty, Duty> {

  private final Spec spec;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;

  private final ValidatorDutyMetrics validatorDutyMetrics;

  public PayloadAttestationDutyFactory(
      final Spec spec,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final ValidatorDutyMetrics validatorDutyMetrics) {
    this.spec = spec;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.validatorDutyMetrics = validatorDutyMetrics;
  }

  @Override
  public PayloadAttestationDuty createProductionDuty(final UInt64 slot, final Validator validator) {
    return new PayloadAttestationDuty(
        spec,
        slot,
        forkProvider,
        validatorApiChannel,
        new BatchAttestationSendingStrategy<>(validatorApiChannel::sendSignedPayloadAttestations),
        validatorDutyMetrics);
  }

  @Override
  public AggregationDuty createAggregationDuty(final UInt64 slot, final Validator validator) {
    throw new UnsupportedOperationException("Aggregation not supported for payload attestations");
  }

  @Override
  public String getProductionType() {
    return "payload_attestation";
  }

  @Override
  public String getAggregationType() {
    // not used
    return "";
  }
}
