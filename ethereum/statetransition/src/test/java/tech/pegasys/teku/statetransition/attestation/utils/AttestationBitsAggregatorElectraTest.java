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

package tech.pegasys.teku.statetransition.attestation.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class AttestationBitsAggregatorElectraTest {
  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final AttestationSchema<?> attestationSchema =
      spec.getGenesisSchemaDefinitions().getAttestationSchema();
  private final AttestationData attestationData = dataStructureUtil.randomAttestationData();

  private Int2IntMap committeeSizes;

  @BeforeEach
  void setUp() {
    committeeSizes = new Int2IntOpenHashMap();
    committeeSizes.put(0, 2);
    committeeSizes.put(1, 3);
    committeeSizes.put(2, 4);
  }

  /*
  full committee bits structure
  01|234|5678
   */

  @Test
  void aggregateFromEmpty() {
    /*
     012 <- committee 1 indices
     011 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(1), 1, 2);

    AttestationBitsAggregator aggregator =
        AttestationBitsAggregator.fromEmptyFromAttestationSchema(
            attestationSchema, () -> committeeSizes);

    assertThat(aggregator.aggregateWith(singleCommitteeAttestation.getAttestation())).isTrue();

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(1);

    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactly(1, 2);
  }

  @Test
  void cannotAggregateSameCommitteesWithOverlappingAggregates() {
    /*
     012 <- committee 1 indices
     011 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(1), 1, 2);

    /*
     012 <- committee 1 indices
     110 <- bits
    */
    ValidatableAttestation otherSingleCommitteeAttestation = createAttestation(List.of(1), 0, 1);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(singleCommitteeAttestation);

    assertThat(aggregator.aggregateWith(otherSingleCommitteeAttestation.getAttestation()))
        .isFalse();
  }

  @Test
  void aggregateOnSameCommittee() {
    /*
     012 <- committee 1 indices
     011 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(1), 1, 2);

    /*
     012 <- committee 1 indices
     100 <- bits
    */
    ValidatableAttestation otherSingleCommitteeAttestation = createAttestation(List.of(1), 0);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(singleCommitteeAttestation);

    assertThat(aggregator.aggregateWith(otherSingleCommitteeAttestation.getAttestation())).isTrue();

    /*
     012 <- committee 1 indices
     111 <- bits
    */

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(1);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactly(0, 1, 2);
  }

  @Test
  void aggregateOnMultipleOverlappingCommitteeBits() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(0, 1), 0, 2);

    /*
     01|234 <- committee 0 and 1 indices
     01|010 <- bits
    */
    ValidatableAttestation otherSingleCommitteeAttestation = createAttestation(List.of(0, 1), 1, 3);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(singleCommitteeAttestation);

    assertThat(aggregator.aggregateWith(otherSingleCommitteeAttestation.getAttestation())).isTrue();

    /*
     01|234 <- committee 0 and 1 indices
     11|110 <- bits
    */

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(0, 1);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactly(0, 1, 2, 3);
  }

  @Test
  void aggregateOnMultipleOverlappingCommitteeBitsVariation() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(0, 1), 0, 2);

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     01|011|0001 <- bits
    */
    ValidatableAttestation otherSingleCommitteeAttestation =
        createAttestation(List.of(0, 1, 2), 1, 3, 4, 8);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(singleCommitteeAttestation);

    assertThat(aggregator.aggregateWith(otherSingleCommitteeAttestation.getAttestation())).isTrue();

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     11|111|0001 <- bits
    */

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(0, 1, 2);
    assertThat(aggregator.getAggregationBits().streamAllSetBits())
        .containsExactly(0, 1, 2, 3, 4, 8);
  }

  @Test
  void cannotAggregateOnMultipleOverlappingCommitteeBitsButWithSomeOfAggregationOverlapping() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(0, 1), 0, 2);

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     01|110|0001 <- bits
    */
    ValidatableAttestation otherSingleCommitteeAttestation =
        createAttestation(List.of(0, 1, 2), 1, 2, 3, 8);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(singleCommitteeAttestation);

    assertThat(aggregator.aggregateWith(otherSingleCommitteeAttestation.getAttestation()))
        .isFalse();

    // check remained untouched

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(0, 1);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactly(0, 2);
  }

  @Test
  void aggregateOnMultipleOverlappingCommitteeBitsButWithSomeOfAggregationOverlappingWhenNoCheck() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(0, 1), 0, 2);

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     01|110|0001 <- bits
    */
    ValidatableAttestation otherSingleCommitteeAttestation =
            createAttestation(List.of(0, 1, 2), 1, 2, 3, 8);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(singleCommitteeAttestation);

    aggregator.aggregateNoCheck(otherSingleCommitteeAttestation.getAttestation());

        /*
     01|234|5678 <- committee 0, 1 and 2 indices
     11|110|0001 <- bits
    */

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(0, 1, 2);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactly(0,1, 2,3,8);
  }

  @Test
  void aggregateOnMultipleDisjointedCommitteeBits() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(0, 1), 0, 2);

    /*
     0123 <- committee 1 indices
     1101 <- bits
    */
    ValidatableAttestation otherSingleCommitteeAttestation = createAttestation(List.of(2), 0, 1, 3);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(singleCommitteeAttestation);

    assertThat(aggregator.aggregateWith(otherSingleCommitteeAttestation.getAttestation())).isTrue();

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     10|100|1101 <- bits
    */

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(0, 1, 2);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactly(0, 2, 5, 6, 8);
  }

  @Test
  void aggregateOnMultipleDisjointedCommitteeBitsVariation() {
    /*
     0123 <- committee 2 indices
     0001 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(2), 3);

    /*
     01 <- committee 0 indices
     01 <- bits
    */
    ValidatableAttestation otherSingleCommitteeAttestation = createAttestation(List.of(0), 1);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(singleCommitteeAttestation);

    assertThat(aggregator.aggregateWith(otherSingleCommitteeAttestation.getAttestation())).isTrue();

    /*
     01|2345 <- committee 0 and 2 indices
     01|0001 <- bits
    */

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(0, 2);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactly(1, 5);
  }

  @Test
  void supersedes1() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(0, 1), 0, 2, 4);

    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    ValidatableAttestation otherSingleCommitteeAttestation = createAttestation(List.of(0, 1), 0, 2);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(singleCommitteeAttestation);

    assertThat(aggregator.supersedes(otherSingleCommitteeAttestation.getAttestation())).isTrue();
  }

  @Test
  void supersedes2() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(0, 1), 0, 2, 4);

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    ValidatableAttestation otherSingleCommitteeAttestation =
        createAttestation(List.of(0, 1), 0, 2, 4);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(singleCommitteeAttestation);

    assertThat(aggregator.supersedes(otherSingleCommitteeAttestation.getAttestation())).isTrue();
  }

  @Test
  void supersedes3() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(0, 1), 0, 2, 4);

    /*
     01|234 <- committee 0 and 1 indices
     10|111 <- bits
    */
    ValidatableAttestation otherSingleCommitteeAttestation =
        createAttestation(List.of(0, 1), 0, 2, 3, 4);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(singleCommitteeAttestation);

    assertThat(aggregator.supersedes(otherSingleCommitteeAttestation.getAttestation())).isFalse();
  }

  @Test
  void supersedes4() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(0, 1), 0, 2, 4);

    /*
     012 <- committee 1 indices
     111 <- bits
    */
    ValidatableAttestation otherSingleCommitteeAttestation = createAttestation(List.of(1), 0, 1, 2);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(singleCommitteeAttestation);

    assertThat(aggregator.supersedes(otherSingleCommitteeAttestation.getAttestation())).isFalse();
  }

  @Test
  void supersedes5() {

    /*
     01 <- committee 0
     10 <- bits
    */
    ValidatableAttestation singleCommitteeAttestation = createAttestation(List.of(0), 0);

    /*
     012 <- committee 1 indices
     100 <- bits
    */
    ValidatableAttestation otherSingleCommitteeAttestation = createAttestation(List.of(1), 0);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(singleCommitteeAttestation);

    assertThat(aggregator.supersedes(otherSingleCommitteeAttestation.getAttestation())).isFalse();
  }

  private ValidatableAttestation createAttestation(
      final List<Integer> committeeIndices, final int... validators) {
    final SszBitlist aggregationBits =
        attestationSchema
            .getAggregationBitsSchema()
            .ofBits(
                Math.toIntExact(attestationSchema.getAggregationBitsSchema().getMaxLength()),
                validators);
    final Supplier<SszBitvector> committeeBits =
        () -> attestationSchema.getCommitteeBitsSchema().orElseThrow().ofBits(committeeIndices);

    final ValidatableAttestation attestation = mock(ValidatableAttestation.class);
    when(attestation.getAttestation())
        .thenReturn(
            attestationSchema.create(
                aggregationBits,
                attestationData,
                committeeBits,
                dataStructureUtil.randomSignature()));
    when(attestation.getCommitteesSize()).thenReturn(Optional.of(committeeSizes));

    return attestation;
  }
}
