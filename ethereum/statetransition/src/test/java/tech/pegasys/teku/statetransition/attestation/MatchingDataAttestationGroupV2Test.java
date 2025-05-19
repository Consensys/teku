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

package tech.pegasys.teku.statetransition.attestation;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.statetransition.attestation.AggregatorUtil.aggregateAttestations;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
class MatchingDataAttestationGroupV2Test {
  private static final UInt64 SLOT = UInt64.valueOf(1234);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private AttestationSchema<Attestation> attestationSchema;

  private AttestationData attestationData;

  private MatchingDataAttestationGroupV2 group;
  private Int2IntMap committeeSizes;

  @BeforeEach
  public void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    attestationSchema = spec.getGenesisSchemaDefinitions().getAttestationSchema();
    dataStructureUtil = specContext.getDataStructureUtil();
    attestationData = dataStructureUtil.randomAttestationData(SLOT);
    committeeSizes = new Int2IntOpenHashMap();
    committeeSizes.put(0, 10);
    committeeSizes.put(1, 10);
    group =
        new MatchingDataAttestationGroupV2(
            spec, System::nanoTime, attestationData, Optional.of(committeeSizes), false);
  }

  @TestTemplate
  public void isEmpty_shouldBeEmptyInitially() {
    assertThat(group.isEmpty()).isTrue();
  }

  @TestTemplate
  public void isEmpty_shouldNotBeEmptyWhenAnAttestationIsAdded() {
    addPooledAttestation(1);
    assertThat(group.isEmpty()).isFalse();
  }

  @TestTemplate
  public void isEmpty_shouldBeEmptyAfterAttestationRemoved() {
    final Attestation attestation = toAttestation(addPooledAttestation(1));
    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, attestation);

    assertThat(group.isEmpty()).isTrue();
    assertThat(numRemoved).isEqualTo(1);
  }

  @TestTemplate
  public void onAttestationIncludedInBlock_shouldRemoveAttestationEvenWhenInstanceIsDifferent() {
    final Attestation attestation = toAttestation(addPooledAttestation(1));
    final Attestation copy = attestationSchema.sszDeserialize(attestation.sszSerialize());
    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, copy);

    verifyGroupContainsExactly(); // empty
    assertThat(group.isEmpty()).isTrue();
    assertThat(numRemoved).isEqualTo(1);
  }

  @TestTemplate
  public void onAttestationIncludedInBlock_multipleCallsShouldAggregate() {

    // Create attestations that will be removed
    final PooledAttestation attestation1 = createPooledAttestation(1);
    final PooledAttestation attestation2 = createPooledAttestation(2);

    // Add some attestations
    final PooledAttestation attestation3 = addPooledAttestation(3);
    addPooledAttestation(1, 2); // This will be an aggregate, not single

    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation1));
    assertThat(numRemoved).isEqualTo(0); // Attestation (1) is covered by (1,2) which is still there
    numRemoved += group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation2));
    assertThat(numRemoved).isEqualTo(1);
    verifyGroupContainsExactly(toPooledAttestationWithData(attestation3));
  }

  @TestTemplate
  public void
      onAttestationIncludedInBlock_shouldRemoveAttestationsThatAreAggregatedIntoRemovedAttestation() {
    final PooledAttestation attestation1 = addPooledAttestation(1);
    final PooledAttestation attestation2 = addPooledAttestation(2);
    final PooledAttestation attestation3 = addPooledAttestation(3);

    int numRemoved =
        group.onAttestationIncludedInBlock(
            UInt64.ZERO,
            aggregateAttestations(toAttestation(attestation1), toAttestation(attestation2)));

    verifyGroupContainsExactly(toPooledAttestationWithData(attestation3));
    assertThat(numRemoved).isEqualTo(2);
  }

  @TestTemplate
  public void add_shouldIgnoreAttestationWhoseBitsHaveAllBeenRemoved() {
    // Create attestations that will be removed
    final PooledAttestation attestation1 = createPooledAttestation(1);
    final PooledAttestation attestation2 = createPooledAttestation(2);

    // Create attestation to be added / ignored
    final PooledAttestation attestationToIgnore = createPooledAttestation(1, 2);

    int numRemoved = group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation1));
    numRemoved +=  group.onAttestationIncludedInBlock(UInt64.ZERO, toAttestation(attestation2));
    assertThat(numRemoved).isEqualTo(0);

    assertThat(group.add(attestationToIgnore, Optional.empty())).isFalse();
    verifyGroupContainsExactly(); // empty
  }

  // TODO: is this an aggregate flow test?

  @TestTemplate
  public void add_shouldAggregateAttestationsFromSameCommittee(final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation attestation1 = addPooledAttestation(Optional.of(0), 1); // single
    final PooledAttestation attestation2 = addPooledAttestation(Optional.of(1), 2); // single
    final PooledAttestation attestation3 = addPooledAttestation(Optional.of(1), 3); // single

    assertThat(group.streamForAggregationProduction(Optional.of(UInt64.ZERO), Long.MAX_VALUE))
        .containsExactly(toPooledAttestationWithData(attestation1));

    final Attestation expected =
        aggregateAttestations(toAttestation(attestation2), toAttestation(attestation3));

    verifyGroupContainsExactly(
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(spec, expected, committeeSizes))));
  }

  @TestTemplate
  public void add_shouldIgnoreDuplicateAttestations() {
    final PooledAttestation attestation = addPooledAttestation(1, 2);
    final PooledAttestation copy =
        PooledAttestation.fromValidatableAttestation(
            ValidatableAttestation.from(
                spec,
                attestationSchema.sszDeserialize(toAttestation(attestation).sszSerialize()),
                committeeSizes),
            validatorBitToValidatorIndex(1, 2));

    assertThat(group.add(copy, Optional.empty())).isFalse();

    verifyGroupContainsExactly(toPooledAttestationWithData(attestation));
  }

//  // TODO: migrate iterator_ to apiRequest?
//
//  @TestTemplate
//  public void iterator_shouldAggregateAttestationsWhereValidatorsDoNotOverlap() {
//    final PooledAttestation attestation1 = addPooledAttestation(1);
//    final PooledAttestation attestation2 = addPooledAttestation(2);
//
//    final Attestation expected =
//        aggregateAttestations(toAttestation(attestation1), toAttestation(attestation2));
//
//    verifyGroupContainsExactly(
//        toPooledAttestationWithData(
//            PooledAttestation.fromValidatableAttestation(
//                ValidatableAttestation.from(spec, expected, committeeSizes))));
//  }



  // --- Tests for streamForApiRequest ---
  @TestTemplate
  public void streamForApiRequest_shouldAggregateDisjointAttestations(final SpecContext specContext) {
    final PooledAttestation attestation1 = addPooledAttestation(1);
    final PooledAttestation attestation2 = addPooledAttestation(2);

    final Attestation expectedAggregate =
            aggregateAttestations(toAttestation(attestation1), toAttestation(attestation2));

    verifyStreamForApiRequest(
            Optional.empty(),
            isElectra(specContext),
            toPooledAttestationWithData(
                    PooledAttestation.fromValidatableAttestation(
                            ValidatableAttestation.from(spec, expectedAggregate, committeeSizes))));
  }

  @TestTemplate
  public void streamForApiRequest_shouldPrioritizeLargerAndAggregateNonOverlapping(final SpecContext specContext) {
    final PooledAttestation bigAttestation = addPooledAttestation(1, 3, 5, 7); // Aggregate (4 validators)
    final PooledAttestation mediumAttestation = addPooledAttestation(3, 5, 9); // Aggregate (3 validators)
    final PooledAttestation littleAttestation = addPooledAttestation(2, 4);   // Aggregate (2 validators)

    final Attestation combinedBigLittle = aggregateAttestations(toAttestation(bigAttestation), toAttestation(littleAttestation));

    verifyStreamForApiRequest(
            Optional.empty(),
            isElectra(specContext),
            toPooledAttestationWithData(PooledAttestation.fromValidatableAttestation(
                    ValidatableAttestation.from(spec, combinedBigLittle, committeeSizes))), // Aggregate of (1,2,3,4,5,7)
            toPooledAttestationWithData(mediumAttestation) // Separate (3,5,9) because 9 is new, but 3,5 overlap
    );
  }

  @TestTemplate
  public void streamForApiRequest_shouldReturnOverlappingAttestationsSeparately(final SpecContext specContext) {
    final PooledAttestation attestation1 = addPooledAttestation(1, 2, 5);
    final PooledAttestation attestation2 = addPooledAttestation(1, 2, 3);

    // These overlap but neither is a superset of the other. They should be returned separately.
    verifyStreamForApiRequest(
            Optional.empty(),
            isElectra(specContext),
            toPooledAttestationWithData(attestation1),
            toPooledAttestationWithData(attestation2));
  }


  @TestTemplate
  void streamForApiRequest_shouldAggregateLeavingNoRedundantParts(final SpecContext specContext) {
    final PooledAttestation useful1 = addPooledAttestation(1, 2, 3);
    addPooledAttestation(2, 4); // This is (2,4). Partially overlaps with (1,2,3) and (4)
    final PooledAttestation useful2 = addPooledAttestation(4);

    // Expect (1,2,3) and (4) to be aggregated into (1,2,3,4).
    // The attestation (2,4) becomes redundant.
    final PooledAttestationWithData expected =
            toPooledAttestationWithData(
                    PooledAttestation.fromValidatableAttestation(
                            ValidatableAttestation.from(
                                    spec,
                                    aggregateAttestations(toAttestation(useful1), toAttestation(useful2)),
                                    committeeSizes)));
    verifyStreamForApiRequest(Optional.empty(), isElectra(specContext), expected);
  }

  @TestTemplate
  void streamForApiRequest_electra_withCommitteeIndex_returnsMatchingAggregated(final SpecContext specContext) {
    specContext.assumeElectraActive();
    // C0 attestations
    final PooledAttestation singleC0V1 = addPooledAttestation(Optional.of(0), 1); // Single
    final PooledAttestation aggC0V23 = addPooledAttestation(Optional.of(0), 2, 3); // Aggregate
    // C1 attestations
    final PooledAttestation singleC1V4 = addPooledAttestation(Optional.of(1), 4); // Single

    // Request for committee 0
    // Expected: aggregate of singleC0V1 and aggC0V23
    final Attestation expectedForC0 = aggregateAttestations(toAttestation(singleC0V1), toAttestation(aggC0V23));

    verifyStreamForApiRequest(
            Optional.of(UInt64.ZERO),
            true,
            toPooledAttestationWithData(PooledAttestation.fromValidatableAttestation(
                    ValidatableAttestation.from(spec, expectedForC0, committeeSizes))));

    // Request for committee 1
    // Expected: single from singleC1V4
    verifyStreamForApiRequest(
            Optional.of(UInt64.ONE),
            true,
            toPooledAttestationWithData(singleC1V4));
  }


  // --- Tests for streamForAggregationProduction ---
  @TestTemplate
  void streamForAggregationProduction_phase0_withAggregates_returnsAggregatedResult(final SpecContext specContext) {
    specContext.assumeIsNotOneOf(ELECTRA);
    final PooledAttestation att1 = addPooledAttestation(1, 2);
    final PooledAttestation att2 = addPooledAttestation(3);
    final Attestation expected = aggregateAttestations(toAttestation(att1), toAttestation(att2));

    // streamForAggregationProduction without committee index (or pre-Electra) uses attestationsByValidatorCount
    verifyStreamForAggregationProductionPhase0ContainsExactly(
            toPooledAttestationWithData(
                    PooledAttestation.fromValidatableAttestation(ValidatableAttestation.from(spec, expected, committeeSizes))));
  }

  @TestTemplate
  void streamForAggregationProduction_electra_noCommitteeIndex_returnsOnlyAggregatedAggregates(final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation agg1 = addPooledAttestation(1, 2); // Aggregate
    addPooledAttestation(Optional.of(0),3); // Single on committee 0
    final PooledAttestation agg2 = addPooledAttestation(4,5); // Aggregate

    // Without committee index, Electra's aggregation production streams from attestationsByValidatorCount.
    // Singles are ignored. Aggregates agg1 and agg2 should be combined.
    final Attestation expected = aggregateAttestations(toAttestation(agg1), toAttestation(agg2));
    verifyStreamForAggregationProductionContainsExactly(
            toPooledAttestationWithData(
                    PooledAttestation.fromValidatableAttestation(ValidatableAttestation.from(spec, expected, committeeSizes))));
  }

  @TestTemplate
  void streamForAggregationProduction_electra_withCommitteeIndex_returnsOnlyAggregatedMatchingSingles(final SpecContext specContext) {
    specContext.assumeElectraActive();
    // Committee 0
    final PooledAttestation singleC0V1 = addPooledAttestation(Optional.of(0), 1);
    final PooledAttestation singleC0V2 = addPooledAttestation(Optional.of(0), 2);
    addPooledAttestation(Optional.of(0), 5, 6); // Aggregate on C0, should be ignored by this stream

    // Committee 1
    addPooledAttestation(Optional.of(1), 3); // Single on C1, should be ignored

    // Request for committee 0. Expect aggregation of singleC0V1 and singleC0V2.
    final Attestation expectedForC0 = aggregateAttestations(toAttestation(singleC0V1), toAttestation(singleC0V2));
    verifyStreamForAggregationProductionContainsExactly(
            toPooledAttestationWithData(
                    PooledAttestation.fromValidatableAttestation(ValidatableAttestation.from(spec, expectedForC0, committeeSizes))));
  }




  // TODO: block production flow

  @TestTemplate
  public void iterator_shouldAggregateAttestationsWithMoreValidatorsFirst() {
    final PooledAttestation bigAttestation = addPooledAttestation(1, 3, 5, 7);
    final PooledAttestation mediumAttestation = addPooledAttestation(3, 5, 9);
    final PooledAttestation littleAttestation = addPooledAttestation(2, 4);

    verifyGroupContainsExactly(
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(
                    spec,
                    aggregateAttestations(
                        toAttestation(bigAttestation), toAttestation(littleAttestation)),
                    committeeSizes))),
        toPooledAttestationWithData(mediumAttestation));
  }

  @TestTemplate
  public void iterator_electra_shouldAggregateSkipSingleAttestationsInBlockProduction(
      final SpecContext specContext) {
    specContext.assumeElectraActive();
    final PooledAttestation bigAttestation = addPooledAttestation(1, 3, 5, 7); // aggregate
    final PooledAttestation mediumAttestation = addPooledAttestation(3, 5, 9); // aggregate
    addPooledAttestation(2); // single, goes to singleAttestationsByCommitteeIndex in V2

    // streamForBlockProduction in V2 sources from attestationsByValidatorCount,
    // so it won't see the single attestation.
    assertThat(group.streamForBlockProduction(Long.MAX_VALUE))
        .containsExactly(
            toPooledAttestationWithData(bigAttestation),
            toPooledAttestationWithData(mediumAttestation));
  }

  @TestTemplate
  public void iterator_shouldNotAggregateAttestationsWhenValidatorsOverlap() {
    final PooledAttestation attestation1 = addPooledAttestation(1, 2, 5);
    final PooledAttestation attestation2 = addPooledAttestation(1, 2, 3);

    assertThat(group.streamForBlockProduction(Long.MAX_VALUE))
        .containsExactlyInAnyOrder(
            toPooledAttestationWithData(attestation1), toPooledAttestationWithData((attestation2)));
  }

  @TestTemplate
  public void iterator_shouldOmitAttestationsThatAreAlreadyIncludedInTheAggregate() {
    final PooledAttestation aggregate = addPooledAttestation(1, 2, 3);
    addPooledAttestation(2); // This would be a single attestation

    // streamForBlockProduction will only show 'aggregate' (if not Electra, or if Electra and it's
    // an aggregate)
    // If Electra, addPooledAttestation(2) is single, not seen by streamForBlockProduction.
    // If not Electra, addPooledAttestation(2) is an aggregate with 1 bit.
    //   The AggregatingIterator for streamForBlockProduction should handle this.
    //   The iterator first processes (1,2,3). Then (2) is redundant.
    assertThat(group.streamForBlockProduction(Long.MAX_VALUE))
        .containsExactly(toPooledAttestationWithData(aggregate));
  }

  @TestTemplate
  void iterator_shouldOmitAttestationsThatOverlapWithFirstAttestationAndAreRedundantWithCombined() {
    final PooledAttestation useful1 = addPooledAttestation(1, 2, 3);
    addPooledAttestation(2, 4);
    final PooledAttestation useful2 = addPooledAttestation(4);

    final PooledAttestationWithData expected =
        toPooledAttestationWithData(
            PooledAttestation.fromValidatableAttestation(
                ValidatableAttestation.from(
                    spec,
                    aggregateAttestations(toAttestation(useful1), toAttestation(useful2)),
                    committeeSizes)));
    // streamForAggregationProduction with committeeIndex 0 will be used here as per original test.
    // This assumes pre-Electra behavior for this test logic w.r.t committee index 0,
    // or that these attestations are for committee 0.
    verifyGroupContainsExactly(expected);
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldRemoveAttestationsMadeRedundant() {
    final PooledAttestation attestation1 = addPooledAttestation(1, 2, 3, 4);
    final PooledAttestation attestation2 = addPooledAttestation(1, 5, 7);
    final PooledAttestation attestation3 = addPooledAttestation(1, 6);

    assertThat(group.size()).isEqualTo(3);
    assertThat(group.streamForBlockProduction(Long.MAX_VALUE))
        .containsExactlyInAnyOrder(
            toPooledAttestationWithData(attestation1),
            toPooledAttestationWithData(attestation2),
            toPooledAttestationWithData(attestation3));

    group.onAttestationIncludedInBlock(
        UInt64.ZERO, toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6, 7)));

    assertThat(group.size()).isZero();
    verifyGroupContainsExactly();
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldNotRemoveAttestationsWithAdditionalValidators() {
    final PooledAttestation attestation1 = addPooledAttestation(1, 2, 3, 4);
    final PooledAttestation attestation2 = addPooledAttestation(1, 5, 7);
    final PooledAttestation attestation3 = addPooledAttestation(1, 6);

    assertThat(group.size()).isEqualTo(3);
    assertThat(group.streamForBlockProduction(Long.MAX_VALUE))
        .containsExactlyInAnyOrder(
            toPooledAttestationWithData(attestation1),
            toPooledAttestationWithData(attestation2),
            toPooledAttestationWithData(attestation3));

    group.onAttestationIncludedInBlock(
        UInt64.ZERO, toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6)));

    // Validator 7 is still relevant (from attestation2)
    assertThat(group.size()).isEqualTo(1);
    verifyGroupContainsExactly(toPooledAttestationWithData(attestation2));
  }

  @TestTemplate
  void onAttestationIncludedInBlock_shouldNotAddAttestationsAlreadySeenInBlocks() {
    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6)));

    assertThat(group.add(createPooledAttestation(1), Optional.empty())).isFalse();
    assertThat(group.add(createPooledAttestation(1, 2, 3, 4, 5, 6), Optional.empty())).isFalse();
    assertThat(group.add(createPooledAttestation(2, 3), Optional.empty())).isFalse();
  }

  @TestTemplate
  void onReorg_shouldAllowReadingAttestationsThatAreNoLongerRedundant() {
    final PooledAttestation attestation = createPooledAttestation(3, 4);

    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), toAttestation(createPooledAttestation(1, 2, 3, 4, 5, 6)));

    assertThat(group.add(attestation, Optional.empty())).isFalse();

    group.onReorg(UInt64.ZERO);

    assertThat(group.add(attestation, Optional.empty())).isTrue();
    assertThat(group.size()).isEqualTo(1);
    verifyGroupContainsExactly(toPooledAttestationWithData(attestation));
  }

  @TestTemplate
  void onReorg_shouldNotAllowReadingAttestationsThatAreStillRedundant() {
    final PooledAttestation attestation1 = createPooledAttestation(3, 4);
    final PooledAttestation attestation2 = createPooledAttestation(1, 2, 3, 4);

    group.onAttestationIncludedInBlock(
        UInt64.valueOf(1), toAttestation(createPooledAttestation(2, 3, 4)));
    group.onAttestationIncludedInBlock(
        UInt64.valueOf(3), toAttestation(createPooledAttestation(1, 2, 3, 4)));

    assertThat(group.add(attestation1, Optional.empty())).isFalse();
    assertThat(group.add(attestation2, Optional.empty())).isFalse();

    group.onReorg(UInt64.valueOf(2)); // Block at slot 3 removed, block at slot 1 remains.

    // Attestation from slot 1 (2,3,4) still makes (3,4) redundant.
    assertThat(group.add(attestation1, Optional.empty())).isFalse();

    // Attestation (1,2,3,4) has validator 1, which is not in (2,3,4). So it can be added.
    assertThat(group.add(attestation2, Optional.empty())).isTrue();
    assertThat(group.size()).isEqualTo(1);
    verifyGroupContainsExactly(toPooledAttestationWithData(attestation2));
  }

  @TestTemplate
  public void size() {
    assertThat(group.size()).isEqualTo(0);
    final PooledAttestationWithData attestation1Data =
        toPooledAttestationWithData(addPooledAttestation(1));
    assertThat(group.size()).isEqualTo(1);
    final PooledAttestationWithData attestation2Data =
        toPooledAttestationWithData(addPooledAttestation(2));
    assertThat(group.size()).isEqualTo(2);
    addPooledAttestation(3, 4);
    assertThat(group.size()).isEqualTo(3);
    addPooledAttestation(1, 2);
    assertThat(group.size()).isEqualTo(4);

    int numRemoved =
        group.onAttestationIncludedInBlock(
            UInt64.ZERO,
            aggregateAttestations(
                attestation1Data.toAttestation(attestationSchema),
                attestation2Data.toAttestation(attestationSchema)));

    assertThat(numRemoved).isEqualTo(3);
    assertThat(group.size()).isEqualTo(1);
  }

  void verifyGroupContainsExactly(final PooledAttestationWithData... expectedAttestations) {
    // streamForApiRequest with no committee index is the only stream that gives us all attestations
    verifyStreamForApiRequest(
        Optional.empty(),
        spec.getGenesisSpec().getMilestone().isGreaterThanOrEqualTo(ELECTRA),
        expectedAttestations);
  }

  void verifyStreamForAggregationProductionContainsExactly(
      final PooledAttestationWithData... expectedAttestations) {
    assertThat(
            group
                .streamForAggregationProduction(Optional.of(UInt64.ZERO), Long.MAX_VALUE)
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  void verifyStreamForAggregationProductionPhase0ContainsExactly(
      final PooledAttestationWithData... expectedAttestations) {
    assertThat(
            group
                .streamForAggregationProduction(Optional.empty(), Long.MAX_VALUE)
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  void verifyStreamForBlockProductionContainsExactly(
      final PooledAttestationWithData... expectedAttestations) {
    assertThat(
            group
                .streamForBlockProduction(Long.MAX_VALUE)
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  void verifyStreamForApiRequest(
      final Optional<UInt64> committeeIndex,
      final boolean requiresCommitteeBits,
      final PooledAttestationWithData... expectedAttestations) {
    assertThat(
            group
                .streamForApiRequest(committeeIndex, requiresCommitteeBits)
                .map(this::toPooledAttestationWithDataWithSortedValidatorIndices))
        .containsExactly(expectedAttestations);
  }

  private PooledAttestationWithData toPooledAttestationWithDataWithSortedValidatorIndices(
          final PooledAttestationWithData attestation) {
    return new PooledAttestationWithData(
            attestation.data(),
            new PooledAttestation(
                    attestation.pooledAttestation().bits(),
                    attestation.pooledAttestation().validatorIndices().map(TreeSet::new).map(List::copyOf),
                    attestation.pooledAttestation().aggregatedSignature(),
                    attestation.pooledAttestation().isSingleAttestation()));
  }

  private PooledAttestation addPooledAttestation(final int... validators) {
    return addPooledAttestation(Optional.empty(), validators);
  }

  private PooledAttestation addPooledAttestation(
      final Optional<Integer> committeeIndex, final int... validators) {
    final PooledAttestation attestation = createPooledAttestation(committeeIndex, validators);
    final boolean added = group.add(attestation, Optional.empty());
    assertThat(added).isTrue();
    return attestation;
  }

  private PooledAttestation createPooledAttestation(final int... validators) {
    return createPooledAttestation(Optional.empty(), validators);
  }

  private PooledAttestation createPooledAttestation(
      final Optional<Integer> committeeIndex, final int... validators) {
    final SszBitlist aggregationBits =
        attestationSchema.getAggregationBitsSchema().ofBits(10, validators);
    final boolean isElectra = spec.atSlot(SLOT).getMilestone().isGreaterThanOrEqualTo(ELECTRA);
    final Supplier<SszBitvector> committeeBits;
    final Optional<Attestation> singleAttestation;
    final int resolvedCommitteeIndex = committeeIndex.orElse(0);

    if (validators.length == 1 && isElectra) {
      singleAttestation =
          Optional.of(
              spec.getGenesisSchemaDefinitions()
                  .toVersionElectra()
                  .orElseThrow()
                  .getSingleAttestationSchema()
                  .create(
                      UInt64.valueOf(resolvedCommitteeIndex),
                      UInt64.valueOf(validators[0]),
                      attestationData,
                      dataStructureUtil.randomSignature()));
    } else {
      singleAttestation = Optional.empty();
    }

    if (spec.atSlot(SLOT).getMilestone().isGreaterThanOrEqualTo(ELECTRA)) {
      committeeBits =
          () ->
              attestationSchema
                  .getCommitteeBitsSchema()
                  .orElseThrow()
                  .ofBits(resolvedCommitteeIndex);
    } else {
      committeeBits = () -> null;
    }

    final Attestation attestation =
        attestationSchema.create(
            aggregationBits, attestationData, dataStructureUtil.randomSignature(), committeeBits);

    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(spec, singleAttestation.orElse(attestation), committeeSizes);

    singleAttestation.ifPresent(
        __ -> validatableAttestation.convertToAggregatedFormatFromSingleAttestation(attestation));

    return PooledAttestation.fromValidatableAttestation(
        validatableAttestation, validatorBitToValidatorIndex(validators));
  }

  private List<UInt64> validatorBitToValidatorIndex(final int... validatorBits) {
    return Arrays.stream(validatorBits)
        .sorted() // make sure we sort validators so that we can compare the list without converting
        // it to a set
        .mapToObj(bit -> UInt64.valueOf(bit + 100))
        .toList();
  }

  private Attestation toAttestation(final PooledAttestation pooledAttestation) {
    return attestationSchema.create(
        pooledAttestation.bits().getAggregationBits(),
        attestationData,
        pooledAttestation.aggregatedSignature(),
        pooledAttestation.bits()::getCommitteeBits); // Supplier for committee bits
  }

  private PooledAttestationWithData toPooledAttestationWithData(
      final PooledAttestation pooledAttestation) {
    // Use the attestation created by toAttestation to ensure consistency if PooledAttestation
    // doesn't directly hold a full Attestation object in the exact schema form.
    return toPooledAttestationWithData(toAttestation(pooledAttestation));
  }

  private PooledAttestationWithData toPooledAttestationWithData(final Attestation attestation) {
    return new PooledAttestationWithData(
        attestationData,
        PooledAttestation.fromValidatableAttestation(
            ValidatableAttestation.from(spec, attestation, committeeSizes),
            validatorBitToValidatorIndex(
                attestation.getAggregationBits().getAllSetBits().toIntArray())));
  }

  private boolean isElectra(final SpecContext specContext) {
    return specContext.getSpecMilestone().isGreaterThanOrEqualTo(ELECTRA);
  }

  private boolean isElectra(final Spec spec) {
    return spec.getGenesisSpec().getMilestone().isGreaterThanOrEqualTo(ELECTRA);
  }
}
