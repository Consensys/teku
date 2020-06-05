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

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.util.async.SafeFuture.failedFuture;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;

public class AttestationProductionDuty implements Duty {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Integer, Committee> validatorsByCommitteeIndex = new HashMap<>();
  private final UnsignedLong slot;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;

  public AttestationProductionDuty(
      final UnsignedLong slot,
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
   * @return a future which will be completed with the unsigned attestation for the committee.
   */
  public SafeFuture<Optional<Attestation>> addValidator(
      final Validator validator, final int attestationCommitteeIndex, final int committeePosition) {
    final Committee committee =
        validatorsByCommitteeIndex.computeIfAbsent(
            attestationCommitteeIndex, key -> new Committee());
    committee.addValidator(validator, committeePosition);
    return committee.attestationFuture;
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
    final SafeFuture<Optional<Attestation>> unsignedAttestationFuture =
        validatorApiChannel.createUnsignedAttestation(slot, committeeIndex);
    unsignedAttestationFuture.propagateTo(committee.attestationFuture);
    return unsignedAttestationFuture.thenCompose(
        maybeUnsignedAttestation ->
            maybeUnsignedAttestation
                .map(attestation -> signAttestationsForCommittee(forkInfo, committee, attestation))
                .orElseGet(
                    () -> {
                      return failedFuture(
                          new IllegalStateException(
                              "Unable to produce attestation for slot "
                                  + slot
                                  + " with committee "
                                  + committeeIndex
                                  + " because chain data was unavailable"));
                    }));
  }

  private SafeFuture<DutyResult> signAttestationsForCommittee(
      final ForkInfo forkInfo, final Committee validators, final Attestation attestation) {
    return DutyResult.combine(
        validators.forEach(
            validator -> signAttestationForValidator(forkInfo, attestation, validator)));
  }

  private SafeFuture<DutyResult> signAttestationForValidator(
      final ForkInfo forkInfo,
      final Attestation attestation,
      final ValidatorWithCommitteePosition validator) {
    return validator
        .getSigner()
        .signAttestationData(attestation.getData(), forkInfo)
        .thenApply(signature -> createSignedAttestation(attestation, validator, signature))
        .thenAccept(validatorApiChannel::sendSignedAttestation)
        .thenApply(__ -> DutyResult.success(attestation.getData().getBeacon_block_root()));
  }

  private Attestation createSignedAttestation(
      final Attestation attestation,
      final ValidatorWithCommitteePosition validator,
      final BLSSignature signature) {
    final Bitlist aggregationBits = new Bitlist(attestation.getAggregation_bits());
    aggregationBits.setBit(validator.getCommitteePosition());
    return new Attestation(aggregationBits, attestation.getData(), signature);
  }

  private static class Committee {
    private final List<ValidatorWithCommitteePosition> validators = new ArrayList<>();
    private final SafeFuture<Optional<Attestation>> attestationFuture = new SafeFuture<>();

    public synchronized void addValidator(final Validator validator, final int committeePosition) {
      validators.add(new ValidatorWithCommitteePosition(validator, committeePosition));
    }

    public synchronized <T> List<SafeFuture<T>> forEach(
        final Function<ValidatorWithCommitteePosition, SafeFuture<T>> action) {
      return validators.stream().map(action).collect(toList());
    }
  }

  private static class ValidatorWithCommitteePosition {
    private final Validator validator;
    private final int committeePosition;

    private ValidatorWithCommitteePosition(final Validator validator, final int committeePosition) {
      this.validator = validator;
      this.committeePosition = committeePosition;
    }

    public Signer getSigner() {
      return validator.getSigner();
    }

    public int getCommitteePosition() {
      return committeePosition;
    }
  }
}
