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

package tech.pegasys.teku.validator.client.duties;

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;

public class ValidatorDutyFactory {
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final Spec spec;
  private final MetricsSystem metricsSystem;

  public ValidatorDutyFactory(
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final Spec spec,
      final MetricsSystem metricsSystem) {
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.spec = spec;
    this.metricsSystem = metricsSystem;
  }

  public BlockProductionDuty createBlockProductionDuty(
      final UInt64 slot, final Validator validator) {
    return new BlockProductionDuty(validator, slot, forkProvider, validatorApiChannel, spec);
  }

  public AttestationProductionDuty createAttestationProductionDuty(final UInt64 slot) {
    return new AttestationProductionDuty(slot, forkProvider, validatorApiChannel);
  }

  public AggregationDuty createAggregationDuty(final UInt64 slot) {
    return new AggregationDuty(slot, validatorApiChannel, forkProvider, VALIDATOR_LOGGER);
  }

  public Function<Bytes32, ScheduledDuties<AttestationProductionDuty, AggregationDuty>>
      forAttestations() {
    return dependentRoot ->
        new ScheduledDuties<>(
            (slot, validator) -> createAttestationProductionDuty(slot),
            (slot, validator) -> createAggregationDuty(slot),
            dependentRoot,
            metricsSystem);
  }

  public Function<Bytes32, ScheduledDuties<BlockProductionDuty, Duty>> forBlocks() {
    return dependentRoot ->
        new ScheduledDuties<>(
            this::createBlockProductionDuty,
            (slot, validator) -> {
              throw new UnsupportedOperationException("No aggregation for blocks");
            },
            dependentRoot,
            metricsSystem);
  }
}
