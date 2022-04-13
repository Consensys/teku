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

package tech.pegasys.teku.validator.client.logger;

import java.util.HashSet;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class ValidatorDutyLogger implements ValidatorTimingChannel {
  final Spec spec;
  final Set<DutyLogger> loggers = new HashSet<>();

  public ValidatorDutyLogger(
      AttestationDutyLogger attestationDutiesLogger, BlockDutyLogger blockDutyLogger, Spec spec) {
    loggers.add(attestationDutiesLogger);
    loggers.add(blockDutyLogger);
    this.spec = spec;
  }

  @Override
  public void onSlot(UInt64 slot) {
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    if (epochStartSlot.equals(slot)) {
      loggers.forEach(logger -> logger.onEpochStartSlot(slot));
    } else {
      loggers.forEach(logger -> logger.onSlot(slot));
    }
  }

  @Override
  public void onHeadUpdate(
      UInt64 slot,
      Bytes32 previousDutyDependentRoot,
      Bytes32 currentDutyDependentRoot,
      Bytes32 headBlockRoot) {
    loggers.forEach(DutyLogger::invalidate);
  }

  @Override
  public void onPossibleMissedEvents() {}

  @Override
  public void onValidatorsAdded() {
    loggers.forEach(DutyLogger::invalidate);
  }

  @Override
  public void onBlockProductionDue(UInt64 slot) {}

  @Override
  public void onAttestationCreationDue(UInt64 slot) {}

  @Override
  public void onAttestationAggregationDue(UInt64 slot) {}
}
