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

import static tech.pegasys.teku.logging.ValidatorLogger.VALIDATOR_LOGGER;

import com.google.common.primitives.UnsignedLong;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.validator.client.Validator;

public class ScheduledDuties {
  private final NavigableMap<UnsignedLong, BlockProductionDuty> blockProductionDuties =
      new TreeMap<>();
  private final NavigableMap<UnsignedLong, AttestationProductionDuty> attestationProductionDuties =
      new TreeMap<>();
  private final NavigableMap<UnsignedLong, AggregationDuty> aggregationDuties = new TreeMap<>();

  private final ValidatorDutyFactory dutyFactory;

  public ScheduledDuties(final ValidatorDutyFactory dutyFactory) {
    this.dutyFactory = dutyFactory;
  }

  public synchronized void scheduleBlockProduction(
      final UnsignedLong slot, final Validator validator) {
    blockProductionDuties.put(slot, dutyFactory.createBlockProductionDuty(slot, validator));
  }

  public synchronized SafeFuture<Optional<Attestation>> scheduleAttestationProduction(
      final UnsignedLong slot,
      final Validator validator,
      final int attestationCommitteeIndex,
      final int attestationCommitteePosition) {
    return attestationProductionDuties
        .computeIfAbsent(slot, dutyFactory::createAttestationProductionDuty)
        .addValidator(validator, attestationCommitteeIndex, attestationCommitteePosition);
  }

  public synchronized void scheduleAggregationDuties(
      final UnsignedLong slot,
      final Validator validator,
      final int validatorIndex,
      final BLSSignature slotSignature,
      final int attestationCommitteeIndex,
      final SafeFuture<Optional<Attestation>> unsignedAttestationFuture) {
    aggregationDuties
        .computeIfAbsent(slot, dutyFactory::createAggregationDuty)
        .addValidator(
            validator,
            validatorIndex,
            slotSignature,
            attestationCommitteeIndex,
            unsignedAttestationFuture);
  }

  public synchronized void produceBlock(final UnsignedLong slot) {
    performDutyForSlot(blockProductionDuties, slot);
  }

  public synchronized void produceAttestations(final UnsignedLong slot) {
    performDutyForSlot(attestationProductionDuties, slot);
  }

  public synchronized void performAggregation(final UnsignedLong slot) {
    performDutyForSlot(aggregationDuties, slot);
  }

  private void performDutyForSlot(
      final NavigableMap<UnsignedLong, ? extends Duty> duties, final UnsignedLong slot) {
    discardDutiesBeforeSlot(duties, slot);

    final Duty duty = duties.remove(slot);
    if (duty == null) {
      return;
    }
    duty.performDuty()
        .finish(
            result -> result.report(duty.getProducedType(), slot, VALIDATOR_LOGGER),
            error -> VALIDATOR_LOGGER.dutyFailed(duty.getProducedType(), slot, error));
  }

  private void discardDutiesBeforeSlot(
      final NavigableMap<UnsignedLong, ? extends Duty> duties, final UnsignedLong slot) {
    duties.subMap(UnsignedLong.ZERO, slot).clear();
  }

  public int countDuties() {
    return blockProductionDuties.size()
        + attestationProductionDuties.size()
        + aggregationDuties.size();
  }
}
