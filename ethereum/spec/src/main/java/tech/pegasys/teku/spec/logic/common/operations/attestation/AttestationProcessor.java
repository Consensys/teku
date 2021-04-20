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

package tech.pegasys.teku.spec.logic.common.operations.attestation;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.ssz.SszList;

public abstract class AttestationProcessor {

  protected final BeaconStateUtil beaconStateUtil;
  protected final AttestationUtil attestationUtil;
  private final AttestationDataStateTransitionValidator attestationValidator;

  protected AttestationProcessor(
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final AttestationDataStateTransitionValidator attestationValidator) {
    this.beaconStateUtil = beaconStateUtil;
    this.attestationUtil = attestationUtil;
    this.attestationValidator = attestationValidator;
  }

  public Optional<OperationInvalidReason> validateAttestation(
      final BeaconState state, final AttestationData data) {
    return attestationValidator.validate(state, data);
  }

  protected void assertAttestationValid(
      final MutableBeaconState state, final Attestation attestation) {
    final AttestationData data = attestation.getData();

    final Optional<OperationInvalidReason> invalidReason = validateAttestation(state, data);
    checkArgument(
        invalidReason.isEmpty(),
        "process_attestations: %s",
        invalidReason.map(OperationInvalidReason::describe).orElse(""));

    List<Integer> committee =
        beaconStateUtil.getBeaconCommittee(state, data.getSlot(), data.getIndex());
    checkArgument(
        attestation.getAggregation_bits().size() == committee.size(),
        "process_attestations: Attestation aggregation bits and committee don't have the same length");
  }

  public void processAttestations(
      MutableBeaconState state,
      SszList<Attestation> attestations,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    processAttestations(state, attestations, indexedAttestationCache, BLSSignatureVerifier.SIMPLE);
  }

  public void processAttestations(
      MutableBeaconState state,
      SszList<Attestation> attestations,
      IndexedAttestationCache indexedAttestationCache,
      BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    final IndexedAttestationProvider indexedAttestationProvider =
        createIndexedAttestationProvider(state, indexedAttestationCache);

    for (Attestation attestation : attestations) {
      // Validate
      assertAttestationValid(state, attestation);
      processAttestation(state, attestation, indexedAttestationProvider);
      verifyAttestationSignature(state, indexedAttestationProvider, signatureVerifier, attestation);
    }
  }

  public void verifyAttestationSignatures(
      BeaconState state,
      SszList<Attestation> attestations,
      BLSSignatureVerifier signatureVerifier,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    verifyAttestationSignatures(
        state,
        attestations,
        signatureVerifier,
        createIndexedAttestationProvider(state, indexedAttestationCache));
  }

  protected void verifyAttestationSignatures(
      BeaconState state,
      SszList<Attestation> attestations,
      BLSSignatureVerifier signatureVerifier,
      IndexedAttestationProvider indexedAttestationProvider)
      throws BlockProcessingException {

    Optional<AttestationProcessingResult> processResult =
        attestations.stream()
            .map(indexedAttestationProvider::getIndexedAttestation)
            .map(
                attestation ->
                    attestationUtil.isValidIndexedAttestation(
                        state, attestation, signatureVerifier))
            .filter(result -> !result.isSuccessful())
            .findAny();
    if (processResult.isPresent()) {
      throw new BlockProcessingException(
          "Invalid attestation: " + processResult.get().getInvalidReason());
    }
  }

  public void verifyAttestationSignature(
      final MutableBeaconState state,
      final IndexedAttestationProvider indexedAttestationProvider,
      final BLSSignatureVerifier signatureVerifier,
      final Attestation attestation)
      throws BlockProcessingException {
    final IndexedAttestation indexedAttestation =
        indexedAttestationProvider.getIndexedAttestation(attestation);
    final AttestationProcessingResult validationResult =
        attestationUtil.isValidIndexedAttestation(state, indexedAttestation, signatureVerifier);
    if (!validationResult.isSuccessful()) {
      throw new BlockProcessingException(
          "Invalid attestation: " + validationResult.getInvalidReason());
    }
  }

  private IndexedAttestationProvider createIndexedAttestationProvider(
      BeaconState state, IndexedAttestationCache indexedAttestationCache) {
    return (attestation) ->
        indexedAttestationCache.computeIfAbsent(
            attestation, () -> attestationUtil.getIndexedAttestation(state, attestation));
  }

  /**
   * Corresponds to fork-specific logic from "process_attestation" spec method. Common validation
   * and signature verification logic can be found in {@link #processAttestations}.
   *
   * @param genericState The state corresponding to the block being processed
   * @param attestation An attestation in the body of the block being processed
   * @param indexedAttestationProvider provider for indexed attestations
   */
  protected abstract void processAttestation(
      final MutableBeaconState genericState,
      final Attestation attestation,
      final IndexedAttestationProvider indexedAttestationProvider);

  protected interface IndexedAttestationProvider {
    IndexedAttestation getIndexedAttestation(final Attestation attestation);
  }
}
