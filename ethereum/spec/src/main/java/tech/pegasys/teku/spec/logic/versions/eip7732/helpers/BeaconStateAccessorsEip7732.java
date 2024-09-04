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

package tech.pegasys.teku.spec.logic.versions.eip7732.helpers;

import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigEip7732;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.operations.IndexedPayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedPayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MathHelpers;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;

public class BeaconStateAccessorsEip7732 extends BeaconStateAccessorsElectra {

  private final SpecConfigEip7732 configEip7732;
  private final SchemaDefinitionsEip7732 schemaDefinitionsEip7732;

  public BeaconStateAccessorsEip7732(
      final SpecConfig config,
      final PredicatesElectra predicatesElectra,
      final MiscHelpersElectra miscHelpers,
      final SchemaDefinitionsEip7732 schemaDefinitionsEip7732) {
    super(SpecConfigElectra.required(config), predicatesElectra, miscHelpers);
    this.configEip7732 = config.toVersionEip7732().orElseThrow();
    this.schemaDefinitionsEip7732 = schemaDefinitionsEip7732;
  }

  public static BeaconStateAccessorsEip7732 required(
      final BeaconStateAccessors beaconStateAccessors) {
    checkArgument(
        beaconStateAccessors instanceof BeaconStateAccessorsEip7732,
        "Expected %s but it was %s",
        BeaconStateAccessorsEip7732.class,
        beaconStateAccessors.getClass());
    return (BeaconStateAccessorsEip7732) beaconStateAccessors;
  }

  /** Get the payload timeliness committee for the given ``slot`` */
  public IntList getPtc(final BeaconState state, final UInt64 slot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);

    final UInt64 committeesPerSlot =
        MathHelpers.bitFloor(
            getCommitteeCountPerSlot(state, epoch).min(configEip7732.getPtcSize()));

    final long membersPerCommittee =
        Math.floorDiv(configEip7732.getPtcSize(), committeesPerSlot.longValue());

    final IntList validatorIndices = new IntArrayList(configEip7732.getPtcSize());

    UInt64.range(UInt64.ZERO, committeesPerSlot)
        .forEach(
            idx -> {
              final IntList beaconCommittee = getBeaconCommittee(state, slot, idx);
              for (int i = 0; i < Math.min(membersPerCommittee, beaconCommittee.size()); i++) {
                validatorIndices.addLast(beaconCommittee.getInt(i));
              }
            });

    return validatorIndices;
  }

  /** Return the set of attesting indices corresponding to ``payload_attestation``. */
  public IntList getPayloadAttestingIndices(
      final BeaconState state, final UInt64 slot, final PayloadAttestation payloadAttestation) {
    final IntList ptc = getPtc(state, slot);
    final SszBitvector aggregationBits = payloadAttestation.getAggregationBits();
    final IntList attestingIndices = new IntArrayList();
    for (int i = 0; i < ptc.size(); i++) {
      if (aggregationBits.isSet(i)) {
        final int index = ptc.getInt(i);
        attestingIndices.addLast(index);
      }
    }
    return attestingIndices;
  }

  /** Return the indexed payload attestation corresponding to ``payload_attestation``. */
  public IndexedPayloadAttestation getIndexedPayloadAttestation(
      final BeaconState state, final UInt64 slot, final PayloadAttestation payloadAttestation) {
    final IntList payloadAttestingIndices =
        getPayloadAttestingIndices(state, slot, payloadAttestation);
    final IndexedPayloadAttestationSchema indexedPayloadAttestationSchema =
        schemaDefinitionsEip7732.getIndexedPayloadAttestationSchema();
    final SszUInt64List attestingIndices =
        payloadAttestingIndices
            .intStream()
            .sorted()
            .mapToObj(idx -> SszUInt64.of(UInt64.valueOf(idx)))
            .collect(indexedPayloadAttestationSchema.getAttestingIndicesSchema().collector());
    return indexedPayloadAttestationSchema.create(
        attestingIndices, payloadAttestation.getData(), payloadAttestation.getSignature());
  }
}
