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

package tech.pegasys.artemis.networking.eth2.gossip.topics.validation;

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.networking.eth2.gossip.topics.validation.ValidationResult.INVALID;
import static tech.pegasys.artemis.networking.eth2.gossip.topics.validation.ValidationResult.SAVED_FOR_FUTURE;
import static tech.pegasys.artemis.networking.eth2.gossip.topics.validation.ValidationResult.VALID;
import static tech.pegasys.artemis.util.config.Constants.ATTESTATION_PROPAGATION_SLOT_RANGE;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.BLSKeyGenerator;
import tech.pegasys.artemis.bls.BLSKeyPair;
import tech.pegasys.artemis.core.AttestationGenerator;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.CommitteeUtil;
import tech.pegasys.artemis.ssz.SSZTypes.Bitlist;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.artemis.storage.client.RecentChainData;

/**
 * The following validations MUST pass before forwarding the attestation on the subnet.
 *
 * <p>The attestation's committee index (attestation.data.index) is for the correct subnet.
 *
 * <p>attestation.data.slot is within the last ATTESTATION_PROPAGATION_SLOT_RANGE slots (within a
 * MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. attestation.data.slot +
 * ATTESTATION_PROPAGATION_SLOT_RANGE >= current_slot >= attestation.data.slot (a client MAY queue
 * future attestations for processing at the appropriate slot).
 *
 * <p>The attestation is unaggregated -- that is, it has exactly one participating validator
 * (len([bit for bit in attestation.aggregation_bits if bit == 0b1]) == 1).
 *
 * <p>The attestation is the first valid attestation received for the participating validator for
 * the slot, attestation.data.slot.
 *
 * <p>The block being voted for (attestation.data.beacon_block_root) passes validation.
 *
 * <p>The signature of attestation is valid.
 */
class AttestationValidatorTest {

  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(64);
  private final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(new EventBus());
  private final BeaconChainUtil beaconChainUtil =
      BeaconChainUtil.create(recentChainData, VALIDATOR_KEYS, false);
  private final AttestationGenerator attestationGenerator =
      new AttestationGenerator(beaconChainUtil.getValidatorKeys());

  private final AttestationValidator validator = new AttestationValidator(recentChainData);

  @BeforeAll
  public static void init() {
    BeaconStateUtil.BLS_VERIFY_DEPOSIT = false;
  }

  @AfterAll
  public static void reset() {
    BeaconStateUtil.BLS_VERIFY_DEPOSIT = true;
  }

  @BeforeEach
  public void setUp() {
    beaconChainUtil.initializeStorage();
  }

  @Test
  public void shouldReturnValidForValidAttestation() {
    final Attestation attestation =
        attestationGenerator.validAttestation(recentChainData.getBestBlockAndState().orElseThrow());
    assertThat(validate(attestation)).isEqualTo(VALID);
  }

  @Test
  public void shouldRejectAttestationWithIncorrectAggregateBitsSize() {
    final Attestation attestation =
        attestationGenerator.validAttestation(recentChainData.getBestBlockAndState().orElseThrow());
    final Bitlist validAggregationBits = attestation.getAggregation_bits();
    final Bitlist invalidAggregationBits =
        new Bitlist(validAggregationBits.getCurrentSize() + 1, validAggregationBits.getMaxSize());
    invalidAggregationBits.setAllBits(validAggregationBits);
    final Attestation invalidAttestation =
        new Attestation(
            invalidAggregationBits, attestation.getData(), attestation.getAggregate_signature());
    assertThat(validate(invalidAttestation)).isEqualTo(INVALID);
  }

  @Test
  public void shouldRejectAttestationFromBeforeAttestationPropagationSlotRange() {
    final Attestation attestation =
        attestationGenerator.validAttestation(recentChainData.getBestBlockAndState().orElseThrow());

    // In the first slot after
    final UnsignedLong slot = ATTESTATION_PROPAGATION_SLOT_RANGE.plus(ONE);
    beaconChainUtil.setSlot(slot);
    // Add one more second to get past the MAXIMUM_GOSSIP_CLOCK_DISPARITY
    beaconChainUtil.setTime(recentChainData.getStore().getTime().plus(ONE));

    assertThat(validate(attestation)).isEqualTo(INVALID);
  }

  @Test
  public void shouldAcceptAttestationWithinClockDisparityOfEarliestPropagationSlot() {
    final Attestation attestation =
        attestationGenerator.validAttestation(recentChainData.getBestBlockAndState().orElseThrow());

    // At the very start of the first slot the attestation isn't allowed, but still within
    // the MAXIMUM_GOSSIP_CLOCK_DISPARITY so should be allowed.
    final UnsignedLong slot = ATTESTATION_PROPAGATION_SLOT_RANGE.plus(ONE);
    beaconChainUtil.setSlot(slot);

    assertThat(validate(attestation)).isEqualTo(VALID);
  }

  @Test
  public void shouldDeferAttestationFromAfterThePropagationSlotRange() {
    final Attestation attestation =
        attestationGenerator.validAttestation(
            recentChainData.getBestBlockAndState().orElseThrow(), ONE);
    assertThat(attestation.getData().getSlot()).isEqualTo(ONE);

    beaconChainUtil.setSlot(ZERO);

    assertThat(validate(attestation)).isEqualTo(SAVED_FOR_FUTURE);
  }

  @Test
  public void shouldAcceptAttestationWithinClockDisparityOfLatestPropagationSlot() {
    final Attestation attestation =
        attestationGenerator.validAttestation(
            recentChainData.getBestBlockAndState().orElseThrow(), ONE);
    assertThat(attestation.getData().getSlot()).isEqualTo(ONE);

    // Ideally we'd rewind the time by a few milliseconds but our Store only keeps time to second
    // precision.  Alternatively we might consider using system time, not store time.
    beaconChainUtil.setSlot(ONE);

    assertThat(validate(attestation)).isEqualTo(VALID);
  }

  @Test
  public void shouldRejectAggregatedAttestation() {
    final Attestation attestation =
        AttestationGenerator.groupAndAggregateAttestations(
                attestationGenerator.getAttestationsForSlot(
                    recentChainData.getBestBlockAndState().orElseThrow()))
            .get(0);

    assertThat(validate(attestation)).isEqualTo(INVALID);
  }

  @Test
  public void shouldRejectAttestationForSameValidatorAndSlot() throws Exception {
    final BeaconBlockAndState genesis = recentChainData.getBestBlockAndState().orElseThrow();
    beaconChainUtil.createAndImportBlockAtSlot(ONE);

    // Slot 1 attestation for the block at slot 1
    final Attestation attestation1 =
        attestationGenerator.validAttestation(recentChainData.getBestBlockAndState().orElseThrow());
    // Slot 1 attestation from the same validator claiming no block at slot 1
    final Attestation attestation2 =
        attestationGenerator
            .streamAttestations(genesis, ONE)
            .filter(attestation -> hasSameValidators(attestation1, attestation))
            .findFirst()
            .orElseThrow();

    // Sanity check
    assertThat(attestation1.getData().getSlot()).isEqualTo(attestation2.getData().getSlot());
    assertThat(attestation1.getAggregation_bits()).isEqualTo(attestation2.getAggregation_bits());

    assertThat(validate(attestation1)).isEqualTo(VALID);
    assertThat(validate(attestation2)).isEqualTo(INVALID);
  }

  @Test
  public void shouldAcceptAttestationForSameValidatorButDifferentSlot() throws Exception {
    final BeaconBlockAndState genesis = recentChainData.getBestBlockAndState().orElseThrow();
    beaconChainUtil.createAndImportBlockAtSlot(ONE);

    // Slot 0 attestation
    final Attestation attestation1 = attestationGenerator.validAttestation(genesis);

    // Slot 1 attestation from the same validator
    final Attestation attestation2 =
        attestationGenerator
            .streamAttestations(genesis, ONE)
            .filter(attestation -> hasSameValidators(attestation1, attestation))
            .findFirst()
            .orElseThrow();

    // Sanity check
    assertThat(attestation1.getData().getSlot()).isNotEqualTo(attestation2.getData().getSlot());
    assertThat(attestation1.getAggregation_bits()).isEqualTo(attestation2.getAggregation_bits());

    assertThat(validate(attestation1)).isEqualTo(VALID);
    assertThat(validate(attestation2)).isEqualTo(VALID);
  }

  @Test
  public void shouldAcceptAttestationForSameSlotButDifferentValidator() {
    final BeaconBlockAndState genesis = recentChainData.getBestBlockAndState().orElseThrow();

    // Slot 0 attestation from one validator
    final Attestation attestation1 = attestationGenerator.validAttestation(genesis, ZERO);

    // Slot 0 attestation from a different validator
    final Attestation attestation2 =
        attestationGenerator
            .streamAttestations(genesis, ZERO)
            .filter(attestation -> !hasSameValidators(attestation1, attestation))
            .findFirst()
            .orElseThrow();

    // Sanity check
    assertThat(attestation1.getData().getSlot()).isEqualTo(attestation2.getData().getSlot());
    assertThat(attestation1.getAggregation_bits()).isNotEqualTo(attestation2.getAggregation_bits());

    assertThat(validate(attestation1)).isEqualTo(VALID);
    assertThat(validate(attestation2)).isEqualTo(VALID);
  }

  @Test
  public void shouldRejectAttestationWithInvalidSignature() {
    final Attestation attestation =
        attestationGenerator.attestationWithInvalidSignature(
            recentChainData.getBestBlockAndState().orElseThrow());

    assertThat(validate(attestation)).isEqualTo(INVALID);
  }

  @Test
  public void shouldDeferAttestationWhenBlockBeingVotedForIsNotAvailable() throws Exception {
    final BeaconBlockAndState unknownBlockAndState =
        beaconChainUtil.createBlockAndStateAtSlot(ONE, true).toUnsigned();
    beaconChainUtil.setSlot(ONE);
    final Attestation attestation = attestationGenerator.validAttestation(unknownBlockAndState);

    assertThat(validate(attestation)).isEqualTo(SAVED_FOR_FUTURE);
  }

  @Test
  public void shouldRejectAttestationsSentOnTheWrongSubnet() {
    final Attestation attestation =
        attestationGenerator.validAttestation(recentChainData.getBestBlockAndState().orElseThrow());
    final UnsignedLong expectedSubnetId = CommitteeUtil.getSubnetId(attestation);
    assertThat(validator.validate(attestation, expectedSubnetId.plus(ONE))).isEqualTo(INVALID);
    assertThat(validator.validate(attestation, expectedSubnetId)).isEqualTo(VALID);
  }

  private ValidationResult validate(final Attestation attestation) {
    return validator.validate(attestation, CommitteeUtil.getSubnetId(attestation));
  }

  private boolean hasSameValidators(final Attestation attestation1, final Attestation attestation) {
    return attestation.getAggregation_bits().equals(attestation1.getAggregation_bits());
  }
}
