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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;

public class AttestationProductionDuty implements Duty {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Integer, ScheduledCommittee> validatorsByCommitteeIndex = new HashMap<>();
  private final UInt64 slot;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final SendingStrategy<Attestation> sendingStrategy;

  public AttestationProductionDuty(
      final UInt64 slot,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final SendingStrategy<Attestation> sendingStrategy) {
    this.slot = slot;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.sendingStrategy = sendingStrategy;
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
                sendingStrategy.send(
                    produceAllAttestations(slot, forkInfo, validatorsByCommitteeIndex)));
  }

  private Stream<SafeFuture<ProductionResult<Attestation>>> produceAllAttestations(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final Map<Integer, ScheduledCommittee> validatorsByCommitteeIndex) {
    return validatorsByCommitteeIndex.entrySet().stream()
        .flatMap(
            entry ->
                produceAttestationsForCommittee(slot, forkInfo, entry.getKey(), entry.getValue())
                    .stream());
  }

  private List<SafeFuture<ProductionResult<Attestation>>> produceAttestationsForCommittee(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final int committeeIndex,
      final ScheduledCommittee committee) {
    final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture =
        validatorApiChannel.createAttestationData(slot, committeeIndex);
    unsignedAttestationFuture.propagateTo(committee.getAttestationDataFuture());

    return committee.getValidators().stream()
        .map(
            validator ->
                signAttestationForValidatorInCommittee(
                    slot, forkInfo, committeeIndex, validator, unsignedAttestationFuture))
        .collect(toList());
  }

  private SafeFuture<ProductionResult<Attestation>> signAttestationForValidatorInCommittee(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final int committeeIndex,
      final ValidatorWithCommitteePositionAndIndex validator,
      final SafeFuture<Optional<AttestationData>> attestationDataFuture) {
    return attestationDataFuture
        .thenCompose(
            maybeUnsignedAttestation ->
                maybeUnsignedAttestation
                    .map(
                        attestationData ->
                            signAttestationForValidator(slot, forkInfo, attestationData, validator))
                    .orElseGet(
                        () ->
                            SafeFuture.completedFuture(
                                ProductionResult.failure(
                                    validator.getPublicKey(),
                                    new IllegalStateException(
                                        "Unable to produce attestation for slot "
                                            + slot
                                            + " with committee "
                                            + committeeIndex
                                            + " because chain data was unavailable")))))
        .exceptionally(error -> ProductionResult.failure(validator.getPublicKey(), error));
  }

  private SafeFuture<ProductionResult<Attestation>> signAttestationForValidator(
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
        .thenApply(signature -> createSignedAttestation(attestationData, validator, signature))
        .thenApply(
            attestation ->
                ProductionResult.success(
                    validator.getPublicKey(), attestationData.getBeacon_block_root(), attestation));
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
