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

package tech.pegasys.teku.validator.client.duties.attestations;

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.DutyFactory;

public class AttestationDutyFactory
    implements DutyFactory<AttestationProductionDuty, AggregationDuty> {

  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final boolean sendAttestationsAsBatch;

  public AttestationDutyFactory(
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final boolean sendAttestationsAsBatch) {
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.sendAttestationsAsBatch = sendAttestationsAsBatch;
  }

  @Override
  public AttestationProductionDuty createProductionDuty(
      final UInt64 slot, final Validator validator) {
    return new AttestationProductionDuty(
        slot,
        forkProvider,
        validatorApiChannel,
        sendAttestationsAsBatch
            ? new BatchAttestationSendingStrategy(validatorApiChannel)
            : new IndividualAttestationSendingStrategy(validatorApiChannel));
  }

  @Override
  public AggregationDuty createAggregationDuty(final UInt64 slot, final Validator validator) {
    return new AggregationDuty(slot, validatorApiChannel, forkProvider, VALIDATOR_LOGGER);
  }

  @Override
  public String getProductionType() {
    return "attestation";
  }

  @Override
  public String getAggregationType() {
    return "aggregate";
  }
}
