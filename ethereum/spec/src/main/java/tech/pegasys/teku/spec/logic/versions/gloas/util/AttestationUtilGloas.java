/*
 * Copyright Consensys Software Inc., 2025
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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.IndexedPayloadAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
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
   * <p>Check if ``indexed_payload_attestation`` is non-empty, has sorted indices, and has a valid
   * aggregate signature.
   */
  public boolean isValidIndexedPayloadAttestation(
      final BeaconState state, final IndexedPayloadAttestation indexedPayloadAttestation) {
    // Verify indices are non-empty and sorted
    final List<UInt64> indices = indexedPayloadAttestation.getAttestingIndices().asListUnboxed();
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
            state.getForkInfo(), Domain.PTC_ATTESTER, beaconStateAccessors.getCurrentEpoch(state));
    final Bytes signingRoot =
        miscHelpers.computeSigningRoot(indexedPayloadAttestation.getData(), domain);
    return BLS.fastAggregateVerify(pubKeys, signingRoot, indexedPayloadAttestation.getSignature());
  }
}
