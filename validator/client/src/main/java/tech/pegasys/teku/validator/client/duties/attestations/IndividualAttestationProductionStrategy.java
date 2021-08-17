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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.ssz.collections.SszBitlist;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.RestApiReportedException;

public class IndividualAttestationProductionStrategy implements AttestationProductionStrategy {
  private final ValidatorApiChannel validatorApiChannel;

  public IndividualAttestationProductionStrategy(final ValidatorApiChannel validatorApiChannel) {
    this.validatorApiChannel = validatorApiChannel;
  }

  @Override
  public SafeFuture<DutyResult> produceAttestations(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final Map<Integer, ScheduledCommittee> validatorsByCommitteeIndex) {
    return DutyResult.combine(
        validatorsByCommitteeIndex.entrySet().stream()
            .map(
                entry ->
                    produceAttestationsForCommittee(
                        slot, forkInfo, entry.getKey(), entry.getValue()))
            .collect(toList()));
  }

  private SafeFuture<DutyResult> produceAttestationsForCommittee(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final int committeeIndex,
      final ScheduledCommittee committee) {
    final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture =
        validatorApiChannel.createAttestationData(slot, committeeIndex);
    unsignedAttestationFuture.propagateTo(committee.getAttestationDataFuture());
    return unsignedAttestationFuture
        .thenCompose(
            maybeUnsignedAttestation ->
                maybeUnsignedAttestation
                    .map(
                        attestationData ->
                            signAttestationsForCommittee(
                                slot, forkInfo, committee, attestationData))
                    .orElseGet(
                        () ->
                            failedFuture(
                                new IllegalStateException(
                                    "Unable to produce attestation for slot "
                                        + slot
                                        + " with committee "
                                        + committeeIndex
                                        + " because chain data was unavailable"))))
        .exceptionally(
            error ->
                DutyResult.forError(
                    committee.getValidators().stream()
                        .map(ValidatorWithCommitteePositionAndIndex::getPublicKey)
                        .collect(toSet()),
                    error));
  }

  private SafeFuture<DutyResult> signAttestationsForCommittee(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final ScheduledCommittee validators,
      final AttestationData attestationData) {
    return DutyResult.combine(
        validators.forEach(
            validator ->
                signAttestationForValidator(slot, forkInfo, attestationData, validator)
                    .exceptionally(error -> DutyResult.forError(validator.getPublicKey(), error))));
  }

  private SafeFuture<DutyResult> signAttestationForValidator(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final AttestationData attestationData,
      final ValidatorWithCommitteePositionAndIndex validator) {
    checkArgument(
        attestationData.getSlot().equals(slot),
        "Unsigned attestation slot (%s) does not match expected slot %s",
        attestationData.getSlot(),
        slot);
    return validator
        .getSigner()
        .signAttestationData(attestationData, forkInfo)
        .thenApply(
            signature -> List.of(createSignedAttestation(attestationData, validator, signature)))
        .thenCompose(validatorApiChannel::sendSignedAttestations)
        .thenApply(
            errors -> {
              if (errors.isEmpty()) {
                return DutyResult.success(attestationData.getBeacon_block_root());
              } else {
                return DutyResult.forError(
                    validator.getPublicKey(),
                    new RestApiReportedException(errors.get(0).getMessage()));
              }
            });
  }

  private Attestation createSignedAttestation(
      final AttestationData attestationData,
      final ValidatorWithCommitteePositionAndIndex validator,
      final BLSSignature signature) {
    SszBitlist aggregationBits =
        Attestation.SSZ_SCHEMA
            .getAggregationBitsSchema()
            .ofBits(validator.getCommitteeSize(), validator.getCommitteePosition());
    return new Attestation(aggregationBits, attestationData, signature);
  }
}
