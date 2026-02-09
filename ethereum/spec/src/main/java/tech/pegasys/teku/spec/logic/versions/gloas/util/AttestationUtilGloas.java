/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.gloas.util;

import com.google.common.collect.Comparators;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.IndexedPayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AttestationValidationResult;
import tech.pegasys.teku.spec.logic.versions.electra.util.AttestationUtilElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class AttestationUtilGloas extends AttestationUtilElectra {

  public AttestationUtilGloas(
      final SpecConfigGloas specConfig,
      final SchemaDefinitionsGloas schemaDefinitions,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final MiscHelpersGloas miscHelpers) {
    super(specConfig, schemaDefinitions, beaconStateAccessors, miscHelpers);
  }

  /**
   * is_valid_indexed_payload_attestation
   *
   * <p>Check if ``attestation`` is non-empty, has sorted indices, and has a valid aggregate
   * signature.
   */
  public boolean isValidIndexedPayloadAttestation(
      final BeaconState state, final IndexedPayloadAttestation attestation) {
    // Verify indices are non-empty and sorted
    final List<UInt64> indices = attestation.getAttestingIndices().asListUnboxed();
    if (indices.isEmpty() || !Comparators.isInOrder(indices, UInt64::compareTo)) {
      return false;
    }
    // Verify aggregate signature
    final List<BLSPublicKey> pubKeys =
        indices.stream()
            .map(index -> state.getValidators().get(index.intValue()).getPublicKey())
            .toList();
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            state.getForkInfo(),
            Domain.PTC_ATTESTER,
            miscHelpers.computeEpochAtSlot(attestation.getData().getSlot()));
    final Bytes signingRoot = miscHelpers.computeSigningRoot(attestation.getData(), domain);
    return BLS.fastAggregateVerify(pubKeys, signingRoot, attestation.getSignature());
  }

  @Override
  public AttestationValidationResult validateIndexValue(final UInt64 index) {
    // [REJECT] attestation.data.index < 2
    if (!index.isLessThan(2)) {
      return AttestationValidationResult.invalid(
          () ->
              String.format("Attestation data index must be 0 or 1 for Gloas, but was %s.", index));
    }
    return AttestationValidationResult.VALID;
  }

  @Override
  public AttestationValidationResult validatePayloadStatus(
      final AttestationData attestationData, final Optional<UInt64> maybeBlockSlot) {
    // [REJECT] attestation.data.index == 0 if block.slot == attestation.data.slot.
    return maybeBlockSlot
        .map(
            blockSlot -> {
              if (blockSlot.equals(attestationData.getSlot())
                  && !attestationData.getIndex().isZero()) {
                return AttestationValidationResult.invalid(
                    () ->
                        String.format(
                            "Payload status must be 0, but was %s.", attestationData.getIndex()));
              } else {
                return AttestationValidationResult.VALID;
              }
            })
        .orElse(AttestationValidationResult.VALID);
  }
}
