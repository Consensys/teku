/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.statetransition.attestation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool.ATTESTATION_RETENTION_SLOTS;
import static tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool.DEFAULT_MAXIMUM_ATTESTATION_COUNT;
import static tech.pegasys.teku.statetransition.attestation.AggregatorUtil.aggregateAttestations;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentMatchers;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestation;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataValidator.AttestationInvalidReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
class AggregatingAttestationPoolTest {

  public static final UInt64 SLOT = UInt64.valueOf(1234);
  private static final int COMMITTEE_SIZE = 130;

  private Spec spec;
  private SpecMilestone specMilestone;
  private DataStructureUtil dataStructureUtil;
  private Optional<UInt64> committeeIndex;
  private final Spec mockSpec = mock(Spec.class);
  private final RecentChainData mockRecentChainData = mock(RecentChainData.class);

  private AggregatingAttestationPool aggregatingPool =
      new AggregatingAttestationPool(
          mockSpec,
          mockRecentChainData,
          new NoOpMetricsSystem(),
          DEFAULT_MAXIMUM_ATTESTATION_COUNT);

  private final AttestationForkChecker forkChecker = mock(AttestationForkChecker.class);

  private Int2IntMap committeeSizes;

  @BeforeEach
  public void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    dataStructureUtil = specContext.getDataStructureUtil();

    committeeSizes = new Int2IntOpenHashMap();
    IntStream.range(0, spec.getGenesisSpec().getConfig().getMaxCommitteesPerSlot())
        .forEach(index -> committeeSizes.put(index, COMMITTEE_SIZE));

    if (specMilestone.equals(PHASE0)) {
      committeeIndex = Optional.empty();
    } else {
      committeeIndex =
          Optional.of(
              dataStructureUtil.randomUInt64(
                  spec.getGenesisSpec().getConfig().getMaxCommitteesPerSlot()));

      final BeaconState state = dataStructureUtil.randomBeaconState();
      final UpdatableStore mockStore = mock(UpdatableStore.class);
      when(mockRecentChainData.getStore()).thenReturn(mockStore);
      when(mockStore.getBlockStateIfAvailable(any())).thenReturn(Optional.of(state));
      when(mockSpec.getBeaconCommitteesSize(any(), any())).thenReturn(committeeSizes);
    }

    when(forkChecker.areAttestationsFromCorrectFork(any())).thenReturn(true);
    when(mockSpec.getPreviousEpochAttestationCapacity(any())).thenReturn(Integer.MAX_VALUE);
    // Fwd some calls to the real spec
    when(mockSpec.computeEpochAtSlot(any()))
        .thenAnswer(i -> spec.computeEpochAtSlot(i.getArgument(0)));
    when(mockSpec.getSlotsPerEpoch(any())).thenAnswer(i -> spec.getSlotsPerEpoch(i.getArgument(0)));
    when(mockSpec.getCurrentEpoch(any(BeaconState.class)))
        .thenAnswer(i -> spec.getCurrentEpoch(i.<BeaconState>getArgument(0)));
    when(mockSpec.atSlot(any())).thenAnswer(invocation -> spec.atSlot(invocation.getArgument(0)));
    when(mockSpec.getGenesisSchemaDefinitions()).thenReturn(spec.getGenesisSchemaDefinitions());
  }

  @TestTemplate
  public void createAggregateFor_shouldReturnEmptyWhenNoAttestationsMatchGivenData() {
    final Optional<ValidatableAttestation> result =
        aggregatingPool.createAggregateFor(
            dataStructureUtil.randomAttestationData().hashTreeRoot(), committeeIndex);
    assertThat(result).isEmpty();
  }

  @TestTemplate
  public void createAggregateFor_shouldAggregateAttestationsWithMatchingData() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 3, 5);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 2, 4, 6);

    final Optional<ValidatableAttestation> result =
        aggregatingPool.createAggregateFor(attestationData.hashTreeRoot(), committeeIndex);
    assertThat(result.map(ValidatableAttestation::getAttestation))
        .contains(aggregateAttestations(attestation1, attestation2));
  }

  @TestTemplate
  public void createAggregateFor_shouldReturnBestAggregateForMatchingDataWhenSomeOverlap() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 3, 5, 7);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 2, 4, 6, 8);
    addAttestationFromValidators(attestationData, 2, 3, 9);

    final Optional<ValidatableAttestation> result =
        aggregatingPool.createAggregateFor(attestationData.hashTreeRoot(), committeeIndex);
    assertThat(result.map(ValidatableAttestation::getAttestation))
        .contains(aggregateAttestations(attestation1, attestation2));
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldReturnEmptyListWhenNoAttestationsAvailable() {
    when(mockSpec.validateAttestation(any(), any())).thenReturn(Optional.empty());

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker)).isEmpty();
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldNotIncludeAttestationsWhereDataDoesNotValidate() {
    addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 1);
    addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 2);
    addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 3);

    when(mockSpec.validateAttestation(any(), any()))
        .thenReturn(Optional.of(AttestationInvalidReason.SLOT_NOT_IN_EPOCH));

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker)).isEmpty();
  }

  @TestTemplate
  void getAttestationsForBlock_shouldNotThrowExceptionWhenShufflingSeedIsUnknown() {
    final Attestation attestation =
        createAttestation(dataStructureUtil.randomAttestationData(ONE), 1, 2, 3, 4);
    // Receive the attestation from a block, prior to receiving it via gossip
    aggregatingPool.onAttestationsIncludedInBlock(ONE, List.of(attestation));
    // Attestation isn't added because it's already redundant
    aggregatingPool.add(ValidatableAttestation.fromValidator(spec, attestation));
    assertThat(aggregatingPool.getSize()).isZero();

    // But we now have a MatchingDataAttestationGroup with unknown shuffling seed present
    // It was previously assumed that wasn't possible so it threw an IllegalStateException
    // Now it should just exclude the group from consideration
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(2));
    final SszList<Attestation> result =
        aggregatingPool.getAttestationsForBlock(state, new AttestationForkChecker(spec, state));
    assertThat(result).isEmpty();
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldIncludeAttestationsThatPassValidation() {
    final Attestation attestation1 =
        addAttestationFromValidators(dataStructureUtil.randomAttestationData(ZERO), 1);
    final Attestation attestation2 =
        addAttestationFromValidators(dataStructureUtil.randomAttestationData(ZERO), 2);
    final Attestation attestation3 =
        addAttestationFromValidators(dataStructureUtil.randomAttestationData(ZERO), 3);

    final BeaconState state = dataStructureUtil.randomBeaconState(ONE);
    when(mockSpec.validateAttestation(state, attestation1.getData()))
        .thenReturn(Optional.of(AttestationInvalidReason.SLOT_NOT_IN_EPOCH));
    when(mockSpec.validateAttestation(state, attestation2.getData())).thenReturn(Optional.empty());
    when(mockSpec.validateAttestation(state, attestation3.getData())).thenReturn(Optional.empty());

    assertThat(aggregatingPool.getAttestationsForBlock(state, forkChecker))
        .containsExactlyInAnyOrder(attestation2, attestation3);
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldAggregateAttestationsWhenPossible() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData(SLOT);
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 3, 4);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState(SLOT.increment());

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactly(aggregateAttestations(attestation1, attestation2));
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldIncludeAttestationsWithDifferentData() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData(ZERO);
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 3, 4);
    final Attestation attestation3 =
        addAttestationFromValidators(dataStructureUtil.randomAttestationData(ZERO), 3, 4);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState(ONE);

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactlyInAnyOrder(aggregateAttestations(attestation1, attestation2), attestation3);
  }

  @TestTemplate
  void getAttestationsForBlock_shouldIncludeMoreRecentAttestationsFirst() {
    final AttestationData attestationData1 =
        dataStructureUtil.randomAttestationData(UInt64.valueOf(5));
    final AttestationData attestationData2 =
        dataStructureUtil.randomAttestationData(UInt64.valueOf(6));
    final AttestationData attestationData3 =
        dataStructureUtil.randomAttestationData(UInt64.valueOf(7));
    final Attestation attestation1 = addAttestationFromValidators(attestationData1, 1, 2);
    final Attestation attestation2 = addAttestationFromValidators(attestationData2, 3, 4);
    final Attestation attestation3 = addAttestationFromValidators(attestationData3, 5, 6);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState(UInt64.valueOf(10));

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactly(attestation3, attestation2, attestation1);
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldNotAddMoreAttestationsThanAllowedInBlock() {
    final int allowed =
        Math.toIntExact(
            spec.atSlot(ONE)
                .getSchemaDefinitions()
                .getBeaconBlockBodySchema()
                .getAttestationsSchema()
                .getMaxLength());

    final int validatorCount = allowed + 1;
    final BeaconState state = dataStructureUtil.randomBeaconState(validatorCount, 100, ONE);
    final AttestationData attestationData = dataStructureUtil.randomAttestationData(ZERO);

    final int lastValidatorIndex = validatorCount - 1;

    // add non aggregatable attestations, more than allowed in block
    for (int i = 0; i < validatorCount; i++) {
      addAttestationFromValidators(attestationData, i, lastValidatorIndex);
    }

    assertThat(aggregatingPool.getAttestationsForBlock(state, forkChecker)).hasSize(allowed);
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldGivePriorityToBestAggregationForEachSlot() {
    // let's test this on electra only, which has only 8 attestations for block
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ELECTRA);
    assertThat(
            spec.atSlot(ONE)
                .getSchemaDefinitions()
                .getBeaconBlockBodySchema()
                .getAttestationsSchema()
                .getMaxLength())
        .isEqualTo(8);

    final BeaconState state = dataStructureUtil.randomBeaconState(ONE);

    // let's prepare 2 different attestationData for the same slot
    final AttestationData attestationData0 = dataStructureUtil.randomAttestationData(ZERO);
    final AttestationData attestationData1 = dataStructureUtil.randomAttestationData(ZERO);

    // let's fill up the pool with non-aggregatable attestationsData0
    addAttestationFromValidators(attestationData0, 1, 2);
    addAttestationFromValidators(attestationData0, 1, 3);
    addAttestationFromValidators(attestationData0, 1, 4);
    addAttestationFromValidators(attestationData0, 1, 5);
    addAttestationFromValidators(attestationData0, 1, 6);
    addAttestationFromValidators(attestationData0, 1, 7);
    addAttestationFromValidators(attestationData0, 1, 8);
    addAttestationFromValidators(attestationData0, 1, 9);

    // let's add a better aggregation for attestationData1
    final Attestation bestAttestation = addAttestationFromValidators(attestationData1, 11, 14, 15);

    assertThat(aggregatingPool.getAttestationsForBlock(state, forkChecker).get(0))
        .isEqualTo(bestAttestation);
  }

  @TestTemplate
  void getAttestationsForBlock_shouldLimitPreviousEpochAttestations_capacityOf2() {
    testPrevEpochLimits(2);
  }

  @TestTemplate
  void getAttestationsForBlock_shouldLimitPreviousEpochAttestations_capacityOf1() {
    testPrevEpochLimits(1);
  }

  @TestTemplate
  void getAttestationsForBlock_shouldLimitPreviousEpochAttestations_capacityOf0() {
    testPrevEpochLimits(0);
  }

  void testPrevEpochLimits(final int prevEpochCapacity) {
    final UInt64 currentEpoch = UInt64.valueOf(5);
    final UInt64 startSlotAtCurrentEpoch = spec.computeStartSlotAtEpoch(currentEpoch);
    final BeaconState stateAtBlockSlot =
        specMilestone.isGreaterThanOrEqualTo(ELECTRA)
            ? dataStructureUtil
                .stateBuilderElectra(10, 20)
                .slot(startSlotAtCurrentEpoch.plus(5))
                .build()
            : dataStructureUtil
                .stateBuilderPhase0(10, 20)
                .slot(startSlotAtCurrentEpoch.plus(5))
                .build();
    when(mockSpec.getPreviousEpochAttestationCapacity(stateAtBlockSlot))
        .thenReturn(prevEpochCapacity);

    final List<Attestation> expectedAttestations = new ArrayList<>();
    // Current epoch Attestations
    expectedAttestations.add(addAttestationFromValidators(startSlotAtCurrentEpoch.plus(2), 1));
    expectedAttestations.add(addAttestationFromValidators(startSlotAtCurrentEpoch.plus(1), 2));
    expectedAttestations.add(addAttestationFromValidators(startSlotAtCurrentEpoch, 3));

    // Prev epoch attestations within capacity limit
    for (int i = 0; i < prevEpochCapacity; i++) {
      expectedAttestations.add(
          addAttestationFromValidators(startSlotAtCurrentEpoch.minus(i + 1), 3 + i));
    }
    // Add a few extras
    addAttestationFromValidators(startSlotAtCurrentEpoch.minus(3), 1);
    addAttestationFromValidators(startSlotAtCurrentEpoch.minus(4), 2);

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsExactlyElementsOf(expectedAttestations);
  }

  @TestTemplate
  public void onSlot_shouldPruneAttestationsMoreThanTwoEpochsBehindCurrentSlot() {
    final AttestationData pruneAttestationData = dataStructureUtil.randomAttestationData(SLOT);
    final AttestationData preserveAttestationData =
        dataStructureUtil.randomAttestationData(SLOT.plus(ONE));
    addAttestationFromValidators(pruneAttestationData, 1);
    final Attestation preserveAttestation =
        addAttestationFromValidators(preserveAttestationData, 2);

    final BeaconState stateAtBlockSlot = dataStructureUtil.randomBeaconState();

    assertThat(aggregatingPool.getSize()).isEqualTo(2);
    aggregatingPool.onSlot(
        pruneAttestationData.getSlot().plus(ATTESTATION_RETENTION_SLOTS).plus(ONE));

    assertThat(aggregatingPool.getAttestationsForBlock(stateAtBlockSlot, forkChecker))
        .containsOnly(preserveAttestation);
    assertThat(aggregatingPool.getSize()).isEqualTo(1);
  }

  @TestTemplate
  public void getSize_shouldIncludeAttestationsAdded() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();

    addAttestationFromValidators(attestationData, 1, 2, 3, 4);
    addAttestationFromValidators(attestationData, 2, 5);
    assertThat(aggregatingPool.getSize()).isEqualTo(2);
  }

  @TestTemplate
  public void getSize_shouldDecreaseWhenAttestationsRemoved() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    addAttestationFromValidators(attestationData, 1, 2, 3, 4);
    final Attestation attestationToRemove = addAttestationFromValidators(attestationData, 2, 5);
    aggregatingPool.onAttestationsIncludedInBlock(ZERO, List.of(attestationToRemove));
    assertThat(aggregatingPool.getSize()).isEqualTo(1);
  }

  @TestTemplate
  public void getSize_shouldNotIncrementWhenAttestationAlreadyExists() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();

    final Attestation attestation = addAttestationFromValidators(attestationData, 1, 2, 3, 4);
    aggregatingPool.add(ValidatableAttestation.from(spec, attestation));
    assertThat(aggregatingPool.getSize()).isEqualTo(1);
  }

  @TestTemplate
  public void getSize_shouldDecrementForAllRemovedAttestations() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    addAttestationFromValidators(attestationData, 1, 2, 3);
    addAttestationFromValidators(attestationData, 4, 5);
    assertThat(aggregatingPool.getSize()).isEqualTo(2);
    final Attestation attestationToRemove =
        addAttestationFromValidators(attestationData, 1, 2, 3, 4, 5);
    aggregatingPool.onAttestationsIncludedInBlock(ZERO, List.of(attestationToRemove));
    assertThat(aggregatingPool.getSize()).isEqualTo(0);
  }

  @TestTemplate
  public void getSize_shouldAddTheRightData() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    addAttestationFromValidators(attestationData, 1, 2, 3, 4, 5);
    addAttestationFromValidators(attestationData, 1, 2, 3);
    addAttestationFromValidators(attestationData, 4, 5);
    addAttestationFromValidators(attestationData, 6);
    addAttestationFromValidators(attestationData, 7, 8);
    assertThat(aggregatingPool.getSize()).isEqualTo(5);
  }

  @TestTemplate
  public void getSize_shouldDecrementForAllRemovedAttestationsWhileKeepingOthers() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();

    addAttestationFromValidators(attestationData, 1, 2, 3);
    addAttestationFromValidators(attestationData, 4, 5);
    addAttestationFromValidators(attestationData, 6);
    addAttestationFromValidators(attestationData, 7, 8);

    final Attestation attestationToRemove =
        addAttestationFromValidators(attestationData, 1, 2, 3, 4, 5);
    assertThat(aggregatingPool.getSize()).isEqualTo(5);

    aggregatingPool.onAttestationsIncludedInBlock(ZERO, List.of(attestationToRemove));
    assertThat(aggregatingPool.getSize()).isEqualTo(2);
  }

  @TestTemplate
  void shouldRemoveOldSlotsWhenMaximumNumberOfAttestationsReached() {
    aggregatingPool =
        new AggregatingAttestationPool(mockSpec, mockRecentChainData, new NoOpMetricsSystem(), 5);
    final AttestationData attestationData0 = dataStructureUtil.randomAttestationData(ZERO);
    final AttestationData attestationData1 = dataStructureUtil.randomAttestationData(ONE);
    final AttestationData attestationData2 =
        dataStructureUtil.randomAttestationData(UInt64.valueOf(2));
    addAttestationFromValidators(attestationData0, 1);
    addAttestationFromValidators(attestationData0, 2);
    addAttestationFromValidators(attestationData1, 3);
    addAttestationFromValidators(attestationData1, 4);
    addAttestationFromValidators(attestationData2, 5);

    assertThat(aggregatingPool.getSize()).isEqualTo(5);

    final BeaconState slot1State = dataStructureUtil.randomBeaconState(ONE);
    assertThat(aggregatingPool.getAttestationsForBlock(slot1State, forkChecker)).isNotEmpty();

    addAttestationFromValidators(attestationData2, 6);
    // Should drop the slot 0 attestations
    assertThat(aggregatingPool.getSize()).isEqualTo(4);
    assertThat(aggregatingPool.getAttestationsForBlock(slot1State, forkChecker)).isEmpty();
  }

  @TestTemplate
  void shouldNotRemoveLastSlotEvenWhenMaximumNumberOfAttestationsReached() {
    aggregatingPool =
        new AggregatingAttestationPool(mockSpec, mockRecentChainData, new NoOpMetricsSystem(), 5);
    final AttestationData attestationData = dataStructureUtil.randomAttestationData(ZERO);
    addAttestationFromValidators(attestationData, 1);
    addAttestationFromValidators(attestationData, 2);
    addAttestationFromValidators(attestationData, 3);
    addAttestationFromValidators(attestationData, 4);
    addAttestationFromValidators(attestationData, 5);

    assertThat(aggregatingPool.getSize()).isEqualTo(5);

    final BeaconState slot1State = dataStructureUtil.randomBeaconState(ONE);
    assertThat(aggregatingPool.getAttestationsForBlock(slot1State, forkChecker)).isNotEmpty();

    addAttestationFromValidators(attestationData, 6);
    // Can't drop anything as we only have one slot.
    assertThat(aggregatingPool.getSize()).isEqualTo(6);
  }

  @TestTemplate
  public void getAttestationsForBlock_shouldNotAddAttestationsFromWrongFork() {
    final AttestationData attestationData1 = dataStructureUtil.randomAttestationData(ZERO);
    final AttestationData attestationData2 = dataStructureUtil.randomAttestationData(ZERO);

    addAttestationFromValidators(attestationData1, 1, 2, 3);
    Attestation attestation2 = addAttestationFromValidators(attestationData2, 4, 5);

    when(forkChecker.areAttestationsFromCorrectFork(any())).thenReturn(false);
    when(forkChecker.areAttestationsFromCorrectFork(
            ArgumentMatchers.argThat(arg -> arg.getAttestationData().equals(attestationData2))))
        .thenReturn(true);

    final BeaconState state = dataStructureUtil.randomBeaconState(ONE);
    assertThat(aggregatingPool.getAttestationsForBlock(state, forkChecker))
        .containsExactly(attestation2);
  }

  @TestTemplate
  public void getAttestations_shouldReturnAllAttestations() {
    final AttestationData firstAttestationData = dataStructureUtil.randomAttestationData();
    final Attestation firstAttestation =
        addAttestationFromValidators(firstAttestationData, 1, 2, 3);
    final AttestationData secondAttestationData = dataStructureUtil.randomAttestationData();
    final Attestation secondAttestation =
        addAttestationFromValidators(secondAttestationData, 3, 4, 5);
    assertThat(aggregatingPool.getAttestations(Optional.empty(), Optional.empty()))
        .containsExactlyInAnyOrder(firstAttestation, secondAttestation);
  }

  @TestTemplate
  public void
      getAttestations_shouldReturnElectraAttestationsOnly_whenElectraActivatesAndNoSlotProvided() {
    // Genesis spec must be before Electra in order to be able to add phase0 attestations
    assumeThat(specMilestone).isLessThan(ELECTRA);
    final Spec mockedSpec = mock(Spec.class);
    final AggregatingAttestationPool aggregatingPool =
        new AggregatingAttestationPool(
            mockedSpec,
            mockRecentChainData,
            new NoOpMetricsSystem(),
            DEFAULT_MAXIMUM_ATTESTATION_COUNT);
    // Adding a phase0 attestation to the aggregation pool
    final Spec phase0Spec = TestSpecFactory.createMinimalPhase0();
    when(mockedSpec.atSlot(any())).thenReturn(phase0Spec.getGenesisSpec());
    final DataStructureUtil phase0DataStructureUtil = new DataStructureUtil(phase0Spec);
    final AttestationData phase0AttestationData =
        phase0DataStructureUtil.randomAttestationData(SLOT.minus(4));
    addAttestationFromValidators(aggregatingPool, phase0AttestationData, phase0Spec, 4, 5);

    // Adding an Electra attestation to the aggregation pool
    final Spec electraSpec = TestSpecFactory.createMinimalElectra();
    // Electra activates from SLOT
    when(mockedSpec.atSlot(argThat(slot -> slot.isGreaterThanOrEqualTo(SLOT))))
        .thenReturn(electraSpec.getGenesisSpec());
    final AttestationData electraAttestationData = dataStructureUtil.randomAttestationData(SLOT);
    committeeIndex =
        Optional.of(
            dataStructureUtil.randomUInt64(
                electraSpec.getGenesisSpec().getConfig().getMaxCommitteesPerSlot()));
    final Attestation electraAttestation =
        addAttestationFromValidators(aggregatingPool, electraAttestationData, electraSpec, 1, 2, 3);

    when(mockRecentChainData.getCurrentSlot()).thenReturn(Optional.of(SLOT));
    // Should get the Electra attestation only
    assertThat(aggregatingPool.getAttestations(Optional.empty(), Optional.empty()))
        .containsExactly(electraAttestation);
  }

  @TestTemplate
  public void getAttestations_shouldReturnAttestationsForGivenCommitteeIndexOnly_PreElectra() {
    assumeThat(specMilestone).isLessThan(ELECTRA);
    // Pre Electra the committee index filter is applied to the index set at the attestation data
    // level
    final AttestationData attestationData1 = dataStructureUtil.randomAttestationData();
    final AttestationData attestationData2 =
        new AttestationData(
            attestationData1.getSlot(),
            attestationData1.getIndex().plus(1),
            attestationData1.getBeaconBlockRoot(),
            attestationData1.getSource(),
            attestationData1.getTarget());
    final Attestation attestation1 = addAttestationFromValidators(attestationData1, 1, 2, 3);
    addAttestationFromValidators(attestationData2, 4, 5, 6);
    assertThat(
            aggregatingPool.getAttestations(
                Optional.empty(), Optional.of(attestationData1.getIndex())))
        .containsExactly(attestation1);
  }

  @TestTemplate
  public void getAttestations_shouldReturnAttestationsForGivenCommitteeIndexOnly_PostElectra() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(ELECTRA);
    // Post Electra the committee index filter is applied to the committee bits
    final AttestationData attestationData1 = dataStructureUtil.randomAttestationData();
    final AttestationData attestationData2 = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData1, 1, 2, 3);
    final Optional<UInt64> committeeIndexFilter = committeeIndex;
    committeeIndex = Optional.of(committeeIndex.get().plus(1));
    addAttestationFromValidators(attestationData2, 4, 5, 6);
    assertThat(aggregatingPool.getAttestations(Optional.empty(), committeeIndexFilter))
        .containsExactly(attestation1);
  }

  @TestTemplate
  public void getAttestations_shouldReturnAttestationsForGivenSlotOnly() {
    final AttestationData attestationData1 = dataStructureUtil.randomAttestationData();
    final AttestationData attestationData2 =
        new AttestationData(
            attestationData1.getSlot().plus(1),
            attestationData1.getIndex(),
            attestationData1.getBeaconBlockRoot(),
            attestationData1.getSource(),
            attestationData1.getTarget());
    Attestation attestation1 = addAttestationFromValidators(attestationData1, 1, 2, 3);
    addAttestationFromValidators(attestationData2, 4, 5, 6);
    assertThat(
            aggregatingPool.getAttestations(
                Optional.of(attestationData1.getSlot()), Optional.empty()))
        .containsExactly(attestation1);
  }

  @TestTemplate
  void onAttestationsIncludedInBlock_shouldNotAddAttestationsAlreadySeenInABlock() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData(ZERO);
    // Included in block before we see any attestations with this data
    aggregatingPool.onAttestationsIncludedInBlock(
        ONE, List.of(createAttestation(attestationData, 1, 2, 3, 4)));

    // But still shouldn't be able to add a redundant attestation later
    addAttestationFromValidators(attestationData, 2, 3);
    assertThat(aggregatingPool.getSize()).isZero();
  }

  @TestTemplate
  void onAttestationsIncludedInBlock_shouldRemoveAttestationsWhenSeenInABlock() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData(ZERO);
    addAttestationFromValidators(attestationData, 2, 3);

    aggregatingPool.onAttestationsIncludedInBlock(
        ONE, List.of(createAttestation(attestationData, 1, 2, 3, 4)));

    assertThat(aggregatingPool.getSize()).isZero();
  }

  @TestTemplate
  void onReorg_shouldBeAbleToReadAttestations() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData(ZERO);
    // Included in block before we see any attestations with this data
    aggregatingPool.onAttestationsIncludedInBlock(
        ONE, List.of(createAttestation(attestationData, 1, 2, 3, 4)));

    aggregatingPool.onReorg(ZERO);

    // Should now be able to add attestations that were redundant
    addAttestationFromValidators(attestationData, 2, 3);
    assertThat(aggregatingPool.getSize()).isEqualTo(1);
  }

  private Attestation addAttestationFromValidators(final UInt64 slot, final int... validators) {
    return addAttestationFromValidators(dataStructureUtil.randomAttestationData(slot), validators);
  }

  private Attestation addAttestationFromValidators(
      final AttestationData data, final int... validators) {
    return addAttestationFromValidators(data, spec, validators);
  }

  private Attestation addAttestationFromValidators(
      final AttestationData data, final Spec spec, final int... validators) {
    return addAttestationFromValidators(aggregatingPool, data, spec, validators);
  }

  private Attestation addAttestationFromValidators(
      final AggregatingAttestationPool aggregatingAttestationPool,
      final AttestationData data,
      final Spec spec,
      final int... validators) {
    final Attestation attestationFromValidators;
    final Attestation attestation;
    if (specMilestone.isGreaterThanOrEqualTo(ELECTRA) && validators.length == 1) {
      attestationFromValidators = createSingleAttestation(data, validators[0]);
    } else {
      attestationFromValidators = createAttestation(data, spec, validators);
    }

    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(spec, attestationFromValidators, committeeSizes);

    if (attestationFromValidators.isSingleAttestation()) {
      attestation = createAttestation(data, spec, validators);
      validatableAttestation.convertToAggregatedFormatFromSingleAttestation(attestation);
    } else {
      attestation = attestationFromValidators;
    }

    validatableAttestation.saveCommitteeShufflingSeedAndCommitteesSize(
        dataStructureUtil.randomBeaconState(100, 15, data.getSlot()));
    aggregatingAttestationPool.add(validatableAttestation);
    return attestation;
  }

  private Attestation createAttestation(final AttestationData data, final int... validators) {
    return createAttestation(data, spec, validators);
  }

  private SingleAttestation createSingleAttestation(
      final AttestationData data, final int validatorIndex) {
    final SingleAttestationSchema attestationSchema =
        spec.getGenesisSchemaDefinitions()
            .toVersionElectra()
            .orElseThrow()
            .getSingleAttestationSchema();

    return attestationSchema.create(
        committeeIndex.orElseThrow(),
        UInt64.valueOf(validatorIndex),
        data,
        dataStructureUtil.randomSignature());
  }

  private Attestation createAttestation(
      final AttestationData data, final Spec spec, final int... validators) {
    final AttestationSchema<?> attestationSchema =
        spec.getGenesisSchemaDefinitions().getAttestationSchema();
    final SszBitlist bitlist =
        spec.getGenesisSchemaDefinitions()
            .getAttestationSchema()
            .getAggregationBitsSchema()
            .ofBits(COMMITTEE_SIZE, validators);

    final Supplier<SszBitvector> committeeBits;

    if (spec.atSlot(data.getSlot()).getMilestone().isGreaterThanOrEqualTo(ELECTRA)) {
      committeeBits =
          () ->
              attestationSchema
                  .getCommitteeBitsSchema()
                  .orElseThrow()
                  .ofBits(committeeIndex.orElseThrow().intValue());
    } else {
      committeeBits = () -> null;
    }
    return attestationSchema.create(
        bitlist, data, dataStructureUtil.randomSignature(), committeeBits);
  }
}
