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

package tech.pegasys.artemis.validator.client.duties;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.client.ForkProvider;
import tech.pegasys.artemis.validator.client.Validator;
import tech.pegasys.artemis.validator.client.signer.Signer;

public class AttestationProductionDuty implements Duty {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Integer, List<ValidatorWithIndex>> validators = new HashMap<>();
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

  public synchronized void addValidator(
      final int attestationCommitteeIndex, final Validator validator, final int validatorIndex) {
    validators
        .computeIfAbsent(attestationCommitteeIndex, key -> new ArrayList<>())
        .add(new ValidatorWithIndex(validator, validatorIndex));
  }

  @Override
  public synchronized void performDuty() {
    LOG.trace("Creating attestations at slot {}", slot);
    forkProvider
        .getFork()
        .thenCompose(this::produceAttestations)
        .finish(
            () -> LOG.debug("Created attestations at slot {}", slot),
            error -> LOG.error("Failed to create attestations at slot " + slot, error));
  }

  @SuppressWarnings("rawtypes")
  private SafeFuture<Void> produceAttestations(final Fork fork) {
    final SafeFuture[] futures =
        validators.entrySet().stream()
            .map(entry -> produceAttestationsForCommittee(fork, entry.getKey(), entry.getValue()))
            .toArray(SafeFuture[]::new);
    return SafeFuture.allOf(futures);
  }

  private SafeFuture<Void> produceAttestationsForCommittee(
      final Fork fork, final int committeeIndex, final List<ValidatorWithIndex> validators) {
    return validatorApiChannel
        .createUnsignedAttestation(slot, committeeIndex)
        .thenCompose(
            maybeUnsignedAttestation -> {
              if (maybeUnsignedAttestation.isEmpty()) {
                LOG.error(
                    "Unable to produce attestation for slot {} with committee {} because chain data was unavailable",
                    slot,
                    committeeIndex);
                return SafeFuture.COMPLETE;
              }
              final Attestation attestation = maybeUnsignedAttestation.get();
              return SafeFuture.allOf(
                  validators.stream()
                      .map(
                          validator -> produceAttestationForValidator(fork, attestation, validator))
                      .toArray(SafeFuture[]::new));
            });
  }

  private SafeFuture<Void> produceAttestationForValidator(
      final Fork fork, final Attestation attestation, final ValidatorWithIndex validator) {
    return validator
        .getSigner()
        .signAttestationData(attestation.getData(), fork)
        .thenApply(signature -> createSignedAttestation(attestation, validator, signature))
        .thenAccept(validatorApiChannel::sendSignedAttestation);
  }

  private Attestation createSignedAttestation(
      final Attestation attestation,
      final ValidatorWithIndex validator,
      final BLSSignature signature) {
    final Bitlist aggregationBits = new Bitlist(attestation.getAggregation_bits());
    aggregationBits.setBit(validator.getValidatorIndex());
    return new Attestation(aggregationBits, attestation.getData(), signature);
  }

  private static class ValidatorWithIndex {
    private final Validator validator;
    private final int validatorIndex;

    private ValidatorWithIndex(final Validator validator, final int validatorIndex) {
      this.validator = validator;
      this.validatorIndex = validatorIndex;
    }

    public Signer getSigner() {
      return validator.getSigner();
    }

    public int getValidatorIndex() {
      return validatorIndex;
    }
  }
}
