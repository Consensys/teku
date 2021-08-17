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

package tech.pegasys.teku.validator.client.duties.attestations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.DutyResult;

public class AttestationProductionDuty implements Duty {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Integer, ScheduledCommittee> validatorsByCommitteeIndex = new HashMap<>();
  private final UInt64 slot;
  private final ForkProvider forkProvider;
  private final AttestationProductionStrategy attestationProductionStrategy;

  public AttestationProductionDuty(
      final UInt64 slot,
      final ForkProvider forkProvider,
      final AttestationProductionStrategy attestationProductionStrategy) {
    this.slot = slot;
    this.forkProvider = forkProvider;
    this.attestationProductionStrategy = attestationProductionStrategy;
  }

  /**
   * Adds a validator that should produce an attestation in this slot.
   *
   * @param validator the validator to produce an attestation
   * @param attestationCommitteeIndex the committee index for the validator
   * @param committeePosition the validator's position within the committee
   * @param validatorIndex the index of the validator
   * @param committeeSize the number of validators in the committee
   * @return a future which will be completed with the unsigned attestation for the committee.
   */
  public SafeFuture<Optional<AttestationData>> addValidator(
      final Validator validator,
      final int attestationCommitteeIndex,
      final int committeePosition,
      final int validatorIndex,
      final int committeeSize) {
    final ScheduledCommittee committee =
        validatorsByCommitteeIndex.computeIfAbsent(
            attestationCommitteeIndex, key -> new ScheduledCommittee());
    committee.addValidator(validator, committeePosition, validatorIndex, committeeSize);
    return committee.getAttestationDataFuture();
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Creating attestations at slot {}", slot);
    if (validatorsByCommitteeIndex.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NO_OP);
    }
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(
            forkInfo ->
                attestationProductionStrategy.produceAttestations(
                    slot, forkInfo, validatorsByCommitteeIndex));
  }
}
