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
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.AttestationContainer;
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
   *     <a>https://github.com/ethereum/consensus-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_attesting_indices</a>
   */
  @Override
  public IntList getAttestingIndices(
      final BeaconState state, final AttestationContainer attestation) {
    final List<UInt64> committeeIndices =
        attestation
            .getCommitteeIndices()
            .orElseThrow(() -> new IllegalArgumentException("Missing committee indices"));
    final SszList<SszBitlist> aggregationBits =
        attestation.getAggregationBitsElectra().orElseThrow();
    final IntList attestingIndices = new IntArrayList();
    committeeIndices.forEach(
        committeeIndex -> {
          final SszBitlist attesterBits = aggregationBits.get(committeeIndex.intValue());
          final IntList committeeAttesters =
              getCommitteeAttesters(
                  state, attestation.getData().getSlot(), attesterBits, committeeIndex);
          attestingIndices.addAll(committeeAttesters);
        });
    return attestingIndices;
  }

  public IntList getCommitteeAttesters(
      final BeaconState state,
      final UInt64 slot,
      final SszBitlist attesterBits,
      final UInt64 committeeIndex) {
    return IntList.of(
        streamCommitteeAttesters(state, slot, attesterBits, committeeIndex).toArray());
  }

  public IntStream streamCommitteeAttesters(
      final BeaconState state,
      final UInt64 slot,
      final SszBitlist attesterBits,
      final UInt64 committeeIndex) {
    final IntList committee = beaconStateAccessors.getBeaconCommittee(state, slot, committeeIndex);
    final IntList attestingBits = attesterBits.getAllSetBits();
    return attestingBits.intStream().map(committee::getInt);
  }
}
