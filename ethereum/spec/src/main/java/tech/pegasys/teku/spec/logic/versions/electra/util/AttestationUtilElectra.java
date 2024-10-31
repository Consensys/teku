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

package tech.pegasys.teku.spec.logic.versions.electra.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.deneb.util.AttestationUtilDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class AttestationUtilElectra extends AttestationUtilDeneb {
  public AttestationUtilElectra(
      final SpecConfig specConfig,
      final SchemaDefinitions schemaDefinitions,
      final BeaconStateAccessors beaconStateAccessors,
      final MiscHelpers miscHelpers) {
    super(specConfig, schemaDefinitions, beaconStateAccessors, miscHelpers);
  }

  /**
   * Return the attesting indices corresponding to ``aggregation_bits`` and ``committee_bits``.
   *
   * @param state
   * @param attestation
   * @return
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/dev/specs/electra/beacon-chain.md#modified-get_attesting_indices</a>
   */
  @Override
  public IntList getAttestingIndices(final BeaconState state, final Attestation attestation) {
    final List<UInt64> committeeIndices = attestation.getCommitteeIndicesRequired();
    final SszBitlist aggregationBits = attestation.getAggregationBits();
    final IntList attestingIndices = new IntArrayList();
    int committeeOffset = 0;
    for (final UInt64 committeeIndex : committeeIndices) {
      final IntList committee =
          beaconStateAccessors.getBeaconCommittee(
              state, attestation.getData().getSlot(), committeeIndex);
      final IntList committeeAttesters =
          getCommitteeAttesters(committee, aggregationBits, committeeOffset);
      attestingIndices.addAll(committeeAttesters);
      committeeOffset += committee.size();
    }
    return attestingIndices;
  }

  public IntList getCommitteeAttesters(
      final IntList committee, final SszBitlist aggregationBits, final int committeeOffset) {
    return IntList.of(
        streamCommitteeAttesters(committee, aggregationBits, committeeOffset).toArray());
  }

  public IntStream streamCommitteeAttesters(
      final IntList committee, final SszBitlist aggregationBits, final int committeeOffset) {
    return IntStream.range(committeeOffset, committeeOffset + committee.size())
        .filter(aggregationBits::isSet)
        .map(attesterIndex -> committee.getInt(attesterIndex - committeeOffset));
  }

  /**
   * In electra, attestationData must have committee index set to 0
   *
   * @see
   *     <a>https://github.com/ethereum/consensus-specs/blob/dev/specs/electra/validator.md#construct-attestation</a>
   */
  @Override
  public AttestationData getGenericAttestationData(
      final UInt64 slot,
      final BeaconState state,
      final BeaconBlockSummary block,
      final UInt64 committeeIndex) {
    return super.getGenericAttestationData(slot, state, block, UInt64.ZERO);
  }
}
