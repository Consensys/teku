/*
 * Copyright ConsenSys Software Inc., 2022
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.Constants.ATTESTATION_PROPAGATION_SLOT_RANGE;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.REJECT;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.SAVE_FOR_FUTURE;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.storage.api.StateStorageMode;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

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
  private static final Logger LOG = LogManager.getLogger();

  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(64);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final AttestationSchema attestationSchema =
      spec.getGenesisSchemaDefinitions().getAttestationSchema();
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);
  private final ChainUpdater chainUpdater =
      new ChainUpdater(storageSystem.recentChainData(), chainBuilder);
  private final AttestationGenerator attestationGenerator =
      new AttestationGenerator(spec, chainBuilder.getValidatorKeys());
  private final AsyncBLSSignatureVerifier signatureVerifier =
      AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE);

  private final AttestationValidator validator =
      new AttestationValidator(spec, recentChainData, signatureVerifier);

  @BeforeAll
  public static void init() {
    AbstractBlockProcessor.blsVerifyDeposit = false;
  }

  @AfterAll
  public static void reset() {
    AbstractBlockProcessor.blsVerifyDeposit = true;
  }

  @BeforeEach
  public void setUp() {
    chainUpdater.initializeGenesis(false);
  }

  @Test
  public void shouldReturnValidForValidAttestation() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());
    assertThat(validate(attestation).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldReturnValidForValidAttestation_whenManyBlocksHaveBeenSkipped() {
    final StateAndBlockSummary head = storageSystem.getChainHead();
    final UInt64 currentSlot = head.getSlot().plus(spec.getSlotsPerEpoch(head.getSlot()) * 3L);
    storageSystem.chainUpdater().setCurrentSlot(currentSlot);

    final Attestation attestation =
        attestationGenerator.validAttestation(
            head, head.getSlot().plus(spec.getSlotsPerEpoch(head.getSlot()) * 3L));
    assertThat(validate(attestation).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectAttestationWithIncorrectAggregateBitsSize() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());
    final SszBitlist validAggregationBits = attestation.getAggregationBits();
    SszBitlist invalidAggregationBits =
        validAggregationBits
            .getSchema()
            .ofBits(validAggregationBits.size() + 1)
            .or(validAggregationBits);
    final Attestation invalidAttestation =
        attestationSchema.create(
            invalidAggregationBits, attestation.getData(), attestation.getAggregateSignature());
    assertThat(validate(invalidAttestation).code()).isEqualTo(REJECT);
  }

  @Test
  public void shouldRejectAttestationFromBeforeAttestationPropagationSlotRange() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    // In the first slot after
    final UInt64 slot = ATTESTATION_PROPAGATION_SLOT_RANGE.plus(ONE);
    chainUpdater.setCurrentSlot(slot);
    // Add one more second to get past the MAXIMUM_GOSSIP_CLOCK_DISPARITY
    chainUpdater.setTime(recentChainData.getStore().getTimeSeconds().plus(ONE));

    assertThat(validate(attestation).code()).isEqualTo(IGNORE);
  }

  @Test
  public void shouldAcceptAttestationWithinClockDisparityOfEarliestPropagationSlot() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead());

    // At the very start of the first slot the attestation isn't allowed, but still within
    // the MAXIMUM_GOSSIP_CLOCK_DISPARITY so should be allowed.
    final UInt64 slot = ATTESTATION_PROPAGATION_SLOT_RANGE.plus(ONE);
    chainUpdater.setCurrentSlot(slot);

    assertThat(validate(attestation).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldDeferAttestationFromAfterThePropagationSlotRange() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead(), ONE);
    assertThat(attestation.getData().getSlot()).isEqualTo(ONE);

    chainUpdater.setCurrentSlot(ZERO);

    assertThat(validate(attestation).code()).isEqualTo(SAVE_FOR_FUTURE);
  }

  @Test
  public void shouldAcceptAttestationWithinClockDisparityOfLatestPropagationSlot() {
    final Attestation attestation =
        attestationGenerator.validAttestation(storageSystem.getChainHead(), ONE);
    assertThat(attestation.getData().getSlot()).isEqualTo(ONE);

    // Ideally we'd rewind the time by a few milliseconds but our Store only keeps time to second
    // precision.  Alternatively we might consider using system time, not store time.
    chainUpdater.setCurrentSlot(ONE);

    assertThat(validate(attestation).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectAggregatedAttestation() {
    final Attestation attestation =
        AttestationGenerator.groupAndAggregateAttestations(
                attestationGenerator.getAttestationsForSlot(storageSystem.getChainHead()))
            .get(0);

    assertThat(validate(attestation).code()).isEqualTo(REJECT);
  }

  @Test
  public void shouldAcceptAttestationForSameValidatorButDifferentTargetEpoch() {
    final StateAndBlockSummary genesis = storageSystem.getChainHead();
    final SignedBeaconBlock nextEpochBlock =
        chainUpdater
            .advanceChain(UInt64.valueOf(spec.getSlotsPerEpoch(genesis.getSlot()) + 1))
            .getBlock();

    // Slot 0 attestation
    final Attestation attestation1 = attestationGenerator.validAttestation(genesis);

    // Slot 1 attestation from the same validator
    final Attestation attestation2 =
        attestationGenerator
            .streamAttestations(storageSystem.getChainHead(), nextEpochBlock.getSlot())
            .filter(attestation -> hasSameValidators(attestation1, attestation))
            .findFirst()
            .orElseThrow();

    // Sanity check
    assertThat(attestation1.getData().getTarget().getEpoch())
        .isNotEqualTo(attestation2.getData().getTarget().getEpoch());
    assertThat(attestation1.getAggregationBits()).isEqualTo(attestation2.getAggregationBits());

    assertThat(validate(attestation1).code()).isEqualTo(ACCEPT);
    assertThat(validate(attestation2).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldAcceptAttestationForSameSlotButDifferentValidator() {
    final StateAndBlockSummary genesis = storageSystem.getChainHead();

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
    assertThat(attestation1.getAggregationBits()).isNotEqualTo(attestation2.getAggregationBits());

    assertThat(validate(attestation1).code()).isEqualTo(ACCEPT);
    assertThat(validate(attestation2).code()).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectAttestationWithInvalidSignature() {
    final Attestation attestation =
        attestationGenerator.attestationWithInvalidSignature(storageSystem.getChainHead());

    assertThat(validate(attestation).code()).isEqualTo(REJECT);
  }

  @Test
  public void shouldDeferAttestationWhenBlockBeingVotedForIsNotAvailable() {
    final BeaconBlockAndState unknownBlockAndState =
        chainBuilder.generateBlockAtSlot(ONE).toUnsigned();
    chainUpdater.setCurrentSlot(ONE);
    final Attestation attestation = attestationGenerator.validAttestation(unknownBlockAndState);

    assertThat(validate(attestation).code()).isEqualTo(SAVE_FOR_FUTURE);
  }

  @Test
  public void shouldRejectAttestationsSentOnTheWrongSubnet() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final int expectedSubnetId =
        spec.computeSubnetForAttestation(blockAndState.getState(), attestation);
    assertThat(
            validator.validate(
                ValidateableAttestation.fromNetwork(spec, attestation, expectedSubnetId + 1)))
        .matches(rejected("Attestation received on incorrect subnet"), "Rejected incorrect subnet");

    assertThat(
            validator.validate(
                ValidateableAttestation.fromNetwork(spec, attestation, expectedSubnetId)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @Test
  public void shouldRejectAttestationsWithCommitteeIndexNotInTheExpectedRange() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final AttestationData data = attestation.getData();
    final int expectedSubnetId =
        spec.computeSubnetForAttestation(blockAndState.getState(), attestation);
    assertThat(
            validator.validate(
                ValidateableAttestation.fromNetwork(
                    spec,
                    attestationSchema.create(
                        attestation.getAggregationBits(),
                        new AttestationData(
                            data.getSlot(),
                            spec.getCommitteeCountPerSlot(
                                blockAndState.getState(), data.getTarget().getEpoch()),
                            data.getBeaconBlockRoot(),
                            data.getSource(),
                            data.getTarget()),
                        attestation.getAggregateSignature()),
                    expectedSubnetId)))
        .matches(rejected("Committee index 2 is out of range"), "Rejected committee out of range");
  }

  @Test
  public void shouldRejectAttestationsThatHaveNonMatchingTargetEpochAndSlot() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final AttestationData data = attestation.getData();
    final int expectedSubnetId =
        spec.computeSubnetForAttestation(blockAndState.getState(), attestation);
    assertThat(
            validator.validate(
                ValidateableAttestation.fromNetwork(
                    spec,
                    attestationSchema.create(
                        attestation.getAggregationBits(),
                        new AttestationData(
                            data.getSlot(),
                            data.getIndex(),
                            data.getBeaconBlockRoot(),
                            data.getSource(),
                            new Checkpoint(data.getTarget().getEpoch().plus(2), Bytes32.ZERO)),
                        attestation.getAggregateSignature()),
                    expectedSubnetId)))
        .matches(rejected("is not from target epoch"), "Rejected with is not from target epoch");
  }

  @Test
  public void shouldRejectAttestationsThatHaveLMDVotesInconsistentWithTargetRoot() {
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final AsyncBLSSignatureVerifier signatureVerifier = mock(AsyncBLSSignatureVerifier.class);
    final AttestationValidator validator =
        new AttestationValidator(spec, recentChainData, signatureVerifier);
    final AttestationData data = attestation.getData();
    final Checkpoint checkpoint =
        new Checkpoint(
            data.getTarget().getEpoch().minusMinZero(1),
            blockAndState.getBeaconBlock().orElseThrow().getParentRoot());
    final int expectedSubnetId =
        spec.computeSubnetForAttestation(blockAndState.getState(), attestation);

    // the signature would be invalid, but it's actually not the point of the test case.
    when(signatureVerifier.verify(anyList(), any(Bytes.class), any()))
        .thenReturn(SafeFuture.completedFuture(true));
    assertThat(
            validator.validate(
                ValidateableAttestation.fromNetwork(
                    spec,
                    attestationSchema.create(
                        attestation.getAggregationBits(),
                        new AttestationData(
                            data.getSlot(),
                            data.getIndex(),
                            data.getBeaconBlockRoot(),
                            data.getSource(),
                            checkpoint),
                        attestation.getAggregateSignature()),
                    expectedSubnetId)))
        .matches(
            rejected("descend from target block"), "Rejected does not descend from target block");
  }

  private Predicate<? super CompletableFuture<InternalValidationResult>> rejected(
      final String messageContents) {
    return result -> {
      try {
        InternalValidationResult internalValidationResult = result.get();
        return internalValidationResult.isReject()
            && internalValidationResult.getDescription().orElseThrow().contains(messageContents);
      } catch (Exception e) {
        LOG.error("failed to evaluate rejected predicate", e);
        return false;
      }
    };
  }

  private InternalValidationResult validate(final Attestation attestation) {
    final BeaconState state = safeJoin(recentChainData.getBestState().orElseThrow());

    return validator
        .validate(
            ValidateableAttestation.fromNetwork(
                spec, attestation, spec.computeSubnetForAttestation(state, attestation)))
        .join();
  }

  private boolean hasSameValidators(final Attestation attestation1, final Attestation attestation) {
    return attestation.getAggregationBits().equals(attestation1.getAggregationBits());
  }
}
