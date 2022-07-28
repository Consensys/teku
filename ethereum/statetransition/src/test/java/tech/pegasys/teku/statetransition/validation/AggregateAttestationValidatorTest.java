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
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.AggregateGenerator;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.StateStorageMode;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

/**
 * The following validations MUST pass before forwarding the signed_aggregate_and_proof on the
 * network. (We define the following for convenience -- aggregate_and_proof =
 * signed_aggregate_and_proof.message and aggregate = aggregate_and_proof.aggregate)
 *
 * <p>aggregate.data.slot is within the last ATTESTATION_PROPAGATION_SLOT_RANGE slots (with a
 * MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. aggregate.data.slot +
 * ATTESTATION_PROPAGATION_SLOT_RANGE >= current_slot >= aggregate.data.slot (a client MAY queue
 * future aggregates for processing at the appropriate slot).
 *
 * <p>The aggregate attestation defined by hash_tree_root(aggregate) has not already been seen (via
 * aggregate gossip, within a block, or through the creation of an equivalent aggregate locally).
 *
 * <p>The aggregate is the first valid aggregate received for the aggregator with index
 * aggregate_and_proof.aggregator_index for the slot aggregate.data.slot.
 *
 * <p>The block being voted for (aggregate.data.beacon_block_root) passes validation.
 *
 * <p>aggregate_and_proof.selection_proof selects the validator as an aggregator for the slot --
 * i.e. is_aggregator(state, aggregate.data.slot, aggregate.data.index,
 * aggregate_and_proof.selection_proof) returns True.
 *
 * <p>The aggregator's validator index is within the aggregate's committee -- i.e.
 * aggregate_and_proof.aggregator_index in get_attesting_indices(state, aggregate.data,
 * aggregate.aggregation_bits).
 *
 * <p>The aggregate_and_proof.selection_proof is a valid signature of the aggregate.data.slot by the
 * validator with index aggregate_and_proof.aggregator_index.
 *
 * <p>The aggregator signature, signed_aggregate_and_proof.signature, is valid.
 *
 * <p>The signature of aggregate is valid.
 */
class AggregateAttestationValidatorTest {

  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 1024);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final SpecVersion genesisSpec = spec.getGenesisSpec();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SignedAggregateAndProofSchema signedAggregateAndProofSchema =
      spec.getGenesisSchemaDefinitions().getSignedAggregateAndProofSchema();
  private final AggregateAndProofSchema aggregateAndProofSchema =
      spec.getGenesisSchemaDefinitions().getAggregateAndProofSchema();
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);
  private final ChainUpdater chainUpdater =
      new ChainUpdater(storageSystem.recentChainData(), chainBuilder);

  private final AggregateGenerator generator =
      new AggregateGenerator(spec, chainBuilder.getValidatorKeys());
  private final AttestationValidator attestationValidator = mock(AttestationValidator.class);

  private final AsyncBLSSignatureVerifier signatureVerifier =
      AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE);
  private AggregateAttestationValidator validator =
      new AggregateAttestationValidator(spec, attestationValidator, signatureVerifier);
  private SignedBlockAndState bestBlock;
  private SignedBlockAndState genesis;

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
    genesis = chainUpdater.initializeGenesis(false);
    bestBlock = chainUpdater.addNewBestBlock();
  }

  private void disableSignatureVerification() {
    validator =
        new AggregateAttestationValidator(
            spec, attestationValidator, AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.NO_OP));
  }

  @Test
  public void shouldReturnValidForValidAggregate() {
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final SignedAggregateAndProof aggregate = generator.validAggregateAndProof(chainHead);
    whenAttestationIsValid(aggregate);
    assertThat(validator.validate(ValidateableAttestation.aggregateFromValidator(spec, aggregate)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @Test
  public void shouldRejectWhenAttestationValidatorRejects() {
    final SignedAggregateAndProof aggregate =
        generator.validAggregateAndProof(storageSystem.getChainHead());
    ValidateableAttestation attestation =
        ValidateableAttestation.aggregateFromValidator(spec, aggregate);
    when(attestationValidator.singleOrAggregateAttestationChecks(
            any(), eq(attestation), eq(OptionalInt.empty())))
        .thenReturn(
            SafeFuture.completedFuture(InternalValidationResultWithState.reject("Nah mate")));

    assertThat(validator.validate(attestation)).isCompletedWithValue(reject("Nah mate"));
  }

  @Test
  public void shouldIgnoreWhenAttestationValidatorIgnores() {
    final SignedAggregateAndProof aggregate =
        generator.validAggregateAndProof(storageSystem.getChainHead());
    ValidateableAttestation attestation =
        ValidateableAttestation.aggregateFromValidator(spec, aggregate);
    when(attestationValidator.singleOrAggregateAttestationChecks(
            any(), eq(attestation), eq(OptionalInt.empty())))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResultWithState.ignore()));

    assertThat(validator.validate(attestation))
        .isCompletedWithValue(InternalValidationResult.IGNORE);
  }

  @Test
  public void shouldSaveForFutureWhenAttestationValidatorSavesForFuture() {
    final SignedAggregateAndProof aggregate =
        generator.validAggregateAndProof(storageSystem.getChainHead());
    ValidateableAttestation attestation =
        ValidateableAttestation.aggregateFromValidator(spec, aggregate);
    when(attestationValidator.singleOrAggregateAttestationChecks(
            any(), eq(attestation), eq(OptionalInt.empty())))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResultWithState.saveForFuture()));

    assertThat(validator.validate(attestation))
        .isCompletedWithValue(InternalValidationResult.SAVE_FOR_FUTURE);
  }

  @Test
  public void shouldSaveForFutureWhenStateIsNotAvailable() {
    final SignedBlockAndState target = bestBlock;
    final SignedAggregateAndProof aggregate = generator.validAggregateAndProof(target.toUnsigned());
    ValidateableAttestation attestation =
        ValidateableAttestation.aggregateFromValidator(spec, aggregate);
    when(attestationValidator.singleOrAggregateAttestationChecks(
            any(), eq(attestation), eq(OptionalInt.empty())))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResultWithState.saveForFuture()));

    assertThat(validator.validate(attestation))
        .isCompletedWithValue(InternalValidationResult.SAVE_FOR_FUTURE);
  }

  @Test
  public void shouldOnlyAcceptFirstAggregateWithSameSlotAndAggregatorIndex() {
    final BeaconBlockAndState chainHead = bestBlock.toUnsigned();
    final SignedAggregateAndProof aggregateAndProof1 = generator.validAggregateAndProof(chainHead);

    final List<Attestation> aggregatesForSlot =
        AttestationGenerator.groupAndAggregateAttestations(
            generator
                .getAttestationGenerator()
                .getAttestationsForSlot(chainHead, chainHead.getSlot()));
    final Attestation aggregate2 =
        aggregatesForSlot.stream()
            .filter(attestation -> hasSameCommitteeIndex(aggregateAndProof1, attestation))
            .findFirst()
            .orElseThrow();
    final SignedAggregateAndProof aggregateAndProof2 =
        generator.generator().blockAndState(chainHead).aggregate(aggregate2).generate();
    whenAttestationIsValid(aggregateAndProof1);
    whenAttestationIsValid(aggregateAndProof2);

    // Sanity check
    assertThat(aggregateAndProof1.getMessage().getAggregate()).isNotEqualTo(aggregate2);
    assertThat(aggregateAndProof1).isNotEqualTo(aggregateAndProof2);

    assertThat(
            validator.validate(
                ValidateableAttestation.aggregateFromValidator(spec, aggregateAndProof1)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
    assertThat(
            validator.validate(
                ValidateableAttestation.aggregateFromValidator(spec, aggregateAndProof2)))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @Test
  public void shouldAcceptAggregateWithSameHashTreeRoot() {
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final SignedAggregateAndProof aggregateAndProof1 = generator.validAggregateAndProof(chainHead);

    final Attestation attestation = aggregateAndProof1.getMessage().getAggregate();

    final SignedAggregateAndProof aggregateAndProof2 =
        signedAggregateAndProofSchema.create(
            aggregateAndProofSchema.create(UInt64.valueOf(2), attestation, BLSSignature.empty()),
            BLSSignature.empty());

    whenAttestationIsValid(aggregateAndProof1);
    whenAttestationIsValid(aggregateAndProof2);

    // Sanity check
    assertThat(aggregateAndProof1.getMessage().getAggregate()).isNotEqualTo(aggregateAndProof2);
    assertThat(aggregateAndProof1).isNotEqualTo(aggregateAndProof2);

    ValidateableAttestation attestation1 =
        ValidateableAttestation.aggregateFromValidator(spec, aggregateAndProof1);
    ValidateableAttestation attestation2 =
        ValidateableAttestation.aggregateFromValidator(spec, aggregateAndProof2);

    // Sanity check
    assertThat(attestation1.hashTreeRoot()).isEqualTo(attestation2.hashTreeRoot());

    assertThat(validator.validate(attestation1))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
    assertThat(validator.validate(attestation2))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @Test
  public void shouldOnlyAcceptFirstAggregateWithSameHashTreeRootWhenPassedSeenAggregates() {
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final SignedAggregateAndProof aggregateAndProof1 = generator.validAggregateAndProof(chainHead);
    final Attestation attestation = aggregateAndProof1.getMessage().getAggregate();
    final SignedAggregateAndProof aggregateAndProof2 =
        signedAggregateAndProofSchema.create(
            aggregateAndProofSchema.create(UInt64.valueOf(2), attestation, BLSSignature.empty()),
            BLSSignature.empty());

    whenAttestationIsValid(aggregateAndProof1);
    whenAttestationIsValid(aggregateAndProof2);

    // Sanity check
    assertThat(aggregateAndProof1.getMessage().getAggregate()).isNotEqualTo(aggregateAndProof2);
    assertThat(aggregateAndProof1).isNotEqualTo(aggregateAndProof2);

    ValidateableAttestation attestation1 =
        ValidateableAttestation.aggregateFromValidator(spec, aggregateAndProof1);
    ValidateableAttestation attestation2 =
        ValidateableAttestation.aggregateFromValidator(spec, aggregateAndProof2);

    // Sanity check
    assertThat(attestation1.hashTreeRoot()).isEqualTo(attestation2.hashTreeRoot());

    validator.addSeenAggregate(attestation1);
    assertThat(validator.validate(attestation2))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @Test
  void shouldIgnoreAggregateWhenAlreadySeenAllAttestingValidators() {
    disableSignatureVerification();
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final SignedAggregateAndProof validAggregate = generator.validAggregateAndProof(chainHead);
    final AttestationData attestationData = validAggregate.getMessage().getAggregate().getData();
    final ValidateableAttestation smallAttestation =
        createValidAggregate(ONE, attestationData, true, false, false);
    final ValidateableAttestation largeAttestation =
        createValidAggregate(
            validAggregate.getMessage().getIndex(), attestationData, true, true, true);

    validator.addSeenAggregate(largeAttestation);
    assertThat(validator.validate(smallAttestation))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @Test
  void shouldAcceptAggregateWhenNotAllAttestingValidatorsSeen() {
    disableSignatureVerification();
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final AggregateAndProof validAggregate =
        generator.validAggregateAndProof(chainHead).getMessage();
    final AttestationData attestationData = validAggregate.getAggregate().getData();
    final ValidateableAttestation smallAttestation =
        createValidAggregate(ONE, attestationData, true, false, false);
    final ValidateableAttestation largeAttestation =
        createValidAggregate(validAggregate.getIndex(), attestationData, true, true, true);

    validator.addSeenAggregate(smallAttestation);
    assertThat(validator.validate(largeAttestation))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  @Test
  void shouldAcceptAggregateWhenNoSupersetOfValidatorsSeen() {
    disableSignatureVerification();
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final AggregateAndProof validAggregate =
        generator.validAggregateAndProof(chainHead).getMessage();
    final AttestationData attestationData = validAggregate.getAggregate().getData();
    final ValidateableAttestation smallAttestation1 =
        createValidAggregate(ONE, attestationData, true, false, true);
    final ValidateableAttestation smallAttestation2 =
        createValidAggregate(UInt64.valueOf(2), attestationData, false, true, true);
    final ValidateableAttestation attestation =
        createValidAggregate(validAggregate.getIndex(), attestationData, true, true, false);

    validator.addSeenAggregate(smallAttestation1);
    validator.addSeenAggregate(smallAttestation2);

    // Accept because neither of the small attestations includes both the validators this one does
    assertThat(validator.validate(attestation))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  private ValidateableAttestation createValidAggregate(
      final UInt64 validatorIndex,
      final AttestationData attestationData,
      final Boolean... aggregationBits) {
    final SszBitlist aggregateBits =
        aggregateAndProofSchema
            .getAttestationSchema()
            .getAggregationBitsSchema()
            .of(aggregationBits);
    final Attestation attestation =
        aggregateAndProofSchema
            .getAttestationSchema()
            .create(aggregateBits, attestationData, BLSSignature.empty());
    final SignedAggregateAndProof signedAggregate =
        signedAggregateAndProofSchema.create(
            aggregateAndProofSchema.create(validatorIndex, attestation, BLSSignature.empty()),
            BLSSignature.empty());
    whenAttestationIsValid(signedAggregate);
    return ValidateableAttestation.aggregateFromValidator(spec, signedAggregate);
  }

  @Test
  public void shouldAcceptAggregateWithSameSlotAndDifferentAggregatorIndex() {
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final SignedAggregateAndProof aggregateAndProof1 = generator.validAggregateAndProof(chainHead);

    final List<Attestation> aggregatesForSlot =
        AttestationGenerator.groupAndAggregateAttestations(
            generator
                .getAttestationGenerator()
                .getAttestationsForSlot(chainHead, chainHead.getSlot()));
    final Attestation aggregate2 =
        aggregatesForSlot.stream()
            .filter(attestation -> !hasSameCommitteeIndex(aggregateAndProof1, attestation))
            .findFirst()
            .orElseThrow();
    final SignedAggregateAndProof aggregateAndProof2 =
        generator.generator().blockAndState(chainHead).aggregate(aggregate2).generate();
    whenAttestationIsValid(aggregateAndProof1);
    whenAttestationIsValid(aggregateAndProof2);

    assertThat(
            validator.validate(
                ValidateableAttestation.aggregateFromValidator(spec, aggregateAndProof1)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
    assertThat(
            validator.validate(
                ValidateableAttestation.aggregateFromValidator(spec, aggregateAndProof2)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @Test
  public void shouldAcceptAggregateWithSameAggregatorIndexAndDifferentSlot() {
    chainUpdater.setCurrentSlot(ONE);
    final BeaconBlockAndState chainHead = bestBlock.toUnsigned();

    // We need a validator that is an aggregator for both epoch 0 and 1. 238 happens to be one.
    final UInt64 aggregatorIndex = UInt64.valueOf(238);
    final SignedAggregateAndProof aggregateAndProof1 =
        generator
            .generator()
            .blockAndState(genesis.toUnsigned())
            .aggregatorIndex(aggregatorIndex)
            .generate();

    final CommitteeAssignment epochOneCommitteeAssignment =
        getCommitteeAssignment(chainHead, aggregatorIndex.intValue(), ONE);
    final SignedAggregateAndProof aggregateAndProof2 =
        generator
            .generator()
            .blockAndState(chainHead, epochOneCommitteeAssignment.getSlot())
            .committeeIndex(epochOneCommitteeAssignment.getCommitteeIndex())
            .aggregatorIndex(aggregatorIndex)
            .generate();
    whenAttestationIsValid(aggregateAndProof1);
    whenAttestationIsValid(aggregateAndProof2);

    // Sanity check
    assertThat(aggregateAndProof1.getMessage().getAggregate().getData().getSlot())
        .isNotEqualTo(aggregateAndProof2.getMessage().getAggregate().getData().getSlot());
    assertThat(aggregateAndProof1.getMessage().getIndex())
        .isEqualTo(aggregateAndProof2.getMessage().getIndex());

    assertThat(
            validator.validate(
                ValidateableAttestation.aggregateFromValidator(spec, aggregateAndProof1)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
    assertThat(
            validator.validate(
                ValidateableAttestation.aggregateFromValidator(spec, aggregateAndProof2)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @Test
  public void shouldRejectAggregateWhenSelectionProofDoesNotSelectAsAggregator() {
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    int aggregatorIndex = 3;
    final CommitteeAssignment committeeAssignment =
        getCommitteeAssignment(chainHead, aggregatorIndex, ZERO);
    final SignedAggregateAndProof aggregate =
        generator
            .generator()
            .blockAndState(chainHead, committeeAssignment.getSlot())
            .aggregatorIndex(UInt64.valueOf(aggregatorIndex))
            .committeeIndex(committeeAssignment.getCommitteeIndex())
            .generate();
    whenAttestationIsValid(aggregate);
    // Sanity check
    final int committeeLength = committeeAssignment.getCommittee().size();
    final int aggregatorModulo =
        genesisSpec.getValidatorsUtil().getAggregatorModulo(committeeLength);
    assertThat(aggregatorModulo).isGreaterThan(1);
    assertThat(
            genesisSpec
                .getValidatorsUtil()
                .isAggregator(aggregate.getMessage().getSelectionProof(), aggregatorModulo))
        .isFalse();

    assertThat(validator.validate(ValidateableAttestation.aggregateFromValidator(spec, aggregate)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  public void shouldRejectIfAggregatorIndexIsNotWithinTheCommittee() {
    final StateAndBlockSummary chainHead = storageSystem.getChainHead();
    final int aggregatorIndex = 60;
    final SignedAggregateAndProof aggregate =
        generator
            .generator()
            .blockAndState(chainHead)
            .aggregatorIndex(UInt64.valueOf(aggregatorIndex))
            .generate();
    whenAttestationIsValid(aggregate);
    // Sanity check aggregator is not in the committee
    final AttestationData attestationData = aggregate.getMessage().getAggregate().getData();
    final CommitteeAssignment committeeAssignment =
        getCommitteeAssignment(
            chainHead, aggregatorIndex, spec.computeEpochAtSlot(chainHead.getSlot()));
    if (committeeAssignment.getCommitteeIndex().equals(attestationData.getIndex())
        && committeeAssignment.getSlot().equals(attestationData.getSlot())) {
      fail("Aggregator was in the committee");
    }

    assertThat(validator.validate(ValidateableAttestation.aggregateFromValidator(spec, aggregate)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  public void shouldRejectIfSelectionProofIsNotAValidSignatureOfAggregatorIndex() {
    final SignedAggregateAndProof aggregate =
        generator
            .generator()
            .blockAndState(storageSystem.getChainHead())
            .aggregatorIndex(ONE)
            .selectionProof(dataStructureUtil.randomSignature())
            .generate();
    whenAttestationIsValid(aggregate);

    assertThat(validator.validate(ValidateableAttestation.aggregateFromValidator(spec, aggregate)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  public void shouldRejectIfAggregateAndProofSignatureIsNotValid() {
    final SignedAggregateAndProof validAggregate =
        generator.validAggregateAndProof(storageSystem.getChainHead());
    final SignedAggregateAndProof invalidAggregate =
        signedAggregateAndProofSchema.create(
            validAggregate.getMessage(), dataStructureUtil.randomSignature());
    whenAttestationIsValid(invalidAggregate);
    whenAttestationIsValid(validAggregate);

    assertThat(
            validator.validate(
                ValidateableAttestation.aggregateFromValidator(spec, invalidAggregate)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
    assertThat(
            validator.validate(
                ValidateableAttestation.aggregateFromValidator(spec, validAggregate)))
        .isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  private boolean hasSameCommitteeIndex(
      final SignedAggregateAndProof aggregateAndProof, final Attestation attestation) {
    return attestation
        .getData()
        .getIndex()
        .equals(aggregateAndProof.getMessage().getAggregate().getData().getIndex());
  }

  private void whenAttestationIsValid(final SignedAggregateAndProof aggregate) {
    ValidateableAttestation attestation =
        ValidateableAttestation.aggregateFromValidator(spec, aggregate);
    when(attestationValidator.singleOrAggregateAttestationChecks(
            any(), eq(attestation), eq(OptionalInt.empty())))
        .thenReturn(
            SafeFuture.completedFuture(
                InternalValidationResultWithState.accept(getStateFor(aggregate).orElseThrow())));
  }

  private Optional<BeaconState> getStateFor(final SignedAggregateAndProof aggregate) {
    return safeJoin(
        storageSystem
            .recentChainData()
            .retrieveBlockState(
                aggregate.getMessage().getAggregate().getData().getBeaconBlockRoot()));
  }

  private CommitteeAssignment getCommitteeAssignment(
      final StateAndBlockSummary chainHead, final int aggregatorIndex, final UInt64 epoch) {
    return spec.getCommitteeAssignment(chainHead.getState(), epoch, aggregatorIndex).orElseThrow();
  }
}
