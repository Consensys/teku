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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;
import static tech.pegasys.teku.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;

public class AttestationProductionDuty implements Duty {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Integer, Committee> validatorsByCommitteeIndex = new HashMap<>();
  private final UInt64 slot;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private BLSPublicKey validatorPublicKey;

  public AttestationProductionDuty(
      final UInt64 slot,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel) {
    this.slot = slot;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
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
    final Committee committee =
        validatorsByCommitteeIndex.computeIfAbsent(
            attestationCommitteeIndex, key -> new Committee());
    committee.addValidator(validator, committeePosition, validatorIndex, committeeSize);
    validatorPublicKey = validator.getPublicKey();
    return committee.attestationDataFuture;
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Creating attestations at slot {}", slot);
    if (validatorsByCommitteeIndex.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NO_OP);
    }
    return forkProvider.getForkInfo().thenCompose(this::produceAttestations);
  }

  @Override
  public String getProducedType() {
    return "attestation";
  }

  @Override
  public Optional<BLSPublicKey> getValidatorIdentifier() {
    return Optional.ofNullable(validatorPublicKey);
  }

  private SafeFuture<DutyResult> produceAttestations(final ForkInfo forkInfo) {
    return DutyResult.combine(
        validatorsByCommitteeIndex.entrySet().stream()
            .map(
                entry ->
                    produceAttestationsForCommittee(forkInfo, entry.getKey(), entry.getValue()))
            .collect(toList()));
  }

  private SafeFuture<DutyResult> produceAttestationsForCommittee(
      final ForkInfo forkInfo, final int committeeIndex, final Committee committee) {
    final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture =
        validatorApiChannel.createAttestationData(slot, committeeIndex);
    unsignedAttestationFuture.propagateTo(committee.attestationDataFuture);
    return unsignedAttestationFuture.thenCompose(
        maybeUnsignedAttestation ->
            maybeUnsignedAttestation
                .map(
                    attestationData ->
                        signAttestationsForCommittee(forkInfo, committee, attestationData))
                .orElseGet(
                    () ->
                        failedFuture(
                            new IllegalStateException(
                                "Unable to produce attestation for slot "
                                    + slot
                                    + " with committee "
                                    + committeeIndex
                                    + " because chain data was unavailable"))));
  }

  private SafeFuture<DutyResult> signAttestationsForCommittee(
      final ForkInfo forkInfo, final Committee validators, final AttestationData attestationData) {
    return DutyResult.combine(
        validators.forEach(
            validator -> signAttestationForValidator(forkInfo, attestationData, validator)));
  }

  private SafeFuture<DutyResult> signAttestationForValidator(
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
        .thenAccept(
            signedAttestation ->
                validatorApiChannel.sendSignedAttestation(
                    signedAttestation, Optional.of(validator.getValidatorIndex())))
        .thenApply(__ -> DutyResult.success(attestationData.getBeacon_block_root()));
  }

  private Attestation createSignedAttestation(
      final AttestationData attestationData,
      final ValidatorWithCommitteePositionAndIndex validator,
      final BLSSignature signature) {
    final Bitlist aggregationBits =
        new Bitlist(validator.getCommitteeSize(), MAX_VALIDATORS_PER_COMMITTEE);
    aggregationBits.setBit(validator.getCommitteePosition());
    return new Attestation(aggregationBits, attestationData, signature);
  }

  private static class Committee {
    private final List<ValidatorWithCommitteePositionAndIndex> validators = new ArrayList<>();
    private final SafeFuture<Optional<AttestationData>> attestationDataFuture = new SafeFuture<>();

    public synchronized void addValidator(
        final Validator validator,
        final int committeePosition,
        final int validatorIndex,
        final int committeeSize) {
      validators.add(
          new ValidatorWithCommitteePositionAndIndex(
              validator, committeePosition, validatorIndex, committeeSize));
    }

    public synchronized <T> List<SafeFuture<T>> forEach(
        final Function<ValidatorWithCommitteePositionAndIndex, SafeFuture<T>> action) {
      return validators.stream().map(action).collect(toList());
    }

    @Override
    public String toString() {
      return "Committee{" + validators + '}';
    }
  }

  private static class ValidatorWithCommitteePositionAndIndex {
    private final Validator validator;
    private final int committeePosition;
    private final int validatorIndex;
    private final int committeeSize;

    private ValidatorWithCommitteePositionAndIndex(
        final Validator validator,
        final int committeePosition,
        final int validatorIndex,
        final int committeeSize) {
      this.validator = validator;
      this.committeePosition = committeePosition;
      this.validatorIndex = validatorIndex;
      this.committeeSize = committeeSize;
    }

    public Signer getSigner() {
      return validator.getSigner();
    }

    public int getCommitteePosition() {
      return committeePosition;
    }

    public int getValidatorIndex() {
      return validatorIndex;
    }

    public int getCommitteeSize() {
      return committeeSize;
    }

    @Override
    public String toString() {
      return "ValidatorWithCommitteePositionAndIndex{"
          + "validator="
          + validator
          + ", committeePosition="
          + committeePosition
          + ", validatorIndex="
          + validatorIndex
          + '}';
    }
  }
}
