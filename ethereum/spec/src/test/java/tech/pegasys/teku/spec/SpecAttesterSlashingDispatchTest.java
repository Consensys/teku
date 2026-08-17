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

package tech.pegasys.teku.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.AttesterSlashingValidator.AttesterSlashingInvalidReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * Operation validation must be dispatched on the fork of the state being validated against, not on
 * an epoch supplied by the message. A message-supplied epoch is attacker controlled and has no
 * lower bound, so dispatching on it lets a caller select an arbitrarily old milestone's validation
 * rules - diverging from what block processing will later apply.
 *
 * <p>Gloas is where this becomes observable for attester slashings: it moves {@code
 * attesting_indices} to an unbounded progressive SSZ list and therefore enforces the {@code
 * MAX_VALIDATORS_PER_ATTESTATION} bound at runtime instead of through the schema. Dispatching on
 * the attestation's epoch skips that runtime bound whenever the attestations pre-date Gloas, so the
 * pool would accept a slashing that Gloas block processing goes on to reject.
 */
class SpecAttesterSlashingDispatchTest {

  private static final int MAX_VALIDATORS_PER_COMMITTEE = 4;
  private static final int MAX_COMMITTEES_PER_SLOT = 2;
  // SpecConfigElectra derives MAX_VALIDATORS_PER_ATTESTATION as the product of the two, and Gloas
  // inherits that derivation.
  private static final int MAX_VALIDATORS_PER_ATTESTATION =
      MAX_VALIDATORS_PER_COMMITTEE * MAX_COMMITTEES_PER_SLOT;

  private static final UInt64 GLOAS_FORK_EPOCH = UInt64.valueOf(2);
  // The epoch the slashable attestations name: before Gloas, so it resolves to the Fulu milestone.
  private static final UInt64 ATTESTATION_EPOCH = UInt64.ONE;
  // The epoch the state is in: after Gloas.
  private static final UInt64 STATE_EPOCH = UInt64.valueOf(3);

  private final Spec spec =
      TestSpecFactory.createMinimalGloas(
          builder ->
              builder
                  .maxValidatorsPerCommittee(MAX_VALIDATORS_PER_COMMITTEE)
                  .maxCommitteesPerSlot(MAX_COMMITTEES_PER_SLOT)
                  .gloasForkEpoch(GLOAS_FORK_EPOCH));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldApplyGloasAttestingIndicesBoundToSlashingNamingAPreGloasEpoch() {
    final UInt64 stateSlot = spec.computeStartSlotAtEpoch(STATE_EPOCH);
    final UInt64 attestationSlot = spec.computeStartSlotAtEpoch(ATTESTATION_EPOCH);
    assertThat(spec.atSlot(stateSlot).getMilestone()).isEqualTo(SpecMilestone.GLOAS);
    assertThat(spec.atSlot(attestationSlot).getMilestone()).isEqualTo(SpecMilestone.FULU);

    // One more attester than Gloas permits in a single indexed attestation.
    final int attesterCount = MAX_VALIDATORS_PER_ATTESTATION + 1;
    final List<BLSKeyPair> attesterKeys =
        IntStream.range(0, attesterCount)
            .mapToObj(__ -> dataStructureUtil.randomKeyPair())
            .toList();
    final List<UInt64> attestingIndices =
        IntStream.range(0, attesterCount).mapToObj(UInt64::valueOf).toList();

    final BeaconState state = buildStateWithSlashableAttesters(stateSlot, attesterKeys);

    // A double vote: same target epoch, different beacon block roots, so the pair is slashable.
    final AttestationData data1 =
        attestationData(attestationSlot, dataStructureUtil.randomBytes32());
    final AttestationData data2 =
        attestationData(attestationSlot, dataStructureUtil.randomBytes32());

    final AttesterSlashing slashing =
        spec.atSlot(stateSlot)
            .getSchemaDefinitions()
            .getAttesterSlashingSchema()
            .create(
                indexedAttestation(stateSlot, attestingIndices, data1, state, attesterKeys),
                indexedAttestation(stateSlot, attestingIndices, data2, state, attesterKeys));

    // Dispatching on the attestations' epoch would select the Fulu milestone, whose attesting
    // indices bound lives in the (bypassed) schema rather than in the validation logic, and would
    // wrongly report this slashing as valid.
    assertThat(spec.validateAttesterSlashing(state, slashing))
        .contains(AttesterSlashingInvalidReason.ATTESTATION_1_INVALID);
  }

  private BeaconState buildStateWithSlashableAttesters(
      final UInt64 stateSlot, final List<BLSKeyPair> attesterKeys) {
    final Validator[] validators =
        attesterKeys.stream()
            .map(
                keyPair ->
                    dataStructureUtil
                        .validatorBuilder()
                        .publicKey(keyPair.getPublicKey())
                        .activationEligibilityEpoch(UInt64.ZERO)
                        .activationEpoch(UInt64.ZERO)
                        .exitEpoch(FAR_FUTURE_EPOCH)
                        .withdrawableEpoch(FAR_FUTURE_EPOCH)
                        .slashed(false)
                        .build())
            .toArray(Validator[]::new);

    return dataStructureUtil
        .stateBuilderGloas(validators.length, 1, 1)
        .slot(stateSlot)
        .fork(spec.getForkSchedule().getFork(STATE_EPOCH))
        .validators(validators)
        .build();
  }

  private AttestationData attestationData(final UInt64 slot, final Bytes32 beaconBlockRoot) {
    return new AttestationData(
        slot,
        UInt64.ZERO,
        beaconBlockRoot,
        new Checkpoint(UInt64.ZERO, dataStructureUtil.randomBytes32()),
        new Checkpoint(ATTESTATION_EPOCH, dataStructureUtil.randomBytes32()));
  }

  private IndexedAttestation indexedAttestation(
      final UInt64 stateSlot,
      final List<UInt64> attestingIndices,
      final AttestationData data,
      final BeaconState state,
      final List<BLSKeyPair> attesterKeys) {
    // Gloas schema, since a slashing arriving now is encoded under the current fork - this is what
    // lets the list exceed MAX_VALIDATORS_PER_ATTESTATION at all.
    final IndexedAttestationSchema schema =
        spec.atSlot(stateSlot).getSchemaDefinitions().getIndexedAttestationSchema();
    return schema.create(
        schema.getAttestingIndicesSchema().of(attestingIndices),
        data,
        aggregateAttestationSignature(data, state, attesterKeys));
  }

  private BLSSignature aggregateAttestationSignature(
      final AttestationData data, final BeaconState state, final List<BLSKeyPair> attesterKeys) {
    // The domain is selected from the fork passed to validation (the state's fork) and the target
    // epoch, so it is the same whichever milestone ends up being dispatched on.
    final Bytes32 domain =
        spec.atSlot(state.getSlot())
            .beaconStateAccessors()
            .getDomain(
                Domain.BEACON_ATTESTER,
                data.getTarget().getEpoch(),
                state.getFork(),
                state.getGenesisValidatorsRoot());
    final Bytes signingRoot =
        spec.atSlot(state.getSlot()).miscHelpers().computeSigningRoot(data, domain);
    return BLS.aggregate(
        attesterKeys.stream()
            .map(keyPair -> BLS.sign(keyPair.getSecretKey(), signingRoot))
            .toList());
  }
}
