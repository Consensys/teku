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

package tech.pegasys.teku.spec.logic.versions.gloas.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.IndexedPayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BeaconStateAccessorsFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class BeaconStateAccessorsGloas extends BeaconStateAccessorsFulu {

  protected final SchemaDefinitionsGloas schemaDefinitions;

  public static BeaconStateAccessorsGloas required(
      final BeaconStateAccessors beaconStateAccessors) {
    checkArgument(
        beaconStateAccessors instanceof BeaconStateAccessorsGloas,
        "Expected %s but it was %s",
        BeaconStateAccessorsGloas.class,
        beaconStateAccessors.getClass());
    return (BeaconStateAccessorsGloas) beaconStateAccessors;
  }

  public BeaconStateAccessorsGloas(
      final SpecConfigGloas config,
      final SchemaDefinitionsGloas schemaDefinitions,
      final PredicatesGloas predicates,
      final MiscHelpersGloas miscHelpers) {
    super(config, predicates, miscHelpers);
    this.schemaDefinitions = schemaDefinitions;
  }

  /**
   * get_indexed_payload_attestation
   *
   * <p>Return the indexed payload attestation corresponding to ``payload_attestation``.
   */
  public IndexedPayloadAttestation getIndexedPayloadAttestation(
      final BeaconState state, final UInt64 slot, final PayloadAttestation payloadAttestation) {
    final IntList ptc = getPtc(state, slot);
    final SszBitvector aggregationBits = payloadAttestation.getAggregationBits();
    final IntList attestingIndices = new IntArrayList();
    for (int i = 0; i < ptc.size(); i++) {
      if (aggregationBits.isSet(i)) {
        final int index = ptc.getInt(i);
        attestingIndices.add(index);
      }
    }
    final SszUInt64List sszAttestingIndices =
        attestingIndices
            .intStream()
            .sorted()
            .mapToObj(idx -> SszUInt64.of(UInt64.valueOf(idx)))
            .collect(
                schemaDefinitions
                    .getIndexedPayloadAttestationSchema()
                    .getAttestingIndicesSchema()
                    .collector());
    return schemaDefinitions
        .getIndexedPayloadAttestationSchema()
        .create(
            sszAttestingIndices, payloadAttestation.getData(), payloadAttestation.getSignature());
  }

  /**
   * get_ptc
   *
   * <p>Get the payload timeliness committee for the given ``slot``
   */
  public IntList getPtc(final BeaconState state, final UInt64 slot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    final Bytes32 seed =
        Hash.sha256(
            Bytes.concatenate(getSeed(state, epoch, Domain.PTC_ATTESTER), uint64ToBytes(slot)));
    final IntList indices = new IntArrayList();
    // Concatenate all committees for this slot in order
    UInt64.range(UInt64.ZERO, getCommitteeCountPerSlot(state, epoch))
        .forEach(
            i -> {
              final IntList committee = getBeaconCommittee(state, slot, i);
              indices.addAll(committee);
            });
    return MiscHelpersGloas.required(miscHelpers)
        .computeBalanceWeightedSelection(
            state, indices, seed, SpecConfigGloas.required(config).getPtcSize(), false);
  }

  /**
   * get_builder_payment_quorum_threshold
   *
   * <p>Calculate the quorum threshold for builder payments.
   */
  public UInt64 getBuilderPaymentQuorumThreshold(final BeaconState state) {
    final UInt64 quorum =
        getTotalActiveBalance(state)
            .dividedBy(config.getSlotsPerEpoch())
            .times(SpecConfigGloas.BUILDER_PAYMENT_THRESHOLD_NUMERATOR);
    return quorum.dividedBy(SpecConfigGloas.BUILDER_PAYMENT_THRESHOLD_DENOMINATOR);
  }
}
