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

package tech.pegasys.teku.statetransition.attestation.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
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
  private final Spec spec = TestSpecFactory.createMainnetElectra();
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
    final ValidatableAttestation initialAttestation = createAttestation(List.of(1), 1, 2);

    final AttestationBitsAggregator aggregator =
        AttestationBitsAggregator.fromEmptyFromAttestationSchema(
            attestationSchema, Optional.of(committeeSizes));

    assertThat(aggregator.aggregateWith(initialAttestation.getAttestation())).isTrue();

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(1);

    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactly(1, 2);
  }

  @Test
  void cannotAggregateSameCommitteesWithOverlappingAggregates() {
    /*
     012 <- committee 1 indices
     011 <- bits
    */
    final ValidatableAttestation initialAttestation = createAttestation(List.of(1), 1, 2);

    /*
     012 <- committee 1 indices
     110 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(1), 0, 1);

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    assertThat(aggregator.aggregateWith(otherAttestation.getAttestation())).isFalse();
  }

  @Test
  void aggregateOnSameCommittee() {
    /*
     012 <- committee 1 indices
     011 <- bits
    */
    final ValidatableAttestation initialAttestation = createAttestation(List.of(1), 1, 2);

    /*
     012 <- committee 1 indices
     100 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(1), 0);

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    assertThat(aggregator.aggregateWith(otherAttestation.getAttestation())).isTrue();

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
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0, 1), 0, 2);

    /*
     01|234 <- committee 0 and 1 indices
     01|010 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(0, 1), 1, 3);

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    assertThat(aggregator.aggregateWith(otherAttestation.getAttestation())).isTrue();

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
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0, 1), 0, 2);

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     01|011|0001 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(0, 1, 2), 1, 3, 4, 8);

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    assertThat(aggregator.aggregateWith(otherAttestation.getAttestation())).isTrue();

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
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0, 1), 0, 2);

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     01|110|0001 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(0, 1, 2), 1, 2, 3, 8);

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    assertThat(aggregator.aggregateWith(otherAttestation.getAttestation())).isFalse();

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
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0, 1), 0, 2);

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     01|110|0001 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(0, 1, 2), 1, 2, 3, 8);

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    // cannot aggregate
    assertThat(aggregator.aggregateWith(otherAttestation.getAttestation())).isFalse();

    // calculate the or
    aggregator.or(otherAttestation.getAttestation());

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     11|110|0001 <- bits
    */

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(0, 1, 2);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactly(0, 1, 2, 3, 8);
  }

  @Test
  void aggregateSingleAttestationFillUp() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0, 1), 0, 2);

    /*
     123 <- committee 1 indices
     001 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(1), 2);

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    // calculate the or
    aggregator.or(otherAttestation.getAttestation());

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(0, 1);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactly(0, 2, 4);
  }

  @Test
  void
      aggregateOnMultipleOverlappingCommitteeBitsButWithSomeOfAggregationOverlappingWhenNoCheck2() {
    /*
     0123 <- committee 2 indices
     0100 <- bits
    */
    final ValidatableAttestation initialAttestation = createAttestation(List.of(2), 1);

    /*
     0123 <- committee 2 indices
     1101 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(2), 0, 1, 3);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    // cannot aggregate
    assertThat(aggregator.aggregateWith(otherAttestation.getAttestation())).isFalse();

    // calculate the or
    aggregator.or(otherAttestation.getAttestation());

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     11|110|0001 <- bits
    */

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(2);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactly(0, 1, 3);
  }

  @Test
  void aggregateOnMultipleDisjointedCommitteeBits() {
    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0, 1), 0, 2);

    /*
     0123 <- committee 1 indices
     1101 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(2), 0, 1, 3);

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    assertThat(aggregator.aggregateWith(otherAttestation.getAttestation())).isTrue();

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
    final ValidatableAttestation initialAttestation = createAttestation(List.of(2), 3);

    /*
     01 <- committee 0 indices
     01 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(0), 1);

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    assertThat(aggregator.aggregateWith(otherAttestation.getAttestation())).isTrue();

    /*
     01|2345 <- committee 0 and 2 indices
     01|0001 <- bits
    */

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(0, 2);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactly(1, 5);
  }

  @Test
  void bigAggregation() {
    committeeSizes = new Int2IntOpenHashMap();
    committeeSizes.put(0, 50);
    committeeSizes.put(1, 49);
    committeeSizes.put(2, 50);
    committeeSizes.put(3, 50);
    committeeSizes.put(4, 50);
    committeeSizes.put(5, 50);
    committeeSizes.put(6, 50);
    committeeSizes.put(7, 50);
    committeeSizes.put(8, 50);
    committeeSizes.put(9, 50);
    committeeSizes.put(10, 50);
    committeeSizes.put(11, 50);
    committeeSizes.put(12, 50);
    committeeSizes.put(13, 50);
    committeeSizes.put(14, 50);
    committeeSizes.put(15, 51);

    final ValidatableAttestation initialAttestation =
        createAttestation(
            "1111111111111111",
            """
    11111111111111111111111111111111111111111111111111\
    0000000000000000000000000000000000000000000000010\
    00000000000000000000100000000000000000000000000000\
    00000000000010000000000000000000000000000000000000\
    00000000000000000000000000000000000000000000000001\
    00000000000000000000000000000000000010000000000000\
    00000000000000000010000000000000000000000000000000\
    00000000000000000001000000000000000000000000000000\
    00000000000000010001000000000000000000000000000000\
    00000000000000000000000001000000000000000000000000\
    00000010000000000000000000000000000000000000000000\
    00010000000000000000000000000000000000000000000000\
    00000000000000100000000000000000000000000000000000\
    00000000000010000000000000000000000000000000000000\
    00100000000000000000000000000000000000000000000000\
    000000000000001000000000000000000000000000000000001\
    """);
    final ValidatableAttestation att =
        createAttestation("0001000000000000", "00000000000000000000000100000000000000000000000000");

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);
    assertThat(aggregator.aggregateWith(att.getAttestation())).isTrue();

    final ValidatableAttestation result =
        createAttestation(
            "1111111111111111",
            """
        11111111111111111111111111111111111111111111111111\
        0000000000000000000000000000000000000000000000010\
        00000000000000000000100000000000000000000000000000\
        00000000000010000000000100000000000000000000000000\
        00000000000000000000000000000000000000000000000001\
        00000000000000000000000000000000000010000000000000\
        00000000000000000010000000000000000000000000000000\
        00000000000000000001000000000000000000000000000000\
        00000000000000010001000000000000000000000000000000\
        00000000000000000000000001000000000000000000000000\
        00000010000000000000000000000000000000000000000000\
        00010000000000000000000000000000000000000000000000\
        00000000000000100000000000000000000000000000000000\
        00000000000010000000000000000000000000000000000000\
        00100000000000000000000000000000000000000000000000\
        000000000000001000000000000000000000000000000000001\
        """);

    assertThat(aggregator.getCommitteeBits())
        .isEqualTo(result.getAttestation().getCommitteeBitsRequired());
    assertThat(aggregator.getAggregationBits())
        .isEqualTo(result.getAttestation().getAggregationBits());
  }

  @Test
  void orIntoEmptyAggregator() {
    final AttestationBitsAggregator aggregator =
        AttestationBitsAggregator.fromEmptyFromAttestationSchema(
            attestationSchema, Optional.of(committeeSizes));

    final ValidatableAttestation otherAttestation = createAttestation(List.of(1), 2);

    aggregator.or(otherAttestation.getAttestation());

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(1);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactly(2);
  }

  @Test
  void orWithNewCommittee() {
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0), 1);
    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    final ValidatableAttestation otherAttestation = createAttestation(List.of(1), 2);

    aggregator.or(otherAttestation.getAttestation());

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactlyInAnyOrder(0, 1);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactlyInAnyOrder(1, 4);
  }

  @Test
  void orWithExistingCommitteeAddNewBits() {

    final ValidatableAttestation initialAttestation = createAttestation(List.of(1), 0);
    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    final ValidatableAttestation otherAttestation = createAttestation(List.of(1), 2);

    aggregator.or(otherAttestation.getAttestation());

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(1);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactlyInAnyOrder(0, 2);
  }

  @Test
  void orWithExistingCommitteeOverlapAndNewBits() {
    final ValidatableAttestation initialAttestation = createAttestation(List.of(1), 0, 1);
    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    final ValidatableAttestation otherAttestation = createAttestation(List.of(1), 1, 2);

    aggregator.or(otherAttestation.getAttestation());

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(1);
    assertThat(aggregator.getAggregationBits().streamAllSetBits())
        .containsExactlyInAnyOrder(0, 1, 2);
  }

  @Test
  void orWithStrictSubsetAttestation() {
    final ValidatableAttestation initialAttestation = createAttestation(List.of(1), 0, 2);
    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    final ValidatableAttestation otherAttestation = createAttestation(List.of(1), 0);

    aggregator.or(otherAttestation.getAttestation());

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactly(1);
    assertThat(aggregator.getAggregationBits().streamAllSetBits()).containsExactlyInAnyOrder(0, 2);
  }

  @Test
  void orWithMultipleCommitteesMixedNewAndExisting() {
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0, 1), 0, 3);
    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    final ValidatableAttestation otherAttestation = createAttestation(List.of(1, 2), 2, 5);

    aggregator.or(otherAttestation.getAttestation());

    assertThat(aggregator.getCommitteeBits().streamAllSetBits()).containsExactlyInAnyOrder(0, 1, 2);
    assertThat(aggregator.getAggregationBits().streamAllSetBits())
        .containsExactlyInAnyOrder(0, 3, 4, 7);
  }

  @Test
  void orAggregatorWithAggregator() {
    final ValidatableAttestation att1Data = createAttestation(List.of(0), 0);
    final AttestationBitsAggregator aggregator1 = AttestationBitsAggregator.of(att1Data);

    final ValidatableAttestation att2Data = createAttestation(List.of(0, 1), 1, 2);
    final AttestationBitsAggregator aggregator2 = AttestationBitsAggregator.of(att2Data);

    aggregator1.or(aggregator2);

    assertThat(aggregator1.getCommitteeBits().streamAllSetBits()).containsExactlyInAnyOrder(0, 1);
    assertThat(aggregator1.getAggregationBits().streamAllSetBits())
        .containsExactlyInAnyOrder(0, 1, 2);

    // aggregator2 should remain unchanged
    assertThat(aggregator2.getCommitteeBits().streamAllSetBits()).containsExactlyInAnyOrder(0, 1);
    assertThat(aggregator2.getAggregationBits().streamAllSetBits()).containsExactlyInAnyOrder(1, 2);
  }

  @Test
  void isSuperSetOf1() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0, 1), 0, 2, 4);

    /*
     01|234 <- committee 0 and 1 indices
     10|100 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(0, 1), 0, 2);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    assertThat(aggregator.isSuperSetOf(otherAttestation.getAttestation())).isTrue();
  }

  @Test
  void isSuperSetOf2() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0, 1), 0, 2, 4);

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(0, 1), 0, 2, 4);

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    assertThat(aggregator.isSuperSetOf(otherAttestation.getAttestation())).isTrue();
  }

  @Test
  void isSuperSetOf3() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0, 1), 0, 2, 4);

    /*
     01|234 <- committee 0 and 1 indices
     10|111 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(0, 1), 0, 2, 3, 4);

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    assertThat(aggregator.isSuperSetOf(otherAttestation.getAttestation())).isFalse();
  }

  @Test
  void isSuperSetOf4() {

    /*
     01|234 <- committee 0 and 1 indices
     10|101 <- bits
    */
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0, 1), 0, 2, 4);

    /*
     012 <- committee 1 indices
     111 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(1), 0, 1, 2);

    AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    assertThat(aggregator.isSuperSetOf(otherAttestation.getAttestation())).isFalse();
  }

  @Test
  void isSuperSetOf5() {

    /*
     01 <- committee 0
     10 <- bits
    */
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0), 0);

    /*
     012 <- committee 1 indices
     100 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(1), 0);

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    assertThat(aggregator.isSuperSetOf(otherAttestation.getAttestation())).isFalse();
  }

  @Test
  void isSuperSetOf6() {

    /*
     01|234|5678 <- committee 0, 1 and 2 indices
     11|111|1111 <- bits
    */
    final ValidatableAttestation initialAttestation =
        createAttestation(List.of(0, 1, 2), 0, 1, 2, 3, 4, 5, 6, 7, 8);

    /*
     012 <- committee 1 indices
     100 <- bits
    */
    final ValidatableAttestation otherAttestation = createAttestation(List.of(1), 0);

    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    assertThat(aggregator.isSuperSetOf(otherAttestation.getAttestation())).isTrue();
  }

  @Test
  void copy_shouldNotModifyOriginal() {
    final ValidatableAttestation initialAttestation = createAttestation(List.of(0), 0);
    final AttestationBitsAggregator aggregator = AttestationBitsAggregator.of(initialAttestation);

    // check aggregator is initialized correctly
    assertThat(aggregator.getCommitteeBits())
        .isEqualTo(initialAttestation.getAttestation().getCommitteeBitsRequired());
    assertThat(aggregator.getAggregationBits())
        .isEqualTo(initialAttestation.getAttestation().getAggregationBits());

    final AttestationBitsAggregator copy = aggregator.copy();

    assertThat(copy.getCommitteeBits()).isEqualTo(aggregator.getCommitteeBits());
    assertThat(copy.getAggregationBits()).isEqualTo(aggregator.getAggregationBits());
    assertThat(copy).isNotSameAs(aggregator);

    assertThat(copy.aggregateWith(createAttestation(List.of(1), 1).getAttestation())).isTrue();

    // the original should not be modified
    assertThat(aggregator.getCommitteeBits())
        .isEqualTo(initialAttestation.getAttestation().getCommitteeBitsRequired());
    assertThat(aggregator.getAggregationBits())
        .isEqualTo(initialAttestation.getAttestation().getAggregationBits());
  }

  private ValidatableAttestation createAttestation(final String commBits, final String aggBits) {
    assertThat(commBits).matches(Pattern.compile("^[0-1]+$"));
    assertThat(aggBits).matches(Pattern.compile("^[0-1]+$"));
    final List<Integer> commBitList =
        IntStream.range(0, commBits.length())
            .mapToObj(index -> commBits.charAt(index) == '1' ? index : -1)
            .filter(index -> index >= 0)
            .toList();
    final int[] aggBitList =
        IntStream.range(0, aggBits.length())
            .map(index -> aggBits.charAt(index) == '1' ? index : -1)
            .filter(index -> index >= 0)
            .toArray();
    return createAttestation(commBitList, aggBitList);
  }

  private ValidatableAttestation createAttestation(
      final List<Integer> committeeIndices, final int... validators) {
    final SszBitlist aggregationBits =
        attestationSchema
            .getAggregationBitsSchema()
            .ofBits(
                committeeSizes.int2IntEntrySet().stream()
                    .filter(entry -> committeeIndices.contains(entry.getIntKey()))
                    .mapToInt(Entry::getIntValue)
                    .sum(),
                validators);
    final Supplier<SszBitvector> committeeBits =
        () -> attestationSchema.getCommitteeBitsSchema().orElseThrow().ofBits(committeeIndices);

    final ValidatableAttestation attestation = mock(ValidatableAttestation.class);
    when(attestation.getAttestation())
        .thenReturn(
            attestationSchema.create(
                aggregationBits,
                attestationData,
                dataStructureUtil.randomSignature(),
                committeeBits));
    when(attestation.getCommitteesSize()).thenReturn(Optional.of(committeeSizes));

    return attestation;
  }
}
