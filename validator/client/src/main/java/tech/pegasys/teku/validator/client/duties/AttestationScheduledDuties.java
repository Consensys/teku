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

package tech.pegasys.teku.validator.client.duties;

import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.validator.client.Validator;

public class AttestationScheduledDuties extends ScheduledDuties {

  protected final NavigableMap<UInt64, AttestationProductionDuty> attestationProductionDuties =
      new TreeMap<>();
  protected final NavigableMap<UInt64, AggregationDuty> aggregationDuties = new TreeMap<>();

  public AttestationScheduledDuties(
      final ValidatorDutyFactory dutyFactory,
      final Bytes32 dependentRoot,
      final MetricsSystem metricsSystem) {
    super(dutyFactory, dependentRoot, metricsSystem);
  }

  @Override
  public synchronized int countDuties() {
    return attestationProductionDuties.size() + aggregationDuties.size();
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

  public synchronized void produceAttestations(final UInt64 slot) {
    performDutyForSlot(attestationProductionDuties, slot);
  }

  public synchronized void performAggregation(final UInt64 slot) {
    performDutyForSlot(aggregationDuties, slot);
  }
}
