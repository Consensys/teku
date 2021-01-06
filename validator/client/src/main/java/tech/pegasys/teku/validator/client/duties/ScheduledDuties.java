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

import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.Validator;

public class ScheduledDuties {
  private final NavigableMap<UInt64, BlockProductionDuty> blockProductionDuties = new TreeMap<>();
  private final NavigableMap<UInt64, AttestationProductionDuty> attestationProductionDuties =
      new TreeMap<>();
  private final NavigableMap<UInt64, AggregationDuty> aggregationDuties = new TreeMap<>();

  private final ValidatorDutyFactory dutyFactory;
  private final Bytes32 dependentRoot;

  public ScheduledDuties(final ValidatorDutyFactory dutyFactory, final Bytes32 dependentRoot) {
    this.dutyFactory = dutyFactory;
    this.dependentRoot = dependentRoot;
  }

  public Bytes32 getDependentRoot() {
    return dependentRoot;
  }

  public synchronized void scheduleBlockProduction(final UInt64 slot, final Validator validator) {
    blockProductionDuties.put(slot, dutyFactory.createBlockProductionDuty(slot, validator));
  }

  public synchronized SafeFuture<Optional<AttestationData>> scheduleAttestationProduction(
      final UInt64 slot,
      final Validator validator,
      final int attestationCommitteeIndex,
      final int attestationCommitteePosition,
      final int attestationCommitteeSize,
      final int validatorIndex) {
    return attestationProductionDuties
        .computeIfAbsent(slot, dutyFactory::createAttestationProductionDuty)
        .addValidator(
            validator,
            attestationCommitteeIndex,
            attestationCommitteePosition,
            validatorIndex,
            attestationCommitteeSize);
  }

  public synchronized void scheduleAggregationDuties(
      final UInt64 slot,
      final Validator validator,
      final int validatorIndex,
      final BLSSignature slotSignature,
      final int attestationCommitteeIndex,
      final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture) {
    aggregationDuties
        .computeIfAbsent(slot, dutyFactory::createAggregationDuty)
        .addValidator(
            validator,
            validatorIndex,
            slotSignature,
            attestationCommitteeIndex,
            unsignedAttestationFuture);
  }

  public synchronized void produceBlock(final UInt64 slot) {
    performDutyForSlot(blockProductionDuties, slot);
  }

  public synchronized void produceAttestations(final UInt64 slot) {
    performDutyForSlot(attestationProductionDuties, slot);
  }

  public synchronized void performAggregation(final UInt64 slot) {
    performDutyForSlot(aggregationDuties, slot);
  }

  private void performDutyForSlot(
      final NavigableMap<UInt64, ? extends Duty> duties, final UInt64 slot) {
    discardDutiesBeforeSlot(duties, slot);

    final Duty duty = duties.remove(slot);
    if (duty == null) {
      return;
    }
    duty.performDuty()
        .finish(
            result ->
                result.report(
                    duty.getProducedType(), slot, duty.getValidatorIdString(), VALIDATOR_LOGGER),
            error ->
                VALIDATOR_LOGGER.dutyFailed(
                    duty.getProducedType(), slot, duty.getValidatorIdString(), error));
  }

  private void discardDutiesBeforeSlot(
      final NavigableMap<UInt64, ? extends Duty> duties, final UInt64 slot) {
    duties.subMap(UInt64.ZERO, true, slot, false).clear();
  }

  public int countDuties() {
    return blockProductionDuties.size()
        + attestationProductionDuties.size()
        + aggregationDuties.size();
  }
}
