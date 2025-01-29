/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.logic.versions.eip7732.util;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.constants.PayloadStatus;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedPayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.electra.util.AttestationUtilElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class AttestationUtilEip7732 extends AttestationUtilElectra {
  public AttestationUtilEip7732(
      final SpecConfig specConfig,
      final SchemaDefinitions schemaDefinitions,
      final BeaconStateAccessors beaconStateAccessors,
      final MiscHelpers miscHelpers) {
    super(specConfig, schemaDefinitions, beaconStateAccessors, miscHelpers);
  }

  @Override
  public Optional<SlotInclusionGossipValidationResult> performSlotInclusionGossipValidation(
      final PayloadAttestationData payloadAttestationData,
      final UInt64 genesisTime,
      final UInt64 currentTimeMillis) {
    final UInt64 slot = payloadAttestationData.getSlot();
    final UInt64 minimumAllowedTime =
        secondsToMillis(genesisTime.plus(slot.times(specConfig.getSecondsPerSlot())))
            .minusMinZero(specConfig.getMaximumGossipClockDisparity());
    final UInt64 lastAllowedTime =
        secondsToMillis(genesisTime.plus(slot.plus(ONE).times(specConfig.getSecondsPerSlot())))
            .plus(specConfig.getMaximumGossipClockDisparity());
    if (currentTimeMillis.isGreaterThan(lastAllowedTime)) {
      return Optional.of(SlotInclusionGossipValidationResult.IGNORE);
    }
    if (currentTimeMillis.isLessThan(minimumAllowedTime)) {
      return Optional.of(SlotInclusionGossipValidationResult.SAVE_FOR_FUTURE);
    }
    return Optional.empty();
  }

  // EIP-7732 TODO: fix (doesn't work in local interop)
  /** get_attesting_indices is modified to ignore PTC votes */
  @Override
  public IntList getAttestingIndices(final BeaconState state, final Attestation attestation) {
    final IntList attestingIndices = super.getAttestingIndices(state, attestation);
    //    final IntList ptc =
    //        BeaconStateAccessorsEip7732.required(beaconStateAccessors)
    //            .getPtc(state, attestation.getData().getSlot());
    final IntList ptc = IntList.of();
    return IntList.of(attestingIndices.intStream().filter(i -> !ptc.contains(i)).toArray());
  }

  /**
   * Check if ``indexed_payload_attestation`` is not empty, has sorted and unique indices and has a
   * valid aggregate signature.
   */
  public boolean isValidIndexedPayloadAttestation(
      final BeaconState state, final IndexedPayloadAttestation indexedPayloadAttestation) {
    // Verify the data is valid
    if (indexedPayloadAttestation.getData().getPayloadStatus()
        >= PayloadStatus.PAYLOAD_INVALID_STATUS.getCode()) {
      return false;
    }

    final SszUInt64List indices = indexedPayloadAttestation.getAttestingIndices();

    // Verify indices are sorted and unique
    if (indices.isEmpty()
        || !indices
            .asListUnboxed()
            .equals(indices.asListUnboxed().stream().sorted().distinct().toList())) {
      return false;
    }

    // Verify aggregate signature
    final List<BLSPublicKey> publicKeys =
        indices.stream()
            .map(idx -> state.getValidators().get(idx.get().intValue()).getPublicKey())
            .toList();

    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            state.getForkInfo(),
            Domain.PTC_ATTESTER,
            miscHelpers.computeEpochAtSlot(state.getSlot()));

    final Bytes signingRoot =
        miscHelpers.computeSigningRoot(indexedPayloadAttestation.getData(), domain);

    return BLS.fastAggregateVerify(
        publicKeys, signingRoot, indexedPayloadAttestation.getSignature());
  }
}
