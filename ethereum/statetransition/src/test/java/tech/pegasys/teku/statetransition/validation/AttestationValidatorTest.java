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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.BLS_VERIFY_DEPOSIT;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_committee_count_per_slot;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.computeSubnetForAttestation;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.REJECT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_PROPAGATION_SLOT_RANGE;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.AttestationGenerator;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.ForkChoiceUtilWrapper;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.util.config.StateStorageMode;

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
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);
  private final ChainUpdater chainUpdater =
      new ChainUpdater(storageSystem.recentChainData(), chainBuilder);
  private final AttestationGenerator attestationGenerator =
      new AttestationGenerator(chainBuilder.getValidatorKeys());

  private final AttestationValidator validator =
      new AttestationValidator(recentChainData, new ForkChoiceUtilWrapper());

  @BeforeAll
  public static void init() {
    BLS_VERIFY_DEPOSIT = false;
  }

  @AfterAll
  public static void reset() {
    BLS_VERIFY_DEPOSIT = true;
  }

  @BeforeEach
  public void setUp() {
    chainUpdater.initializeGenesis(false);
  }

  @Test
  public void shouldReturnValidForValidAttestation() {
    final Attestation attestation =
        attestationGenerator.validAttestation(recentChainData.getChainHead().orElseThrow());
    assertThat(validate(attestation)).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldReturnValidForValidAttestation_whenManyBlocksHaveBeenSkipped() {
    final StateAndBlockSummary head = recentChainData.getChainHead().orElseThrow();
    final UInt64 currentSlot = head.getSlot().plus(SLOTS_PER_EPOCH * 3);
    storageSystem.chainUpdater().setCurrentSlot(currentSlot);

    final Attestation attestation =
        attestationGenerator.validAttestation(head, head.getSlot().plus(SLOTS_PER_EPOCH * 3));
    assertThat(validate(attestation)).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectAttestationWithIncorrectAggregateBitsSize() {
    final Attestation attestation =
        attestationGenerator.validAttestation(recentChainData.getChainHead().orElseThrow());
    final Bitlist validAggregationBits = attestation.getAggregation_bits();
    final Bitlist invalidAggregationBits =
        new Bitlist(validAggregationBits.getCurrentSize() + 1, validAggregationBits.getMaxSize());
    invalidAggregationBits.setAllBits(validAggregationBits);
    final Attestation invalidAttestation =
        new Attestation(
            invalidAggregationBits, attestation.getData(), attestation.getAggregate_signature());
    assertThat(validate(invalidAttestation)).isEqualTo(REJECT);
  }

  @Test
  public void shouldRejectAttestationFromBeforeAttestationPropagationSlotRange() {
    final Attestation attestation =
        attestationGenerator.validAttestation(recentChainData.getChainHead().orElseThrow());

    // In the first slot after
    final UInt64 slot = ATTESTATION_PROPAGATION_SLOT_RANGE.plus(ONE);
    chainUpdater.setCurrentSlot(slot);
    // Add one more second to get past the MAXIMUM_GOSSIP_CLOCK_DISPARITY
    chainUpdater.setTime(recentChainData.getStore().getTime().plus(ONE));

    assertThat(validate(attestation)).isEqualTo(IGNORE);
  }

  @Test
  public void shouldAcceptAttestationWithinClockDisparityOfEarliestPropagationSlot() {
    final Attestation attestation =
        attestationGenerator.validAttestation(recentChainData.getChainHead().orElseThrow());

    // At the very start of the first slot the attestation isn't allowed, but still within
    // the MAXIMUM_GOSSIP_CLOCK_DISPARITY so should be allowed.
    final UInt64 slot = ATTESTATION_PROPAGATION_SLOT_RANGE.plus(ONE);
    chainUpdater.setCurrentSlot(slot);

    assertThat(validate(attestation)).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldDeferAttestationFromAfterThePropagationSlotRange() {
    final Attestation attestation =
        attestationGenerator.validAttestation(recentChainData.getChainHead().orElseThrow(), ONE);
    assertThat(attestation.getData().getSlot()).isEqualTo(ONE);

    chainUpdater.setCurrentSlot(ZERO);

    assertThat(validate(attestation)).isEqualTo(SAVE_FOR_FUTURE);
  }

  @Test
  public void shouldAcceptAttestationWithinClockDisparityOfLatestPropagationSlot() {
    final Attestation attestation =
        attestationGenerator.validAttestation(recentChainData.getChainHead().orElseThrow(), ONE);
    assertThat(attestation.getData().getSlot()).isEqualTo(ONE);

    // Ideally we'd rewind the time by a few milliseconds but our Store only keeps time to second
    // precision.  Alternatively we might consider using system time, not store time.
    chainUpdater.setCurrentSlot(ONE);

    assertThat(validate(attestation)).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectAggregatedAttestation() {
    final Attestation attestation =
        AttestationGenerator.groupAndAggregateAttestations(
                attestationGenerator.getAttestationsForSlot(
                    recentChainData.getChainHead().orElseThrow()))
            .get(0);

    assertThat(validate(attestation)).isEqualTo(REJECT);
  }

  @Test
  public void shouldRejectAttestationForSameValidatorAndTargetEpoch() throws Exception {
    final StateAndBlockSummary genesis = recentChainData.getChainHead().orElseThrow();
    chainUpdater.advanceChain(ONE);

    // Slot 1 attestation for the block at slot 1
    final Attestation attestation1 =
        attestationGenerator.validAttestation(recentChainData.getChainHead().orElseThrow());
    // Slot 1 attestation from the same validator claiming no block at slot 1
    final Attestation attestation2 =
        attestationGenerator
            .streamAttestations(genesis, ONE)
            .filter(attestation -> hasSameValidators(attestation1, attestation))
            .findFirst()
            .orElseThrow();

    // Sanity check
    assertThat(attestation1.getData().getTarget().getEpoch())
        .isEqualTo(attestation2.getData().getTarget().getEpoch());
    assertThat(attestation1.getAggregation_bits()).isEqualTo(attestation2.getAggregation_bits());

    assertThat(validate(attestation1)).isEqualTo(ACCEPT);
    assertThat(validate(attestation2)).isEqualTo(IGNORE);
  }

  @Test
  public void shouldAcceptAttestationForSameValidatorButDifferentTargetEpoch() throws Exception {
    final StateAndBlockSummary genesis = recentChainData.getChainHead().orElseThrow();
    final SignedBeaconBlock nextEpochBlock =
        chainUpdater.advanceChain(UInt64.valueOf(SLOTS_PER_EPOCH + 1)).getBlock();

    // Slot 0 attestation
    final Attestation attestation1 = attestationGenerator.validAttestation(genesis);

    // Slot 1 attestation from the same validator
    final Attestation attestation2 =
        attestationGenerator
            .streamAttestations(
                recentChainData.getChainHead().orElseThrow(), nextEpochBlock.getSlot())
            .filter(attestation -> hasSameValidators(attestation1, attestation))
            .findFirst()
            .orElseThrow();

    // Sanity check
    assertThat(attestation1.getData().getTarget().getEpoch())
        .isNotEqualTo(attestation2.getData().getTarget().getEpoch());
    assertThat(attestation1.getAggregation_bits()).isEqualTo(attestation2.getAggregation_bits());

    assertThat(validate(attestation1)).isEqualTo(ACCEPT);
    assertThat(validate(attestation2)).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldAcceptAttestationForSameSlotButDifferentValidator() {
    final StateAndBlockSummary genesis = recentChainData.getChainHead().orElseThrow();

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

    assertThat(validate(attestation1)).isEqualTo(ACCEPT);
    assertThat(validate(attestation2)).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectAttestationWithInvalidSignature() {
    final Attestation attestation =
        attestationGenerator.attestationWithInvalidSignature(
            recentChainData.getChainHead().orElseThrow());

    assertThat(validate(attestation)).isEqualTo(REJECT);
  }

  @Test
  public void shouldDeferAttestationWhenBlockBeingVotedForIsNotAvailable() throws Exception {
    final BeaconBlockAndState unknownBlockAndState =
        chainBuilder.generateBlockAtSlot(ONE).toUnsigned();
    chainUpdater.setCurrentSlot(ONE);
    final Attestation attestation = attestationGenerator.validAttestation(unknownBlockAndState);

    assertThat(validate(attestation)).isEqualTo(SAVE_FOR_FUTURE);
  }

  @Test
  public void shouldRejectAttestationsSentOnTheWrongSubnet() {
    final StateAndBlockSummary blockAndState = recentChainData.getChainHead().orElseThrow();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final int expectedSubnetId = computeSubnetForAttestation(blockAndState.getState(), attestation);
    assertThat(
            validator.validate(
                ValidateableAttestation.fromNetwork(attestation, expectedSubnetId + 1)))
        .isCompletedWithValue(REJECT);
    assertThat(
            validator.validate(ValidateableAttestation.fromNetwork(attestation, expectedSubnetId)))
        .isCompletedWithValue(ACCEPT);
  }

  @Test
  public void shouldRejectAttestationsWithCommitteeIndexNotInTheExpectedRange() {
    final StateAndBlockSummary blockAndState = recentChainData.getChainHead().orElseThrow();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final AttestationData data = attestation.getData();
    final int expectedSubnetId = computeSubnetForAttestation(blockAndState.getState(), attestation);
    assertThat(
            validator.validate(
                ValidateableAttestation.fromNetwork(
                    new Attestation(
                        attestation.getAggregation_bits(),
                        new AttestationData(
                            data.getSlot(),
                            get_committee_count_per_slot(
                                blockAndState.getState(), data.getTarget().getEpoch()),
                            data.getBeacon_block_root(),
                            data.getSource(),
                            data.getTarget()),
                        attestation.getAggregate_signature()),
                    expectedSubnetId)))
        .isCompletedWithValue(REJECT);
  }

  @Test
  public void shouldRejectAttestationsThatHaveNonMatchingTargetEpochAndSlot() {
    final StateAndBlockSummary blockAndState = recentChainData.getChainHead().orElseThrow();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final AttestationData data = attestation.getData();
    final int expectedSubnetId = computeSubnetForAttestation(blockAndState.getState(), attestation);
    assertThat(
            validator.validate(
                ValidateableAttestation.fromNetwork(
                    new Attestation(
                        attestation.getAggregation_bits(),
                        new AttestationData(
                            data.getSlot(),
                            data.getIndex(),
                            data.getBeacon_block_root(),
                            data.getSource(),
                            new Checkpoint(data.getTarget().getEpoch().plus(2), Bytes32.ZERO)),
                        attestation.getAggregate_signature()),
                    expectedSubnetId)))
        .isCompletedWithValue(REJECT);
  }

  @Test
  public void shouldRejectAttestationsThatHaveLMDVotesInconsistentWithTargetRoot() {
    ForkChoiceUtilWrapper forkChoiceUtilWrapper = mock(ForkChoiceUtilWrapper.class);
    when(forkChoiceUtilWrapper.get_ancestor(any(), any(), any()))
        .thenReturn(Optional.of(Bytes32.ZERO));
    final AttestationValidator validator =
        new AttestationValidator(recentChainData, forkChoiceUtilWrapper);
    final StateAndBlockSummary blockAndState = recentChainData.getChainHead().orElseThrow();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final int expectedSubnetId = computeSubnetForAttestation(blockAndState.getState(), attestation);
    assertThat(
            validator.validate(ValidateableAttestation.fromNetwork(attestation, expectedSubnetId)))
        .isCompletedWithValue(REJECT);
  }

  @Test
  public void shouldRejectAttestationsThatHaveLMDVotesInconsistentWithFinalizedCheckpointRoot() {
    ForkChoiceUtilWrapper forkChoiceUtilWrapper = mock(ForkChoiceUtilWrapper.class);
    final AttestationValidator validator =
        new AttestationValidator(recentChainData, forkChoiceUtilWrapper);
    final StateAndBlockSummary blockAndState = recentChainData.getChainHead().orElseThrow();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    when(forkChoiceUtilWrapper.get_ancestor(any(), any(), any()))
        .thenReturn(Optional.of(attestation.getData().getTarget().getRoot()))
        .thenReturn(Optional.of(Bytes32.ZERO));
    final int expectedSubnetId = computeSubnetForAttestation(blockAndState.getState(), attestation);
    assertThat(
            validator.validate(ValidateableAttestation.fromNetwork(attestation, expectedSubnetId)))
        .isCompletedWithValue(REJECT);
  }

  private InternalValidationResult validate(final Attestation attestation) {
    final BeaconState state = recentChainData.getBestState().orElseThrow();
    return validator
        .validate(
            ValidateableAttestation.fromNetwork(
                attestation, computeSubnetForAttestation(state, attestation)))
        .join();
  }

  private boolean hasSameValidators(final Attestation attestation1, final Attestation attestation) {
    return attestation.getAggregation_bits().equals(attestation1.getAggregation_bits());
  }
}
